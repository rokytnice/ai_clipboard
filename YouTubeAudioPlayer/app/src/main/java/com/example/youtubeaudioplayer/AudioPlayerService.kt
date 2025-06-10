package com.example.youtubeaudioplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media.session.MediaButtonReceiver // Correct import

class AudioPlayerService : Service(), MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener, MediaPlayer.OnCompletionListener, AudioManager.OnAudioFocusChangeListener {

    private var mediaPlayer: MediaPlayer? = null
    lateinit var mediaSession: MediaSessionCompat // Public for Activity to access token
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    private val binder = LocalBinder()

    private var currentTrackUrl: String? = null
    private var isForegroundService = false


    companion object {
        const val ACTION_PLAY = "com.example.youtubeaudioplayer.ACTION_PLAY"
        const val ACTION_PAUSE = "com.example.youtubeaudioplayer.ACTION_PAUSE"
        const val ACTION_STOP = "com.example.youtubeaudioplayer.ACTION_STOP"
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "AudioPlayerServiceChannel"
        private const val TAG = "AudioPlayerService"
    }

    inner class LocalBinder : Binder() {
        fun getService(): AudioPlayerService = this@AudioPlayerService
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        mediaSession = MediaSessionCompat(this, TAG)

        mediaSession.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                // This is called when MediaControllerCompat.TransportControls.play() is invoked
                // Or from a media button event. The actual URL should be passed to playTrack beforehand.
                currentTrackUrl?.let { playTrack(it) } ?: Log.w(TAG, "Play action received but no URL")
            }

            override fun onPause() {
                pauseTrack()
            }

            override fun onStop() {
                stopTrackAndService() // Or just stopTrack() if service should live longer
            }
        })
        // Removed mediaSession.isActive = true here, set active when playback actually starts or is ready
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand received action: ${intent?.action}")
        MediaButtonReceiver.handleIntent(mediaSession, intent)

        when (intent?.action) {
            // ACTION_PLAY, ACTION_PAUSE, ACTION_STOP are now handled by MediaSession callback
            // but we might still get direct intents if we choose to send them
        }
        return START_NOT_STICKY
    }

    fun playTrack(url: String) {
        if (url.isEmpty()) {
            Log.w(TAG, "playTrack called with empty URL")
            return
        }
        if (!requestAudioFocus()) {
            Log.w(TAG, "Could not get audio focus for $url")
            return
        }

        mediaSession.isActive = true // Activate session when playback is requested
        currentTrackUrl = url
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            try {
                setDataSource(url)
                setOnPreparedListener(this@AudioPlayerService)
                setOnErrorListener(this@AudioPlayerService)
                setOnCompletionListener(this@AudioPlayerService)
                prepareAsync()
                updatePlaybackState(PlaybackStateCompat.STATE_BUFFERING)
            } catch (e: IOException) {
                Log.e(TAG, "MediaPlayer setDataSource failed for $url", e)
                updatePlaybackState(PlaybackStateCompat.STATE_ERROR, "Cannot play this link. Check URL or network.")
                abandonAudioFocus()
            } catch (e: IllegalStateException) {
                 Log.e(TAG, "MediaPlayer IllegalStateException for $url", e)
                updatePlaybackState(PlaybackStateCompat.STATE_ERROR, "Player internal error. Please try again.")
                abandonAudioFocus()
            }
        }
    }

    fun pauseTrack() {
        mediaPlayer?.takeIf { it.isPlaying }?.pause()
        updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
        // To allow notification to be swiped away when paused:
        stopForeground(false)
        isForegroundService = true // Still considered foreground due to persistent notification
    }

    fun stopTrack() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        currentTrackUrl = null // Clear the current track
        abandonAudioFocus()
        mediaSession.isActive = false // Deactivate session when playback stops
        updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
        stopForeground(true)
        isForegroundService = false
    }

    private fun stopTrackAndService() {
        stopTrack()
        Log.d(TAG, "Stopping service.")
        stopSelf()
    }

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying ?: false

    override fun onBind(intent: Intent): IBinder = binder

    override fun onDestroy() {
        Log.d(TAG, "onDestroy called")
        stopTrack() // Clean up all resources
        mediaSession.release()
        super.onDestroy()
    }

    override fun onPrepared(mp: MediaPlayer?) {
        Log.d(TAG, "MediaPlayer prepared, starting playback.")
        mp?.start()
        updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
        // Show foreground notification
        val notification = buildNotification(PlaybackStateCompat.STATE_PLAYING)
        startForeground(NOTIFICATION_ID, notification)
        isForegroundService = true
    }

    override fun onCompletion(mp: MediaPlayer?) {
        Log.d(TAG, "MediaPlayer onCompletion.")
        updatePlaybackState(PlaybackStateCompat.STATE_STOPPED, "Playback completed")
        // Don't call stopForeground(true) here if you want the notification to persist
        // with a 'play' button to restart. Or call stopTrack() to fully clean up.
        // For now, let's keep the notification and allow restart via play button.
        stopForeground(false) // Keep notification, make it dismissable
        isForegroundService = true // Still technically foreground
        // Activity should be notified to update UI
    }

    override fun onError(mp: MediaPlayer?, what: Int, extra: Int): Boolean {
        Log.e(TAG, "MediaPlayer Error - What: $what, Extra: $extra")
        updatePlaybackState(PlaybackStateCompat.STATE_ERROR, "Playback error: $what, $extra")
        stopTrack() // Clean up on error
        return true
    }

    private fun requestAudioFocus(): Boolean {
        val result: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(this)
                .build()
            result = audioManager.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            result = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(this)
        }
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> { Log.d(TAG, "Focus Loss"); pauseTrack() } // Changed to pause, can be stopTrack()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> { Log.d(TAG, "Focus Loss Transient"); pauseTrack() }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> { Log.d(TAG, "Focus Loss Transient Can Duck"); mediaPlayer?.setVolume(0.3f, 0.3f) }
            AudioManager.AUDIOFOCUS_GAIN -> { Log.d(TAG, "Focus Gain"); mediaPlayer?.setVolume(1.0f, 1.0f) /* Optionally resume if was playing */ }
        }
    }

    private fun updatePlaybackState(state: Int, errorMessage: String? = null) {
        val position = mediaPlayer?.currentPosition?.toLong() ?: PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN
        val playbackStateBuilder = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_STOP
            )
            .setState(state, position, 1.0f)

        if (state == PlaybackStateCompat.STATE_ERROR && errorMessage != null) {
            playbackStateBuilder.setErrorMessage(PlaybackStateCompat.ERROR_CODE_UNKNOWN_ERROR, errorMessage)
        }

        mediaSession.setPlaybackState(playbackStateBuilder.build())

        if (isForegroundService && state != PlaybackStateCompat.STATE_STOPPED && state != PlaybackStateCompat.STATE_NONE) {
             val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
             notificationManager.notify(NOTIFICATION_ID, buildNotification(state))
        } else if (state == PlaybackStateCompat.STATE_STOPPED || state == PlaybackStateCompat.STATE_NONE) {
            // If explicitly stopped, remove notification or update it to a stopped state
            // For now, buildNotification handles the visual change. stopForeground(true) removes it.
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Audio Player Service", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(playbackState: Int): Notification {
        val playPauseAction = if (playbackState == PlaybackStateCompat.STATE_PLAYING || playbackState == PlaybackStateCompat.STATE_BUFFERING) {
            NotificationCompat.Action(android.R.drawable.ic_media_pause, "Pause", MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PAUSE))
        } else {
            NotificationCompat.Action(android.R.drawable.ic_media_play, "Play", MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY))
        }
        val stopAction = NotificationCompat.Action(android.R.drawable.ic_media_stop, "Stop", MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_STOP))

        val activityIntent = Intent(this, MainActivity::class.java)
        val pendingActivityIntent = PendingIntent.getActivity(this, 0, activityIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("YouTube Audio Player")
            .setContentText(if (playbackState == PlaybackStateCompat.STATE_ERROR) "Error" else "Playing audio")
            .setSmallIcon(R.drawable.ic_music_note)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(playPauseAction)
            .addAction(stopAction)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession.sessionToken)
                .setShowActionsInCompactView(0, 1))
            .setContentIntent(pendingActivityIntent)
            .setOngoing(playbackState == PlaybackStateCompat.STATE_PLAYING || playbackState == PlaybackStateCompat.STATE_BUFFERING)
            .build()
    }
}
