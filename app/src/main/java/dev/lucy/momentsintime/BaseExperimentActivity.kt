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
import kotlinx.coroutines.Dispatchers
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
    
    // Error handling
    protected var errorCount = 0
    protected val maxErrorsBeforeRecovery = 3
    protected var lastError: String? = null
    protected var recoveryAttempted = false
    
    // Battery monitoring
    private var batteryLevel = 100
    private var isBatteryLow = false
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            batteryLevel = (level * 100 / scale.toFloat()).toInt()
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || 
                             status == BatteryManager.BATTERY_STATUS_FULL
            
            // Consider battery low if below 15% and not charging
            val newLowBatteryState = batteryLevel < 15 && !isCharging
            
            // Only log if state changed
            if (newLowBatteryState != isBatteryLow) {
                isBatteryLow = newLowBatteryState
                if (isBatteryLow) {
                    Log.w(TAG, "Battery level low: $batteryLevel%")
                    try {
                        val logger = dev.lucy.momentsintime.logging.EventLogger.getInstance()
                        logger.logEvent(
                            dev.lucy.momentsintime.logging.EventType.SYSTEM_WARNING,
                            details = mapOf("type" to "low_battery", "level" to batteryLevel)
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to log battery warning: ${e.message}")
                    }
                }
            }
        }
    }
    
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
        
        // Register battery receiver
        registerBatteryReceiver()
        
        // Observe state changes
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                experimentState.collect { state ->
                    onStateChanged(state)
                    logStateTransition(state)
                }
            }
        }
    }
    
    /**
     * Log state transition to EventLogger
     */
    private fun logStateTransition(state: ExperimentState) {
        try {
            val logger = dev.lucy.momentsintime.logging.EventLogger.getInstance()
            logger.logStateChange(state.name, details = mapOf(
                "block" to currentBlock,
                "trial" to currentTrial,
                "elapsedTime" to getElapsedExperimentTime()
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log state transition: ${e.message}")
        }
    }
    
    /**
     * Register battery receiver to monitor battery level
     */
    private fun registerBatteryReceiver() {
        try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            registerReceiver(batteryReceiver, filter)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register battery receiver: ${e.message}")
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
        try {
            unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering battery receiver: ${e.message}")
        }
        
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
        val oldState = _experimentState.value
        stateStartTime = SystemClock.elapsedRealtime()
        
        // Reset error recovery flag when transitioning to a new state
        if (oldState != newState) {
            recoveryAttempted = false
        }
        
        // Check for battery level before critical states
        if (isBatteryLow && (newState == ExperimentState.SPEECH_RECORDING || 
                            newState == ExperimentState.TRIAL_VIDEO)) {
            // Log warning but continue
            Log.w(TAG, "Transitioning to $newState with low battery ($batteryLevel%)")
        }
        
        _experimentState.value = newState
    }
    
    /**
     * Handle error during experiment
     * @return true if error was handled, false if experiment should abort
     */
    protected fun handleError(errorMessage: String, errorSource: String): Boolean {
        lastError = errorMessage
        errorCount++
        
        try {
            // Log the error
            val logger = dev.lucy.momentsintime.logging.EventLogger.getInstance()
            logger.logError("$errorSource error: $errorMessage")
            
            // Save logs immediately in case of crash
            logger.saveEvents(true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log error: ${e.message}")
        }
        
        // If too many errors, suggest recovery
        if (errorCount >= maxErrorsBeforeRecovery && !recoveryAttempted) {
            recoveryAttempted = true
            return false // Suggest stopping experiment
        }
        
        return true // Continue experiment
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
            
            // Log video start and send trigger (non-blocking)
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val logger = dev.lucy.momentsintime.logging.EventLogger.getInstance()
                    val eventType = dev.lucy.momentsintime.logging.EventType.VIDEO_START
                    
                    // Log the event
                    logger.logVideoEvent(
                        eventType,
                        currentBlock,
                        currentTrial,
                        videoName
                    )
                    
                    // Send trigger if helper is available
                    try {
                        val activity = this@BaseExperimentActivity as? ExperimentActivity
                        activity?.serialPortHelper?.sendEventTrigger(eventType)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error sending video start trigger: ${e.message}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error logging video start: ${e.message}")
                }
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
            
            // Log video end and send trigger (non-blocking)
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val logger = dev.lucy.momentsintime.logging.EventLogger.getInstance()
                    val eventType = dev.lucy.momentsintime.logging.EventType.VIDEO_END
                    
                    // Log the event
                    logger.logVideoEvent(
                        eventType,
                        currentBlock,
                        currentTrial,
                        currentVideoName ?: "unknown"
                    )
                    
                    // Send trigger if helper is available
                    try {
                        val activity = this@BaseExperimentActivity as? ExperimentActivity
                        activity?.serialPortHelper?.sendEventTrigger(eventType)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error sending video end trigger: ${e.message}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error logging video end: ${e.message}")
                }
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
        
        if (handleError(errorMessage, "Video playback")) {
            // Default implementation: move to next state
            if (experimentState.value == ExperimentState.TRIAL_VIDEO) {
                transitionToState(ExperimentState.FIXATION_DELAY)
            }
        } else {
            // Critical error - show dialog in UI thread
            lifecycleScope.launch(Dispatchers.Main) {
                showErrorDialog(
                    "Critical Video Error",
                    "Multiple video errors occurred. Last error: $errorMessage\n\nDo you want to continue the experiment?"
                )
            }
        }
    }
    
    /**
     * Show error dialog with recovery options
     */
    protected open fun showErrorDialog(title: String, message: String) {
        // To be implemented by subclasses
        Log.e(TAG, "Error dialog: $title - $message")
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
