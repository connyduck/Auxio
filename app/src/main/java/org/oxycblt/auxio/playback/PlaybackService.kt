package org.oxycblt.auxio.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import org.oxycblt.auxio.R
import org.oxycblt.auxio.music.Song
import org.oxycblt.auxio.music.toURI
import org.oxycblt.auxio.playback.state.PlaybackStateCallback
import org.oxycblt.auxio.playback.state.PlaybackStateManager

private const val CHANNEL_ID = "CHANNEL_AUXIO_PLAYBACK"
private const val NOTIF_ID = 0xA0A0

// A Service that manages the single ExoPlayer instance and [attempts] to keep
// persistence if the app closes.
class PlaybackService : Service(), Player.EventListener, PlaybackStateCallback {
    private val player: SimpleExoPlayer by lazy {
        val p = SimpleExoPlayer.Builder(applicationContext).build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            p.experimentalSetOffloadSchedulingEnabled(true)
        }
        p.addListener(this)
        p
    }

    private val playbackManager = PlaybackStateManager.getInstance()

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(
        serviceJob + Dispatchers.Main
    )

    private var isForeground = false

    private lateinit var notification: Notification

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(this::class.simpleName, "Service is active.")

        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()

        notification = createNotification()

        playbackManager.addCallback(this)
    }

    override fun onDestroy() {
        super.onDestroy()

        player.release()
        serviceJob.cancel()
        playbackManager.removeCallback(this)

        stopForeground(true)
    }

    override fun onPlaybackStateChanged(state: Int) {
        if (state == Player.STATE_ENDED) {
            playbackManager.next()
        } else if (state == Player.STATE_READY) {
            startPollingPosition()
        }
    }

    override fun onSongUpdate(song: Song?) {
        song?.let {
            if (!isForeground) {
                startForeground(NOTIF_ID, notification)

                isForeground = true
            }

            val item = MediaItem.fromUri(it.id.toURI())
            player.setMediaItem(item)
            player.prepare()
            player.play()
        }
    }

    override fun onPlayingUpdate(isPlaying: Boolean) {
        if (isPlaying) {
            player.play()

            startPollingPosition()
        } else {
            player.pause()
        }
    }

    override fun onSeekConfirm(position: Long) {
        player.seekTo(position * 1000)
    }

    // Awful Hack to get position polling to work, as exoplayer does not provide any
    // onPositionChanged callback for some inane reason.
    // FIXME: Don't be surprised if this causes problems.

    private fun pollCurrentPosition() = flow {
        while (player.currentPosition <= player.duration) {
            emit(player.currentPosition)
            delay(500)
        }
    }.conflate()

    private fun startPollingPosition() {
        serviceScope.launch {
            pollCurrentPosition().takeWhile { true }.collect {
                playbackManager.setPosition(it / 1000)
            }
        }
    }

    private fun createNotification(): Notification {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.description_playback),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }
        // TODO: Placeholder, implement proper media controls :)
        val notif = NotificationCompat.Builder(
            applicationContext,
            CHANNEL_ID
        )
            .setSmallIcon(R.drawable.ic_song)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.description_playback))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setChannelId(CHANNEL_ID)
            .build()

        return notif
    }
}
