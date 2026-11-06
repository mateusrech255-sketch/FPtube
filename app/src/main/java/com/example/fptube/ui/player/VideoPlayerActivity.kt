package br.com.fpagency.fptube.ui.player

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import br.com.fpagency.fptube.core.AppLogger
import br.com.fpagency.fptube.databinding.ActivityVideoPlayerBinding
import dagger.hilt.android.AndroidEntryPoint
import java.io.File

@AndroidEntryPoint
class VideoPlayerActivity : ComponentActivity() {

    private lateinit var binding: ActivityVideoPlayerBinding
    private var player: ExoPlayer? = null
    private var currentUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val uri = intent.data ?: intent.getStringExtra("VIDEO_PATH")?.let { path ->
            if (path.startsWith("content://") || path.startsWith("file://")) {
                Uri.parse(path)
            } else {
                Uri.fromFile(File(path))
            }
        }

        currentUri = uri

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnExternal.setOnClickListener {
            openInExternalPlayer()
        }

        if (uri != null) {
            initializePlayer(uri)
        } else {
            finish()
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
    private fun initializePlayer(videoUri: Uri) {
        AppLogger.i("Inicializando player com URI: $videoUri", "VideoPlayerActivity")
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this))
            .build().also { exoPlayer ->
                binding.playerView.player = exoPlayer
                binding.playerView.useController = true
                binding.playerView.setShowBuffering(androidx.media3.ui.PlayerView.SHOW_BUFFERING_ALWAYS)
                binding.playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                
                exoPlayer.addListener(object : androidx.media3.common.Player.Listener {
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        AppLogger.e("Erro na reprodução do vídeo: ${error.message}", "VideoPlayerActivity", error)
                        openInExternalPlayer()
                    }
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        AppLogger.i("PlaybackState changed: $playbackState", "VideoPlayerActivity")
                    }
                })

                val mediaItem = MediaItem.fromUri(videoUri)
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
            }
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }
}
