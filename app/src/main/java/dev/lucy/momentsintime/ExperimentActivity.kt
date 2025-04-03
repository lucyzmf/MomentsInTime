package dev.lucy.momentsintime

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.time.LocalDate

class ExperimentActivity : BaseExperimentActivity() {
    
    private lateinit var statusTextView: TextView
    private lateinit var blockTextView: TextView
    private lateinit var trialTextView: TextView
    private lateinit var timeTextView: TextView
    private lateinit var startButton: Button
    private lateinit var nextButton: Button
    private lateinit var playerView: PlayerView
    private lateinit var experimentContentTextView: TextView
    
    private var participantId: Int = -1
    private var dateString: String = ""
    private var config: ExperimentConfig.Standard? = null
    
    private val handler = Handler(Looper.getMainLooper())
    private val updateTimeRunnable = object : Runnable {
        override fun run() {
            updateTimeDisplay()
            handler.postDelayed(this, 100) // Update every 100ms
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_experiment)
        
        // Get intent data
        participantId = intent.getIntExtra("PARTICIPANT_ID", -1)
        dateString = intent.getStringExtra("DATE") ?: LocalDate.now().toString()
        
        // Create experiment config
        config = ExperimentConfig.Standard(
            participantId = participantId,
            date = LocalDate.parse(dateString)
        )
        
        // Initialize views
        statusTextView = findViewById(R.id.statusTextView)
        blockTextView = findViewById(R.id.blockTextView)
        trialTextView = findViewById(R.id.trialTextView)
        timeTextView = findViewById(R.id.timeTextView)
        startButton = findViewById(R.id.startButton)
        nextButton = findViewById(R.id.nextButton)
        playerView = findViewById(R.id.playerView)
        experimentContentTextView = findViewById(R.id.experimentContentTextView)
        
        // Connect player to view
        playerView.player = player
        
        // Set up button listeners
        startButton.setOnClickListener {
            initializeExperiment(config?.blocks ?: 3, config?.trialsPerBlock ?: 5)
            startButton.visibility = View.GONE
            nextButton.visibility = View.VISIBLE
            startNextBlock()
        }
        
        nextButton.setOnClickListener {
            handleNextButtonClick()
        }
        
        // Start time updates
        handler.post(updateTimeRunnable)
        
        // Observe state changes
        lifecycleScope.launch {
            experimentState.collect { state ->
                updateUI(state)
            }
        }
    }
    
    override fun onDestroy() {
        handler.removeCallbacks(updateTimeRunnable)
        super.onDestroy()
    }
    
    override fun onStateChanged(state: ExperimentState) {
        super.onStateChanged(state)
        
        when (state) {
            ExperimentState.BLOCK_START -> {
                // Automatically transition to first trial after a delay
                handler.postDelayed({
                    startNextTrial()
                }, 2000)
            }
            
            ExperimentState.TRIAL_VIDEO -> {
                // Simulate video playback, then transition to fixation
                handler.postDelayed({
                    transitionToState(ExperimentState.FIXATION_DELAY)
                }, 3000)
            }
            
            ExperimentState.FIXATION_DELAY -> {
                // After fixation delay, transition to speech recording
                handler.postDelayed({
                    transitionToState(ExperimentState.SPEECH_RECORDING)
                }, 1000)
            }
            
            ExperimentState.SPEECH_RECORDING -> {
                // After speech recording duration, prepare for next trial
                handler.postDelayed({
                    if (currentTrial < trialsPerBlock) {
                        startNextTrial()
                    } else {
                        transitionToState(ExperimentState.BLOCK_END)
                    }
                }, config?.speechDurationMs ?: 3000)
            }
            
            ExperimentState.BLOCK_END -> {
                // Show next button to proceed to next block
                nextButton.isEnabled = true
            }
            
            ExperimentState.EXPERIMENT_END -> {
                // Experiment complete
                nextButton.isEnabled = false
                nextButton.text = "Done"
            }
            
            else -> { /* No action needed */ }
        }
    }
    
    private fun handleNextButtonClick() {
        when (experimentState.value) {
            ExperimentState.IDLE -> {
                startNextBlock()
            }
            
            ExperimentState.BLOCK_END -> {
                startNextBlock()
            }
            
            ExperimentState.EXPERIMENT_END -> {
                finish()
            }
            
            else -> { /* No action needed */ }
        }
    }
    
    private fun updateUI(state: ExperimentState) {
        // Update status text
        statusTextView.text = "Status: ${state.name}"
        
        // Update block and trial counters
        blockTextView.text = "Block: $currentBlock / $totalBlocks"
        trialTextView.text = "Trial: $currentTrial / $trialsPerBlock"
        
        // Update experiment content visibility
        when (state) {
            ExperimentState.TRIAL_VIDEO -> {
                playerView.visibility = View.VISIBLE
                experimentContentTextView.visibility = View.GONE
            }
            else -> {
                playerView.visibility = View.GONE
                experimentContentTextView.visibility = View.VISIBLE
                
                // Update content text based on state
                experimentContentTextView.text = when (state) {
                    ExperimentState.BLOCK_START -> "Block $currentBlock Starting..."
                    ExperimentState.FIXATION_DELAY -> "+"  // Fixation cross
                    ExperimentState.SPEECH_RECORDING -> "Please describe what you saw"
                    ExperimentState.BLOCK_END -> "Block $currentBlock Complete"
                    ExperimentState.EXPERIMENT_END -> "Experiment Complete"
                    else -> "Experiment Content Area"
                }
            }
        }
        
        // Update button state
        when (state) {
            ExperimentState.IDLE -> {
                nextButton.isEnabled = true
                nextButton.text = "Start"
            }
            
            ExperimentState.BLOCK_END -> {
                nextButton.isEnabled = true
                nextButton.text = "Next Block"
            }
            
            ExperimentState.EXPERIMENT_END -> {
                nextButton.isEnabled = true
                nextButton.text = "Finish"
            }
            
            else -> {
                nextButton.isEnabled = false
                nextButton.text = "Next"
            }
        }
    }
    
    override fun onVideoError(errorMessage: String) {
        super.onVideoError(errorMessage)
        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
    }
    
    private fun updateTimeDisplay() {
        val elapsedMs = getElapsedExperimentTime()
        val seconds = (elapsedMs / 1000) % 60
        val minutes = (elapsedMs / (1000 * 60)) % 60
        
        timeTextView.text = String.format("Time: %02d:%02d.%03d", 
            minutes, seconds, elapsedMs % 1000)
    }
}
