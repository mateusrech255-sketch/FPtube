package br.com.fpagency.fptube.ui.player

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import br.com.fpagency.fptube.core.AppLogger
import br.com.fpagency.fptube.core.ui.R as UiR
import br.com.fpagency.fptube.core.ui.theme.FPtubeTheme
import dagger.hilt.android.AndroidEntryPoint
import java.io.File

@AndroidEntryPoint
class VideoPlayerActivity : ComponentActivity() {

    private var player: ExoPlayer? = null
    private var currentUri: Uri? = null
    private var triedSoftwareFallback = false

    companion object {
        private const val KEY_POSITION = "playback_position"
        private const val KEY_PLAY_WHEN_READY = "play_when_ready"
    }

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri = intent.data ?: intent.getStringExtra("VIDEO_PATH")?.let { path ->
            if (path.startsWith("content://") || path.startsWith("file://")) {
                Uri.parse(path)
            } else {
                Uri.fromFile(File(path))
            }
        }

        currentUri = uri
        if (uri == null) {
            finish()
            return
        }

        val initialPosition = savedInstanceState?.getLong(KEY_POSITION, 0L) ?: 0L
        val initialPlayWhenReady = savedInstanceState?.getBoolean(KEY_PLAY_WHEN_READY, true) ?: true

        var activePlayer by mutableStateOf<ExoPlayer?>(null)
        activePlayer = initializePlayer(
            videoUri = uri,
            startPositionMs = initialPosition,
            startPlayWhenReady = initialPlayWhenReady,
            preferSoftwareDecoder = false
        ) { failedPos ->
            if (!triedSoftwareFallback) {
                triedSoftwareFallback = true
                AppLogger.w("Fallback automático para decoder de software em VideoPlayerActivity", "VideoPlayerActivity")
                player?.release()
                player = null
                activePlayer = initializePlayer(
                    videoUri = uri,
                    startPositionMs = failedPos,
                    startPlayWhenReady = true,
                    preferSoftwareDecoder = true
                )
            }
        }
        setContent {
            FPtubeTheme {
                val exoPlayer = activePlayer ?: return@FPtubeTheme
                val lifecycleOwner = LocalLifecycleOwner.current
                var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

                DisposableEffect(lifecycleOwner, exoPlayer) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                            Lifecycle.Event.ON_STOP -> exoPlayer.pause()
                            else -> Unit
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                        playerViewRef?.player = null
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                playerViewRef = this
                                useController = true
                                keepScreenOn = true
                                setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                this.player = activePlayer
                            }
                        },
                        update = { view ->
                            if (view.player !== activePlayer) {
                                view.player = activePlayer
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    IconButton(
                        onClick = { finish() },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp)
                            .size(48.dp)
                            .background(Color(0x80000000), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(UiR.string.back),
                            tint = Color.White
                        )
                    }

                    IconButton(
                        onClick = { openInExternalPlayer() },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                            .size(48.dp)
                            .background(Color(0x80000000), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = stringResource(UiR.string.share),
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        player?.let { exo ->
            outState.putLong(KEY_POSITION, exo.currentPosition.coerceAtLeast(0L))
            outState.putBoolean(KEY_PLAY_WHEN_READY, exo.playWhenReady)
        }
    }

    private fun openInExternalPlayer() {
        val uri = currentUri ?: return
        try {
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(viewIntent, "Abrir com..."))
        } catch (e: Exception) {
            AppLogger.e("Erro ao abrir player externo", "VideoPlayerActivity", e)
            Toast.makeText(this, "Nenhum aplicativo encontrado para reproduzir vídeo", Toast.LENGTH_SHORT).show()
        }
    }

    @OptIn(UnstableApi::class)
    private fun initializePlayer(
        videoUri: Uri,
        startPositionMs: Long = 0L,
        startPlayWhenReady: Boolean = true,
        preferSoftwareDecoder: Boolean = false,
        onHardwareError: ((Long) -> Unit)? = null
    ): ExoPlayer {
        AppLogger.i("Inicializando player com URI: $videoUri (sw=$preferSoftwareDecoder)", "VideoPlayerActivity")
        val codecSelector = MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
            val infos = MediaCodecSelector.DEFAULT.getDecoderInfos(
                mimeType,
                requiresSecureDecoder,
                requiresTunnelingDecoder
            )
            // Em emuladores x86_64, c2.goldfish.h264.decoder falha em certas resoluções; priorizar software decoder quando solicitado ou em goldfish
            infos.sortedByDescending { info ->
                val name = info.name.lowercase()
                val isSoftware = name.startsWith("c2.android.") || name.startsWith("omx.google.")
                val isGoldfish = name.contains("goldfish")
                when {
                    preferSoftwareDecoder && isSoftware -> 2
                    isGoldfish -> -1
                    isSoftware -> 1
                    else -> 2
                }
            }
        }

        val renderersFactory = DefaultRenderersFactory(this)
            .setEnableDecoderFallback(true)
            .setMediaCodecSelector(codecSelector)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        return ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this))
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build().also { exoPlayer ->
                player = exoPlayer
                exoPlayer.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        AppLogger.e("Erro na reprodução do vídeo: ${error.message}", "VideoPlayerActivity", error)
                        val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                        if (!preferSoftwareDecoder && onHardwareError != null) {
                            onHardwareError(pos)
                        } else {
                            openInExternalPlayer()
                        }
                    }
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        AppLogger.i("PlaybackState changed: $playbackState", "VideoPlayerActivity")
                    }
                })

                val mediaItem = MediaItem.fromUri(videoUri)
                exoPlayer.setMediaItem(mediaItem, startPositionMs)
                exoPlayer.prepare()
                exoPlayer.playWhenReady = startPlayWhenReady
            }
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }
}
