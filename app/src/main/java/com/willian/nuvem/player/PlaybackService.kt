package com.willian.nuvem.player

import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.willian.nuvem.Nuvem

/**
 * Serviço de reprodução: a música continua tocando com a tela apagada e, enquanto
 * toca (e ao pausar/sair), grava a posição para o app perguntar "continuar de onde parou?".
 */
@UnstableApi
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val handler = Handler(Looper.getMainLooper())

    /** Quando o temporizador de dormir termina (0 = desligado). */
    @Volatile
    var sleepEndsAt: Long = 0L
        private set

    private val sleepRunnable = Runnable {
        player.pause()
        sleepEndsAt = 0L
    }

    /** Pausa a reprodução depois de [minutes] minutos; 0 cancela. */
    fun setSleepTimer(minutes: Int) {
        handler.removeCallbacks(sleepRunnable)
        if (minutes <= 0) {
            sleepEndsAt = 0L
            return
        }
        sleepEndsAt = System.currentTimeMillis() + minutes * 60_000L
        handler.postDelayed(sleepRunnable, minutes * 60_000L)
    }

    companion object {
        @Volatile
        var current: PlaybackService? = null
    }

    private val ticker = object : Runnable {
        override fun run() {
            savePosition()
            if (player.isPlaying) handler.postDelayed(this, 3000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        current = this
        val dataSource = DefaultDataSource.Factory(this, ProviderDataSource.Factory())
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_UNKNOWN).build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                handler.removeCallbacks(ticker)
                if (isPlaying) handler.postDelayed(ticker, 3000) else savePosition()
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) player.currentMediaItem?.mediaId?.let { Nuvem.positions.clear(it) }
            }
        })
        session = MediaSession.Builder(this, player).build()
    }

    private fun savePosition() {
        val id = player.currentMediaItem?.mediaId ?: return
        if (id.isEmpty() || player.playbackState == Player.STATE_ENDED) return
        val dur = player.duration
        Nuvem.positions.put(id, player.currentPosition, if (dur == C.TIME_UNSET) 0L else dur)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        savePosition()
        if (!player.isPlaying) stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        handler.removeCallbacks(sleepRunnable)
        if (current === this) current = null
        savePosition()
        session?.release()
        session = null
        player.release()
        super.onDestroy()
    }
}
