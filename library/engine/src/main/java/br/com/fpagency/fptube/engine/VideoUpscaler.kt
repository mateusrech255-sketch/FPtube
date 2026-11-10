package br.com.fpagency.fptube.engine

import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import br.com.fpagency.fptube.core.AppLogger
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Redimensiona/upscala vídeos via hardware (MediaCodec + OpenGL ES + MediaMuxer)
 * quando a resolução da stream original da plataforma é inferior à resolução solicitada (ex: 720p -> 1080p).
 */
object VideoUpscaler {

    private const val EGL_RECORDABLE_ANDROID = 0x3142

    fun upscaleIfNeeded(
        inputFile: File,
        outputFile: File,
        targetShortSide: Int,
        shouldCancel: () -> Boolean = { false },
        onProgress: (Float) -> Unit = {}
    ): Boolean {
        if (!inputFile.exists() || inputFile.length() == 0L || targetShortSide <= 0) return false

        val retriever = MediaMetadataRetriever()
        val rawWidth: Int
        val rawHeight: Int
        val rotation: Int
        val durationMs: Long
        try {
            retriever.setDataSource(inputFile.absolutePath)
            rawWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            rawHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            return false
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }

        if (rawWidth <= 0 || rawHeight <= 0) return false

        val displayWidth = if (rotation == 90 || rotation == 270) rawHeight else rawWidth
        val displayHeight = if (rotation == 90 || rotation == 270) rawWidth else rawHeight
        val currentShortSide = minOf(displayWidth, displayHeight)

        // Se já está na resolução solicitada ou superior, não precisa reprocessar
        if (currentShortSide >= targetShortSide) {
            return false
        }

        val dstWidth: Int
        val dstHeight: Int
        if (displayWidth <= displayHeight) {
            dstWidth = (targetShortSide / 2) * 2
            dstHeight = (((targetShortSide.toDouble() * displayHeight / displayWidth).toInt() + 1) / 2) * 2
        } else {
            dstHeight = (targetShortSide / 2) * 2
            dstWidth = (((targetShortSide.toDouble() * displayWidth / displayHeight).toInt() + 1) / 2) * 2
        }

        AppLogger.i(
            "[VideoUpscaler] Upscaling via hardware de ${displayWidth}x${displayHeight} para ${dstWidth}x${dstHeight}",
            "VideoUpscaler"
        )

        var videoExtractor: MediaExtractor? = null
        var audioExtractor: MediaExtractor? = null
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var frameThread: HandlerThread? = null
        var decoderSurface: Surface? = null
        var surfaceTexture: SurfaceTexture? = null
        var encoderInputSurface: Surface? = null
        var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
        var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

        try {
            videoExtractor = MediaExtractor().apply { setDataSource(inputFile.absolutePath) }
            var videoTrackIndex = -1
            var inputVideoFormat: MediaFormat? = null
            var inputMime = ""
            for (i in 0 until videoExtractor.trackCount) {
                val fmt = videoExtractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    videoTrackIndex = i
                    inputVideoFormat = fmt
                    inputMime = mime
                    break
                }
            }
            if (videoTrackIndex < 0 || inputVideoFormat == null) return false
            videoExtractor.selectTrack(videoTrackIndex)

            audioExtractor = MediaExtractor().apply { setDataSource(inputFile.absolutePath) }
            var audioTrackIndex = -1
            var inputAudioFormat: MediaFormat? = null
            for (i in 0 until audioExtractor.trackCount) {
                val fmt = audioExtractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    inputAudioFormat = fmt
                    break
                }
            }
            if (audioTrackIndex >= 0) {
                audioExtractor.selectTrack(audioTrackIndex)
            }

            val fps = try {
                inputVideoFormat.getInteger(MediaFormat.KEY_FRAME_RATE)
            } catch (_: Exception) {
                30
            }.coerceIn(24, 60)

            val bitrate = when {
                targetShortSide >= 1080 -> 6_000_000
                targetShortSide >= 720 -> 3_500_000
                else -> 2_000_000
            }

            val outputMime = MediaFormat.MIMETYPE_VIDEO_AVC
            val encoderFormat = MediaFormat.createVideoFormat(outputMime, dstWidth, dstHeight).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            encoder = MediaCodec.createEncoderByType(outputMime)
            encoder.configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoderInputSurface = encoder.createInputSurface()
            encoder.start()

            // Configurar EGL14 ligado à Surface de entrada do Encoder
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val version = IntArray(2)
            EGL14.eglInitialize(eglDisplay, version, 0, version, 1)

            val attribList = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL_RECORDABLE_ANDROID, 1,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, configs.size, numConfigs, 0)
            val eglConfig = configs[0] ?: return false

            val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)

            val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
            eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, encoderInputSurface, surfaceAttribs, 0)
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)

            val renderer = OesTextureRenderer().apply { initGl() }
            frameThread = HandlerThread("VideoUpscalerFrameThread").apply { start() }
            val frameSync = Object()
            var frameAvailable = false

            surfaceTexture = SurfaceTexture(renderer.textureId).apply {
                setOnFrameAvailableListener({
                    synchronized(frameSync) {
                        frameAvailable = true
                        frameSync.notifyAll()
                    }
                }, Handler(frameThread.looper))
            }
            decoderSurface = Surface(surfaceTexture)

            decoder = MediaCodec.createDecoderByType(inputMime)
            decoder.configure(inputVideoFormat, decoderSurface, null, 0)
            decoder.start()

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var muxerVideoTrack = -1
            var muxerAudioTrack = -1

            val decoderBufferInfo = MediaCodec.BufferInfo()
            val encoderBufferInfo = MediaCodec.BufferInfo()
            val stMatrix = FloatArray(16)

            var inputDone = false
            var decoderDone = false
            var encoderDone = false
            val durationUs = (durationMs * 1000L).coerceAtLeast(1L)

            val audioBuf = ByteBuffer.allocateDirect(512 * 1024)
            val audioInfo = MediaCodec.BufferInfo()
            var lastAudioPts = -1L
            var skippedNegativeAudio = 0
            var audioDone = (audioTrackIndex < 0)
            var audioSamplesWritten = 0

            fun drainAudioUpTo(targetPtsUs: Long) {
                if (!muxerStarted || muxerAudioTrack < 0 || audioDone) return
                while (!audioDone) {
                    audioBuf.clear()
                    val sampleSize = audioExtractor.readSampleData(audioBuf, 0)
                    if (sampleSize < 0) {
                        audioDone = true
                        break
                    }
                    val sampleTime = audioExtractor.sampleTime
                    if (sampleTime < 0 && skippedNegativeAudio < 10) {
                        skippedNegativeAudio++
                        audioExtractor.advance()
                        continue
                    }
                    if (sampleTime > targetPtsUs) {
                        // Não consome ainda, aguarda o vídeo alcançar esta estampa de tempo
                        break
                    }
                    val safePts = if (sampleTime <= lastAudioPts) lastAudioPts + 1000L else sampleTime
                    lastAudioPts = safePts
                    audioInfo.offset = 0
                    audioInfo.size = sampleSize
                    audioInfo.presentationTimeUs = safePts
                    audioInfo.flags = if ((audioExtractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                        MediaCodec.BUFFER_FLAG_KEY_FRAME
                    } else {
                        0
                    }
                    audioBuf.position(0)
                    audioBuf.limit(sampleSize)
                    muxer.writeSampleData(muxerAudioTrack, audioBuf, audioInfo)
                    audioSamplesWritten++
                    audioExtractor.advance()
                }
            }

            while (!encoderDone) {
                if (shouldCancel()) {
                    AppLogger.i("[VideoUpscaler] Upscaling cancelado pelo usuário", "VideoUpscaler")
                    return false
                }
                if (!inputDone) {
                    val inIndex = decoder.dequeueInputBuffer(5000L)
                    if (inIndex >= 0) {
                        val inBuf = decoder.getInputBuffer(inIndex)
                        if (inBuf != null) {
                            val sampleSize = videoExtractor.readSampleData(inBuf, 0)
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(
                                    inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputDone = true
                            } else {
                                val pts = videoExtractor.sampleTime
                                decoder.queueInputBuffer(inIndex, 0, sampleSize, pts, 0)
                                videoExtractor.advance()
                            }
                        }
                    }
                }

                var decoderOutputAvailable = !decoderDone
                var encoderOutputAvailable = true

                while (decoderOutputAvailable || encoderOutputAvailable) {
                    val encOutIndex = encoder.dequeueOutputBuffer(encoderBufferInfo, 2000L)
                    when {
                        encOutIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            encoderOutputAvailable = false
                        }
                        encOutIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (!muxerStarted) {
                                muxerVideoTrack = muxer.addTrack(encoder.outputFormat)
                                if (inputAudioFormat != null) {
                                    try {
                                        muxerAudioTrack = muxer.addTrack(inputAudioFormat)
                                    } catch (e: Exception) {
                                        AppLogger.w("[VideoUpscaler] addTrack falhou com formato bruto, usando formato limpo: ${e.message}", "VideoUpscaler")
                                        val cleanFormat = MediaFormat.createAudioFormat(
                                            inputAudioFormat.getString(MediaFormat.KEY_MIME) ?: MediaFormat.MIMETYPE_AUDIO_AAC,
                                            inputAudioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                                            inputAudioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                                        )
                                        if (inputAudioFormat.containsKey("csd-0")) {
                                            cleanFormat.setByteBuffer("csd-0", inputAudioFormat.getByteBuffer("csd-0"))
                                        }
                                        muxerAudioTrack = muxer.addTrack(cleanFormat)
                                    }
                                }
                                muxer.start()
                                muxerStarted = true
                                AppLogger.i(
                                    "[VideoUpscaler] MediaMuxer iniciado (videoTrack=$muxerVideoTrack, audioTrack=$muxerAudioTrack)",
                                    "VideoUpscaler"
                                )
                            }
                        }
                        encOutIndex >= 0 -> {
                            val encodedData = encoder.getOutputBuffer(encOutIndex)
                            if (encodedData != null &&
                                (encoderBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 &&
                                encoderBufferInfo.size > 0 &&
                                muxerStarted
                            ) {
                                encodedData.position(encoderBufferInfo.offset)
                                encodedData.limit(encoderBufferInfo.offset + encoderBufferInfo.size)
                                muxer.writeSampleData(muxerVideoTrack, encodedData, encoderBufferInfo)
                                drainAudioUpTo(encoderBufferInfo.presentationTimeUs)
                                val prog = ((encoderBufferInfo.presentationTimeUs.toFloat() / durationUs) * 100f)
                                    .coerceIn(0f, 100f)
                                onProgress(prog)
                            }
                            encoder.releaseOutputBuffer(encOutIndex, false)
                            if ((encoderBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                encoderDone = true
                                break
                            }
                        }
                    }

                    if (encoderDone) break
                    if (encOutIndex != MediaCodec.INFO_TRY_AGAIN_LATER) continue

                    if (!decoderDone) {
                        val decOutIndex = decoder.dequeueOutputBuffer(decoderBufferInfo, 2000L)
                        when {
                            decOutIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                                decoderOutputAvailable = false
                            }
                            decOutIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                // Ignorar
                            }
                            decOutIndex >= 0 -> {
                                val doRender = decoderBufferInfo.size > 0
                                decoder.releaseOutputBuffer(decOutIndex, doRender)
                                if (doRender) {
                                    synchronized(frameSync) {
                                        var waited = 0
                                        while (!frameAvailable && waited < 10) {
                                            frameSync.wait(50L)
                                            waited++
                                        }
                                        frameAvailable = false
                                    }
                                    surfaceTexture.updateTexImage()
                                    surfaceTexture.getTransformMatrix(stMatrix)
                                    GLES20.glViewport(0, 0, dstWidth, dstHeight)
                                    renderer.drawFrame(stMatrix)
                                    EGLExt.eglPresentationTimeANDROID(
                                        eglDisplay,
                                        eglSurface,
                                        decoderBufferInfo.presentationTimeUs * 1000L
                                    )
                                    EGL14.eglSwapBuffers(eglDisplay, eglSurface)
                                }
                                if ((decoderBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                    decoderDone = true
                                    decoderOutputAvailable = false
                                    encoder.signalEndOfInputStream()
                                }
                            }
                        }
                    }
                }
            }

            // Drenar todo o áudio restante de forma síncrona
            drainAudioUpTo(Long.MAX_VALUE)
            AppLogger.i("[VideoUpscaler] Áudio muxado com sucesso: $audioSamplesWritten amostras", "VideoUpscaler")

            if (muxerStarted) {
                muxer.stop()
                muxerStarted = false
            }

            return outputFile.exists() && outputFile.length() > 0L
        } catch (e: Exception) {
            AppLogger.e("[VideoUpscaler] Falha ao upscalar vídeo: ${e.message}", "VideoUpscaler", e)
            return false
        } finally {
            try { decoder?.stop() } catch (_: Exception) {}
            try { decoder?.release() } catch (_: Exception) {}
            try { encoder?.stop() } catch (_: Exception) {}
            try { encoder?.release() } catch (_: Exception) {}
            try {
                if (muxerStarted) muxer?.stop()
            } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
            try { videoExtractor?.release() } catch (_: Exception) {}
            try { audioExtractor?.release() } catch (_: Exception) {}
            try { decoderSurface?.release() } catch (_: Exception) {}
            try { surfaceTexture?.release() } catch (_: Exception) {}
            try { encoderInputSurface?.release() } catch (_: Exception) {}
            try {
                if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                    EGL14.eglMakeCurrent(
                        eglDisplay,
                        EGL14.EGL_NO_SURFACE,
                        EGL14.EGL_NO_SURFACE,
                        EGL14.EGL_NO_CONTEXT
                    )
                    if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
                    if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
                    EGL14.eglTerminate(eglDisplay)
                }
            } catch (_: Exception) {}
            try { frameThread?.quitSafely() } catch (_: Exception) {}
        }
    }

    private class OesTextureRenderer {
        var textureId: Int = 0
            private set

        private var program = 0
        private var uMVPMatrixHandle = 0
        private var uSTMatrixHandle = 0
        private var aPositionHandle = 0
        private var aTextureCoordHandle = 0
        private val mvpMatrix = FloatArray(16)

        private val triangleVerticesData = floatArrayOf(
            // X, Y, Z, U, V
            -1.0f, -1.0f, 0f, 0f, 0f,
             1.0f, -1.0f, 0f, 1f, 0f,
            -1.0f,  1.0f, 0f, 0f, 1f,
             1.0f,  1.0f, 0f, 1f, 1f
        )

        private val triangleVertices: FloatBuffer = ByteBuffer
            .allocateDirect(triangleVerticesData.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(triangleVerticesData)
                position(0)
            }

        private val vertexShader = """
            uniform mat4 uMVPMatrix;
            uniform mat4 uSTMatrix;
            attribute vec4 aPosition;
            attribute vec4 aTextureCoord;
            varying vec2 vTextureCoord;
            void main() {
                gl_Position = uMVPMatrix * aPosition;
                vTextureCoord = (uSTMatrix * aTextureCoord).xy;
            }
        """.trimIndent()

        private val fragmentShader = """
            #extension GL_OES_EGL_image_external : require
            precision highp float;
            varying vec2 vTextureCoord;
            uniform samplerExternalOES sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, vTextureCoord);
            }
        """.trimIndent()

        fun initGl() {
            val vShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShader)
            val fShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShader)
            program = GLES20.glCreateProgram()
            GLES20.glAttachShader(program, vShader)
            GLES20.glAttachShader(program, fShader)
            GLES20.glLinkProgram(program)

            aPositionHandle = GLES20.glGetAttribLocation(program, "aPosition")
            aTextureCoordHandle = GLES20.glGetAttribLocation(program, "aTextureCoord")
            uMVPMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
            uSTMatrixHandle = GLES20.glGetUniformLocation(program, "uSTMatrix")

            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)
            textureId = textures[0]
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            GLES20.glTexParameterf(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_LINEAR.toFloat()
            )
            GLES20.glTexParameterf(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR.toFloat()
            )
            GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE
            )
            GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE
            )
            Matrix.setIdentityM(mvpMatrix, 0)
        }

        fun drawFrame(stMatrix: FloatArray) {
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            GLES20.glUseProgram(program)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)

            triangleVertices.position(0)
            GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false, 20, triangleVertices)
            GLES20.glEnableVertexAttribArray(aPositionHandle)

            triangleVertices.position(3)
            GLES20.glVertexAttribPointer(aTextureCoordHandle, 2, GLES20.GL_FLOAT, false, 20, triangleVertices)
            GLES20.glEnableVertexAttribArray(aTextureCoordHandle)

            GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, mvpMatrix, 0)
            GLES20.glUniformMatrix4fv(uSTMatrixHandle, 1, false, stMatrix, 0)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glFinish()
        }

        private fun loadShader(shaderType: Int, source: String): Int {
            val shader = GLES20.glCreateShader(shaderType)
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            return shader
        }
    }
}
