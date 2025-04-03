package dev.lucy.momentsintime

import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.content.Context

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
    private var experimentStartTime = 0L
    private var stateStartTime = 0L
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Keep screen on during experiment
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // Acquire wake lock to prevent CPU from sleeping
        acquireWakeLock()
        
        // Observe state changes
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                experimentState.collect { state ->
                    onStateChanged(state)
                }
            }
        }
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
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
        // To be implemented by subclasses
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
