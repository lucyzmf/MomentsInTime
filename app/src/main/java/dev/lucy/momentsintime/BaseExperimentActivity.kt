package dev.lucy.momentsintime

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Base activity for experiment execution with state management.
 */
abstract class BaseExperimentActivity : AppCompatActivity() {

    // State management
    private val _experimentState = MutableStateFlow(ExperimentState.IDLE)
    val experimentState: StateFlow<ExperimentState> = _experimentState.asStateFlow()

    // Counters
    protected var currentBlock = 0
    protected var currentTrial = 0
    protected var totalBlocks = 0
    protected var trialsPerBlock = 0

    // Time tracking
    var experimentStartTime = 0L
    private var stateStartTime = 0L
    private var wakeLock: PowerManager.WakeLock? = null
    
    // Video playback
    protected var player: ExoPlayer? = null
    private var currentVideoName: String? = null
    private var videoStartTime = 0L
    private var videoDuration = 0L
    
    companion object {
        private const val TAG = "BaseExperimentActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Keep screen on during experiment
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // Acquire wake lock to prevent CPU from sleeping
        acquireWakeLock()
        
        // Initialize ExoPlayer
        initializePlayer()
        
        // Observe state changes
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                experimentState.collect { state ->
                    onStateChanged(state)
                }
            }
        }
    }
    
    override fun onStart() {
        super.onStart()
        if (player == null) {
            initializePlayer()
        }
    }
    
    override fun onStop() {
        releasePlayer()
        super.onStop()
    }

    override fun onDestroy() {
        releaseWakeLock()
        releasePlayer()
        super.onDestroy()
    }
    
    /**
     * Initialize the ExoPlayer instance
     */
    private fun initializePlayer() {
        player = ExoPlayer.Builder(this)
            .build()
            .also {
                it.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) {
                            onVideoPlaybackEnded()
                        }
                    }
                })
            }
    }
    
    /**
     * Release the ExoPlayer instance
     */
    private fun releasePlayer() {
        player?.release()
        player = null
    }

    /**
     * Initialize experiment with configuration parameters
     */
    protected fun initializeExperiment(blocks: Int, trialsPerBlock: Int) {
        this.totalBlocks = blocks
        this.trialsPerBlock = trialsPerBlock
        this.currentBlock = 0
        this.currentTrial = 0
        this.experimentStartTime = SystemClock.elapsedRealtime()
        
        // Start in IDLE state
        transitionToState(ExperimentState.IDLE)
    }

    /**
     * Transition to a new state
     */
    protected fun transitionToState(newState: ExperimentState) {
        stateStartTime = SystemClock.elapsedRealtime()
        _experimentState.value = newState
    }

    /**
     * Called when the state changes
     */
    protected open fun onStateChanged(state: ExperimentState) {
        // Handle video playback when in TRIAL_VIDEO state
        if (state == ExperimentState.TRIAL_VIDEO) {
            playCurrentTrialVideo()
        } else {
            // Stop video if playing and not in video state
            player?.stop()
        }
    }
    
    /**
     * Play the video for the current trial
     */
    protected fun playCurrentTrialVideo() {
        val videoIndex = (currentBlock - 1) * trialsPerBlock + (currentTrial - 1)
        val videoName = getVideoNameForTrial(videoIndex)
        playVideo(videoName)
    }
    
    /**
     * Get the video name for a specific trial index
     */
    protected open fun getVideoNameForTrial(trialIndex: Int): String {
        return "video${trialIndex + 1}"
    }
    
    /**
     * Play a video by name
     */
    protected open fun playVideo(videoName: String) {
        try {
            currentVideoName = videoName
            videoStartTime = SystemClock.elapsedRealtime()
            
            // Log video start
            try {
                val logger = dev.lucy.momentsintime.logging.EventLogger.getInstance()
                logger.logVideoEvent(
                    dev.lucy.momentsintime.logging.EventType.VIDEO_START,
                    currentBlock,
                    currentTrial,
                    videoName
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error logging video start: ${e.message}")
            }
            
            // Get video resource ID
            val resourceId = resources.getIdentifier(
                videoName, "raw", packageName
            )
            
            if (resourceId == 0) {
                Log.e(TAG, "Video resource not found: $videoName")
                onVideoError("Video resource not found: $videoName")
                return
            }
            
            // Create media item from raw resource
            val videoUri = Uri.parse("android.resource://$packageName/$resourceId")
            val mediaItem = MediaItem.fromUri(videoUri)
            
            // Prepare and play the video
            player?.apply {
                setMediaItem(mediaItem)
                prepare()
                play()
            }
            
            Log.d(TAG, "Started playing video: $videoName")
        } catch (e: Exception) {
            Log.e(TAG, "Error playing video: ${e.message}", e)
            onVideoError("Error playing video: ${e.message}")
        }
    }
    
    /**
     * Called when video playback ends
     */
    private fun onVideoPlaybackEnded() {
        if (experimentState.value == ExperimentState.TRIAL_VIDEO) {
            videoDuration = SystemClock.elapsedRealtime() - videoStartTime
            Log.d(TAG, "Video ended: $currentVideoName, duration: $videoDuration ms")
            
            // Log video end
            try {
                val logger = dev.lucy.momentsintime.logging.EventLogger.getInstance()
                logger.logVideoEvent(
                    dev.lucy.momentsintime.logging.EventType.VIDEO_END,
                    currentBlock,
                    currentTrial,
                    currentVideoName ?: "unknown"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error logging video end: ${e.message}")
            }
            
            // Transition to fixation delay
            transitionToState(ExperimentState.FIXATION_DELAY)
        }
    }
    
    /**
     * Called when there's an error playing the video
     */
    protected open fun onVideoError(errorMessage: String) {
        Log.e(TAG, errorMessage)
        // Default implementation: move to next state
        if (experimentState.value == ExperimentState.TRIAL_VIDEO) {
            transitionToState(ExperimentState.FIXATION_DELAY)
        }
    }

    /**
     * Start the next block
     */
    protected fun startNextBlock() {
        currentBlock++
        currentTrial = 0
        
        if (currentBlock <= totalBlocks) {
            transitionToState(ExperimentState.BLOCK_START)
        } else {
            transitionToState(ExperimentState.EXPERIMENT_END)
        }
    }

    /**
     * Start the next trial
     */
    protected fun startNextTrial() {
        currentTrial++
        
        if (currentTrial <= trialsPerBlock) {
            transitionToState(ExperimentState.TRIAL_VIDEO)
        } else {
            transitionToState(ExperimentState.BLOCK_END)
        }
    }

    /**
     * Get elapsed time since experiment started
     */
    protected fun getElapsedExperimentTime(): Long {
        return SystemClock.elapsedRealtime() - experimentStartTime
    }

    /**
     * Get elapsed time since current state started
     */
    protected fun getElapsedStateTime(): Long {
        return SystemClock.elapsedRealtime() - stateStartTime
    }

    /**
     * Acquire wake lock to prevent CPU from sleeping
     */
    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MomentsInTime:ExperimentWakeLock"
        )
        wakeLock?.acquire(10*60*1000L /*10 minutes*/)
    }

    /**
     * Release wake lock
     */
    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        wakeLock = null
    }
}
