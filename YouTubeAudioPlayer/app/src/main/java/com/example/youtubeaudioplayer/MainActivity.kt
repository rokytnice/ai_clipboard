package com.example.youtubeaudioplayer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.youtube.YoutubeService
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var etVideoUrl: EditText
    private lateinit var btnFetchAudio: Button
    private lateinit var tvStatus: TextView
    private lateinit var playbackControls: LinearLayout
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnStop: ImageButton

    private var currentAudioStreamUrl: String? = null
    private val mainScope = CoroutineScope(Dispatchers.Main)
    private val ioScope = CoroutineScope(Dispatchers.IO)

    private var audioService: AudioPlayerService? = null
    private var isServiceBound = false
    private var mediaController: MediaControllerCompat? = null
    private val TAG = "MainActivity"

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as AudioPlayerService.LocalBinder
            audioService = binder.getService()
            isServiceBound = true
            Log.d(TAG, "AudioPlayerService connected")
            try {
                mediaController = MediaControllerCompat(this@MainActivity, audioService!!.mediaSession.sessionToken)
                mediaController?.registerCallback(mediaControllerCallback)
                // Update UI with current state from service
                val lastState = mediaController?.playbackState
                if (lastState != null) {
                    updateUIFromPlaybackState(lastState)
                } else {
                     // If service is new, it might not have a state yet.
                    updateUIFromPlaybackState(PlaybackStateCompat.Builder().setState(PlaybackStateCompat.STATE_NONE, 0, 1.0f).build())
                }
            } catch (e: Exception) { Log.e(TAG, "Error creating MediaController", e) }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            audioService = null
            isServiceBound = false
            mediaController?.unregisterCallback(mediaControllerCallback)
            mediaController = null
            Log.d(TAG, "AudioPlayerService disconnected")
        }
    }

    private val mediaControllerCallback = object : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            Log.d(TAG, "onPlaybackStateChanged: $state")
            state?.let { updateUIFromPlaybackState(it) }
        }
        // onMetadataChanged can be used if service sends metadata
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etVideoUrl = findViewById(R.id.etVideoUrl)
        btnFetchAudio = findViewById(R.id.btnFetchAudio)
        tvStatus = findViewById(R.id.tvStatus)
        tvStatus.text = "Enter a YouTube video URL to start."
        playbackControls = findViewById(R.id.playbackControls)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnStop = findViewById(R.id.btnStop)

        btnFetchAudio.setOnClickListener {
            val url = etVideoUrl.text.toString().trim()
            if (url.isEmpty()) { tvStatus.text = "Please enter a YouTube URL."; return@setOnClickListener }
            if (!url.contains("youtube.com/") && !url.contains("youtu.be/")) { tvStatus.text = "Please enter a valid YouTube URL."; return@setOnClickListener; }
            fetchAudioStream(url)
        }

        btnPlayPause.setOnClickListener { handlePlayPause() }
        btnStop.setOnClickListener { handleStop() }

        playbackControls.visibility = View.GONE // Initially hide, service state will update

        // Bind to service
        Intent(this, AudioPlayerService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                 // For background playback, service needs to be started explicitly
                 // This is often done when playback is initiated if not already running
            }
        }
    }

    private fun updateUIFromPlaybackState(state: PlaybackStateCompat) {
        Log.d(TAG, "Updating UI for state: ${state.state}")
        when (state.state) {
            PlaybackStateCompat.STATE_PLAYING, PlaybackStateCompat.STATE_BUFFERING -> {
                tvStatus.text = if (state.state == PlaybackStateCompat.STATE_PLAYING) "Playing" else "Buffering..."
                btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
                playbackControls.visibility = View.VISIBLE
            }
            PlaybackStateCompat.STATE_PAUSED -> {
                tvStatus.text = "Paused"
                btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
                playbackControls.visibility = View.VISIBLE
            }
            PlaybackStateCompat.STATE_STOPPED, PlaybackStateCompat.STATE_NONE -> {
                tvStatus.text = "Stopped. Fetch audio to play."
                btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
                // Keep controls visible or hide based on UX preference
                 playbackControls.visibility = if (currentAudioStreamUrl != null) View.VISIBLE else View.GONE

            }
            PlaybackStateCompat.STATE_ERROR -> {
                tvStatus.text = state.errorMessage?.toString() ?: "Error playing audio"
                btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
                playbackControls.visibility = View.VISIBLE // Keep controls to allow retry potentially
            }
        }
    }

    private fun fetchAudioStream(videoUrl: String) {
        mediaController?.transportControls?.stop() // Stop any existing playback via service

        tvStatus.text = "Fetching audio info..."
        btnFetchAudio.isEnabled = false
        currentAudioStreamUrl = null // Reset current URL until new one is fetched

        ioScope.launch {
            try {
                if (NewPipe.getDownloader() == null) { YoutubeHelper.initialize(applicationContext) }
                val service = NewPipe.getService(YoutubeService.SERVICE_ID)
                val streamInfo: StreamInfo = service.getStreamInfo(videoUrl)
                val audioStreams = streamInfo.audioStreams

                if (audioStreams.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) { tvStatus.text = "No audio streams found."; btnFetchAudio.isEnabled = true }
                    return@launch
                }

                var bestAudioStream: AudioStream? = audioStreams.firstOrNull { it.format.getName().contains("opus",true) } ?: audioStreams.firstOrNull { it.format.getName().contains("m4a",true) } ?: audioStreams.firstOrNull()

                if (bestAudioStream != null) {
                    currentAudioStreamUrl = bestAudioStream.url // Store the new URL
                    Log.d(TAG, "Audio URL: ${currentAudioStreamUrl}")
                    withContext(Dispatchers.Main) {
                        tvStatus.text = "Audio ready. Press Play."
                        btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
                        playbackControls.visibility = View.VISIBLE
                        btnFetchAudio.isEnabled = true
                    }
                } else {
                    withContext(Dispatchers.Main) { tvStatus.text = "Could not find a suitable audio stream."; btnFetchAudio.isEnabled = true }
                }
            } catch (e: ReCaptchaException) {
                Log.e(TAG, "ReCaptchaException fetching audio stream", e)
                withContext(Dispatchers.Main) {
                    tvStatus.text = "Error: Video may be protected (ReCaptcha). Try another."
                    btnFetchAudio.isEnabled = true; playbackControls.visibility = View.GONE
                }
            } catch (e: ExtractionException) {
                Log.e(TAG, "ExtractionException fetching audio stream", e)
                withContext(Dispatchers.Main) {
                    tvStatus.text = "Error: Could not extract audio. Invalid URL or video?"
                    btnFetchAudio.isEnabled = true; playbackControls.visibility = View.GONE
                }
            } catch (e: IOException) {
                Log.e(TAG, "IOException fetching audio stream", e)
                withContext(Dispatchers.Main) {
                    tvStatus.text = "Network Error. Check connection and try again."
                    btnFetchAudio.isEnabled = true; playbackControls.visibility = View.GONE
                }
            } catch (e: Exception) {
                Log.e(TAG, "Generic error fetching audio stream", e)
                withContext(Dispatchers.Main) {
                    tvStatus.text = "Error: ${e.localizedMessage ?: "Failed to fetch audio."}"
                    btnFetchAudio.isEnabled = true; playbackControls.visibility = View.GONE
                }
            }
        }
    }

    private fun handlePlayPause() {
        if (!isServiceBound || mediaController == null) { tvStatus.text = "Service not ready."; return }

        val currentState = mediaController!!.playbackState?.state
        if (currentState == PlaybackStateCompat.STATE_PLAYING || currentState == PlaybackStateCompat.STATE_BUFFERING) {
            mediaController!!.transportControls.pause()
        } else { // Paused, Stopped, None, or Error
            if (!currentAudioStreamUrl.isNullOrEmpty()) {
                // Ensure service is started in foreground if we begin playback
                Intent(this, AudioPlayerService::class.java).also {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(it)
                    } else {
                        startService(it)
                    }
                }
                // The service's onPlay callback will call playTrack if URL is set.
                // Or we can call a specific method on the service instance if preferred.
                audioService?.playTrack(currentAudioStreamUrl!!)
                // Alternatively use mediaController.transportControls.playFromUri(Uri.parse(currentAudioStreamUrl!!), null)
                // but playTrack gives more control if we need to pass specific things.
            } else {
                tvStatus.text = "No audio loaded. Fetch audio first."
            }
        }
    }

    private fun handleStop() {
        if (!isServiceBound || mediaController == null) { return }
        mediaController!!.transportControls.stop()
        // Service should handle its lifecycle (stopForeground, stopSelf) if appropriate.
    }

    override fun onStart() {
        super.onStart()
        // Bind to service if not already bound (e.g. if activity was stopped and restarted)
        if (!isServiceBound) {
            Intent(this, AudioPlayerService::class.java).also { intent ->
                bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isServiceBound) {
            mediaController?.unregisterCallback(mediaControllerCallback)
            unbindService(serviceConnection)
            isServiceBound = false
            Log.d(TAG, "Service unbound in onDestroy")
        }
        // Note: Service might continue running in background if playback was active
    }
}
