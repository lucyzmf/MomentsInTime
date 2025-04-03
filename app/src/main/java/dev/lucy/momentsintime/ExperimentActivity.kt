package dev.lucy.momentsintime

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.File
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
    private lateinit var fixationCrossLayout: View
    private lateinit var fixationCrossTextView: TextView
    private lateinit var countdownTextView: TextView
    
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
    
    // Audio recording
    private lateinit var audioRecorder: AudioRecorder
    private var currentRecordingFile: File? = null
    private var permissionsGranted = false
    
    // Permission request launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        permissionsGranted = allGranted
        
        if (allGranted) {
            Toast.makeText(this, "Audio recording permission granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Audio recording permission denied", Toast.LENGTH_LONG).show()
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
        
        // Initialize audio recorder
        audioRecorder = AudioRecorder(this)
        
        // Check and request permissions
        checkAndRequestPermissions()
        
        // Initialize views
        statusTextView = findViewById(R.id.statusTextView)
        blockTextView = findViewById(R.id.blockTextView)
        trialTextView = findViewById(R.id.trialTextView)
        timeTextView = findViewById(R.id.timeTextView)
        startButton = findViewById(R.id.startButton)
        nextButton = findViewById(R.id.nextButton)
        playerView = findViewById(R.id.playerView)
        experimentContentTextView = findViewById(R.id.experimentContentTextView)
        
        // Initialize fixation cross views
        fixationCrossLayout = findViewById(R.id.fixationCrossLayout)
        fixationCrossTextView = fixationCrossLayout.findViewById(R.id.fixationCrossTextView)
        countdownTextView = fixationCrossLayout.findViewById(R.id.countdownTextView)
        
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
        audioRecorder.stopRecording()
        super.onDestroy()
    }
    
    /**
     * Check and request necessary permissions
     */
    private fun checkAndRequestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        
        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest)
        } else {
            permissionsGranted = true
        }
    }
    
    override fun onStateChanged(state: ExperimentState) {
        super.onStateChanged(state)
        
        when (state) {
            ExperimentState.BLOCK_START -> {
                // Automatically transition to first trial after a delay
//                handler.postDelayed({
//                    startNextTrial()
//                }, 2000)
                startNextTrial()
            }
            
            ExperimentState.TRIAL_VIDEO -> {
                // Simulate video playback, then transition to fixation
//                handler.postDelayed({
//                    transitionToState(ExperimentState.FIXATION_DELAY)
//                }, 3000)
                playCurrentTrialVideo()
            }
            
            ExperimentState.FIXATION_DELAY -> {
                // Show fixation cross and start countdown
                startFixationCountdown(1000) // 1000ms delay
            }
            
            ExperimentState.SPEECH_RECORDING -> {
                // Start audio recording
                startAudioRecording()
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
                fixationCrossLayout.visibility = View.GONE
            }
            ExperimentState.FIXATION_DELAY -> {
                playerView.visibility = View.GONE
                experimentContentTextView.visibility = View.GONE
                fixationCrossLayout.visibility = View.VISIBLE
            }
            else -> {
                playerView.visibility = View.GONE
                experimentContentTextView.visibility = View.VISIBLE
                fixationCrossLayout.visibility = View.GONE
                
                // Update content text based on state
                experimentContentTextView.text = when (state) {
                    ExperimentState.BLOCK_START -> "Block $currentBlock Starting..."
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
    
    /**
     * Start the fixation cross countdown timer
     * @param durationMs Total duration of the fixation period in milliseconds
     */
    private fun startFixationCountdown(durationMs: Long) {
        val updateIntervalMs = 100L // Update every 100ms for smooth countdown
        val totalSteps = durationMs / updateIntervalMs
        var remainingSteps = totalSteps
        
        // Initial display
        updateCountdownDisplay(durationMs, durationMs)
        
        // Create a repeating task to update the countdown
        val countdownRunnable = object : Runnable {
            override fun run() {
                remainingSteps--
                val remainingMs = remainingSteps * updateIntervalMs
                
                // Update the display
                updateCountdownDisplay(remainingMs, durationMs)
                
                if (remainingSteps > 0) {
                    // Schedule the next update
                    handler.postDelayed(this, updateIntervalMs)
                } else {
                    // Countdown finished, move to next state
                    transitionToState(ExperimentState.SPEECH_RECORDING)
                }
            }
        }
        
        // Start the countdown
        handler.postDelayed(countdownRunnable, updateIntervalMs)
    }
    
    /**
     * Update the countdown display
     * @param remainingMs Remaining time in milliseconds
     * @param totalMs Total duration in milliseconds
     */
    private fun updateCountdownDisplay(remainingMs: Long, totalMs: Long) {
        // Format as seconds with one decimal place
        val remainingSeconds = remainingMs / 1000f
        countdownTextView.text = String.format("%.1f", remainingSeconds)
        
        // Optional: Animate the text size based on remaining time
        val progress = remainingMs.toFloat() / totalMs
        val startSize = 24f
        val endSize = 36f
        val currentSize = startSize + (endSize - startSize) * (1 - progress)
        countdownTextView.textSize = currentSize
    }
    
    /**
     * Start audio recording for the current trial
     */
    private fun startAudioRecording() {
        if (!permissionsGranted) {
            Toast.makeText(
                this,
                "Cannot record audio: permissions not granted",
                Toast.LENGTH_LONG
            ).show()
            
            // Skip recording and move to next state after delay
            handler.postDelayed({
                handleRecordingComplete()
            }, config?.speechDurationMs ?: 3000)
            return
        }
        
        // Update UI to show recording state
        experimentContentTextView.text = "Recording... Please describe what you saw"
        
        // Start recording
        audioRecorder.startRecording(
            participantId = participantId,
            blockNumber = currentBlock,
            trialNumber = currentTrial,
            durationMs = config?.speechDurationMs ?: 3000,
            onComplete = { file ->
                currentRecordingFile = file
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Recording saved: ${file.name}",
                        Toast.LENGTH_SHORT
                    ).show()
                    handleRecordingComplete()
                }
            },
            onError = { error ->
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Recording error: $error",
                        Toast.LENGTH_LONG
                    ).show()
                    handleRecordingComplete()
                }
            }
        )
    }
    
    /**
     * Handle completion of recording
     */
    private fun handleRecordingComplete() {
        if (currentTrial < trialsPerBlock) {
            startNextTrial()
        } else {
            transitionToState(ExperimentState.BLOCK_END)
        }
    }
}
