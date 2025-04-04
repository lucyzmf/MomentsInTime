package dev.lucy.momentsintime

import android.Manifest
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import android.util.Log
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import dev.lucy.momentsintime.logging.EventLogger
import dev.lucy.momentsintime.logging.EventType
import dev.lucy.momentsintime.serial.SerialPortHelper

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
    private lateinit var connectionStatusTextView: TextView
    private lateinit var batteryStatusTextView: TextView
    
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
    
    // Event logger and serial port helper
    private lateinit var eventLogger: EventLogger
    lateinit var serialPortHelper: SerialPortHelper
    
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
        
        // Hide the status bar and make the app full screen
        hideSystemUI()
        
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

        // Status text views
        connectionStatusTextView = findViewById(R.id.connectionStatusTextView)
        batteryWarningTextView = findViewById(R.id.batteryWarningTextView)
        
        // Hide battery warning by default, only show if battery is low at start
        batteryStatusTextView.visibility = View.GONE

        // Connect player to view
        playerView.player = player
        
        // Initialize experiment
        initializeExperiment(config?.blocks ?: 3, config?.trialsPerBlock ?: 5)
        startButton.visibility = View.GONE

        // Initialize event logger
        eventLogger = EventLogger.initialize(this, this.experimentStartTime)
        eventLogger.setExperimentInfo(participantId, dateString)
        
        // Initialize serial port helper
        serialPortHelper = SerialPortHelper(this)
        
        // Make sure connection status is visible
        connectionStatusTextView.visibility = View.VISIBLE
        
        // Observe connection state
        lifecycleScope.launch {
            serialPortHelper.connectionState.collect { state ->
                updateConnectionStatus(state)
            }
        }
        
        // Try to connect to a USB device
        serialPortHelper.connectToFirstAvailable()

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
        serialPortHelper.cleanup()
        super.onDestroy()
    }
    
    /**
     * Hides the system UI (status bar and navigation bar)
     */
    private fun hideSystemUI() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                // For API 30 and above
                WindowCompat.setDecorFitsSystemWindows(window, false)
                
                // Use post to ensure window is fully initialized
                window.decorView.post {
                    window.insetsController?.let {
                        it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                        it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    }
                }
            } else {
                // For API 29 and below
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                )
            }
            
            // Keep screen on during experiment
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            
            // Make sure connection status is still visible after hiding system UI
            handler.postDelayed({
                connectionStatusTextView.visibility = View.VISIBLE
                connectionStatusTextView.bringToFront()
            }, 100)
        } catch (e: Exception) {
            Log.e("ExperimentActivity", "Error hiding system UI: ${e.message}", e)
        }
    }
    
    /**
     * Check and request necessary permissions
     */
    private fun checkAndRequestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        
        // Check each permission individually and log the result
        permissions.forEach { permission ->
            val isGranted = ContextCompat.checkSelfPermission(this, permission) == 
                PackageManager.PERMISSION_GRANTED
            Log.d("PermissionCheck", "$permission granted: $isGranted")
        }
        
        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        
        if (permissionsToRequest.isNotEmpty()) {
            Log.d("PermissionCheck", "Requesting permissions: ${permissionsToRequest.joinToString()}")
            requestPermissionLauncher.launch(permissionsToRequest)
        } else {
            Log.d("PermissionCheck", "All permissions already granted")
            permissionsGranted = true
        }
    }
    
    override fun onStateChanged(state: ExperimentState) {
        super.onStateChanged(state)
        
        // Log state change with additional details
        eventLogger.logStateChange(state.name,)
        
        // Check if we need to show battery warning (only at experiment start)
        if (state == ExperimentState.IDLE && isBatteryLow) {
            showBatteryWarning()
        }

        // Send trigger for state change if connected
        if (serialPortHelper.connectionState.value == SerialPortHelper.ConnectionState.CONNECTED) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val eventType = when (state) {
                        ExperimentState.BLOCK_START -> EventType.BLOCK_START
                        ExperimentState.TRIAL_VIDEO -> EventType.TRIAL_START
                        ExperimentState.FIXATION_DELAY -> EventType.FIXATION_START
                        ExperimentState.SPEECH_RECORDING -> EventType.RECORDING_START
                        ExperimentState.BLOCK_END -> EventType.BLOCK_END
                        ExperimentState.EXPERIMENT_END -> EventType.EXPERIMENT_END
                        else -> null
                    }
                    
                    eventType?.let {
                        val success = serialPortHelper.sendEventTrigger(it)
                        if (!success) {
                            Log.w("ExperimentActivity", "Failed to send trigger for state: $state")
                            eventLogger.logError("Failed to send trigger for state: $state")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ExperimentActivity", "Error sending trigger: ${e.message}", e)
                    eventLogger.logError("Error sending trigger: ${e.message}")
                }
            }
        }
        
        // No battery status updates on state change
        
        when (state) {
            ExperimentState.BLOCK_START -> {
                // Log block start
                eventLogger.logBlockEvent(EventType.BLOCK_START, currentBlock)
                
                // Automatically transition to first trial after a short delay
                handler.postDelayed({
                    startNextTrial()
                }, 500)
            }
            
            ExperimentState.TRIAL_VIDEO -> {
                // Log trial start
                eventLogger.logTrialEvent(EventType.TRIAL_START, currentBlock, currentTrial)
                
                // Play video
                playCurrentTrialVideo()
            }
            
            ExperimentState.FIXATION_DELAY -> {
                // Log fixation start
                eventLogger.logEvent(EventType.FIXATION_START)

                // Show fixation cross and start countdown
                startFixationCountdown(config?.fixationDurationMs ?: 1000) // 1000ms delay
            }
            
            ExperimentState.SPEECH_RECORDING -> {
                // Start audio recording
                startAudioRecording()
            }
            
            ExperimentState.BLOCK_END -> {
                // Log block end
                eventLogger.logBlockEvent(EventType.BLOCK_END, currentBlock)
                
                // Show next button to proceed to next block
                nextButton.isEnabled = true
            }
            
            ExperimentState.EXPERIMENT_END -> {
                // Log experiment end
                eventLogger.logEvent(EventType.EXPERIMENT_END)
                
                // Save all events
                eventLogger.saveEvents()
                
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
                // Log experiment start
                eventLogger.logEvent(EventType.EXPERIMENT_START)
                
                // Send experiment start trigger
                serialPortHelper.sendEventTrigger(EventType.EXPERIMENT_START)
                
                // Ensure system UI is hidden when experiment starts
                hideSystemUI()
                startNextBlock()
            }
            
            ExperimentState.BLOCK_END -> {
                startNextBlock()
            }
            
            ExperimentState.EXPERIMENT_END -> {
                // Close the app completely when experiment is done
                finishAffinity()
            }
            
            else -> { /* No action needed */ }
        }
    }
    
    /**
     * Update the connection status display
     */
    private fun updateConnectionStatus(state: SerialPortHelper.ConnectionState) {
        runOnUiThread {
            Log.d("ExperimentActivity", "Updating connection status to: $state")
            
            val statusText = when (state) {
                SerialPortHelper.ConnectionState.CONNECTED -> "USB: Connected ✓"
                SerialPortHelper.ConnectionState.CONNECTING -> "USB: Connecting..."
                SerialPortHelper.ConnectionState.DISCONNECTED -> "USB: Disconnected"
                SerialPortHelper.ConnectionState.NO_DEVICES -> "USB: No devices found"
                SerialPortHelper.ConnectionState.PERMISSION_PENDING -> "USB: Permission requested"
                SerialPortHelper.ConnectionState.PERMISSION_DENIED -> "USB: Permission denied"
                SerialPortHelper.ConnectionState.DRIVER_NOT_FOUND -> "USB: No driver found"
                SerialPortHelper.ConnectionState.CONNECTION_FAILED -> "USB: Connection failed"
                SerialPortHelper.ConnectionState.ERROR -> "USB: Error"
            }
            
            val textColor = when (state) {
                SerialPortHelper.ConnectionState.CONNECTED -> getColor(android.R.color.holo_green_dark)
                SerialPortHelper.ConnectionState.CONNECTING, 
                SerialPortHelper.ConnectionState.PERMISSION_PENDING -> getColor(android.R.color.holo_blue_dark)
                else -> getColor(android.R.color.holo_red_dark)
            }
            
            connectionStatusTextView.apply {
                text = statusText
                setTextColor(textColor)
                visibility = View.VISIBLE
                
                // Ensure it's on top of other views
                bringToFront()
                
                // Add a brief animation to draw attention
                alpha = 0.7f
                animate().alpha(1.0f).setDuration(300).start()
            }
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
            ExperimentState.IDLE -> {
                playerView.visibility = View.GONE
                experimentContentTextView.visibility = View.VISIBLE
                fixationCrossLayout.visibility = View.GONE
                experimentContentTextView.text = "Ready to start experiment"
            }
            else -> {
                playerView.visibility = View.GONE
                experimentContentTextView.visibility = View.VISIBLE
                fixationCrossLayout.visibility = View.GONE
                
                // Update content text based on state
                experimentContentTextView.text = when (state) {
                    ExperimentState.BLOCK_START -> "Block $currentBlock Starting..."
                    ExperimentState.SPEECH_RECORDING -> "Please describe what you saw"
                    ExperimentState.BLOCK_END -> "Block $currentBlock Complete\n\nPress Next to continue"
                    ExperimentState.EXPERIMENT_END -> "Experiment Complete\n\nThank you for participating"
                    else -> "Experiment Content Area"
                }
            }
        }
        
        // Update button visibility and state
        when (state) {
            ExperimentState.IDLE -> {
                nextButton.visibility = View.VISIBLE
                nextButton.isEnabled = true
                nextButton.text = "Start"
            }
            
            ExperimentState.BLOCK_END -> {
                nextButton.visibility = View.VISIBLE
                nextButton.isEnabled = true
                nextButton.text = "Next Block"
            }
            
            ExperimentState.EXPERIMENT_END -> {
                nextButton.visibility = View.VISIBLE
                nextButton.isEnabled = true
                nextButton.text = "Finish"
            }
            
            else -> {
                // Hide the button during experiment trials
                nextButton.visibility = View.GONE
            }
        }
    }
    
    override fun onVideoError(errorMessage: String) {
        super.onVideoError(errorMessage)
        // Log error
        eventLogger.logError("Video error: $errorMessage")
        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
    }
    
    /**
     * Show error dialog with recovery options
     */
    override fun showErrorDialog(title: String, message: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            try {
                val dialog = AlertDialog.Builder(this@ExperimentActivity)
                    .setTitle(title)
                    .setMessage(message)
                    .setCancelable(false)
                    .setPositiveButton("Continue") { _, _ ->
                        // Reset error count and continue
                        errorCount = 0
                        recoveryAttempted = true
                        
                        // Log recovery attempt
                        eventLogger.logEvent(
                            EventType.SYSTEM_RECOVERY,
                        )
                        
                        // Continue with next trial
                        if (currentTrial < trialsPerBlock) {
                            startNextTrial()
                        } else {
                            transitionToState(ExperimentState.BLOCK_END)
                        }
                    }
                    .setNegativeButton("End Experiment") { _, _ ->
                        // Log experiment abort
                        eventLogger.logEvent(
                            EventType.EXPERIMENT_ABORTED,
                        )
                        
                        // Save logs before ending
                        eventLogger.saveEvents()
                        
                        // End experiment
                        transitionToState(ExperimentState.EXPERIMENT_END)
                    }
                    .create()
                
                dialog.show()
            } catch (e: Exception) {
                Log.e("ExperimentActivity", "Failed to show error dialog: ${e.message}", e)
                // Fallback to toast
                Toast.makeText(this@ExperimentActivity, "$title: $message", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun updateTimeDisplay() {
        val elapsedMs = getElapsedExperimentTime()
        val seconds = (elapsedMs / 1000) % 60
        val minutes = (elapsedMs / (1000 * 60)) % 60
        
        timeTextView.text = String.format("Time: %02d:%02d.%03d", 
            minutes, seconds, elapsedMs % 1000)
    }
    
    /**
     * Show battery warning if battery is low at experiment start
     */
    private fun showBatteryWarning() {
        if (isBatteryLow) {
            val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || 
                             status == BatteryManager.BATTERY_STATUS_FULL
            
            val chargingSymbol = if (isCharging) "⚡" else ""
            
            runOnUiThread {
                batteryStatusTextView.text = String.format("WARNING: Low Battery: %d%% %s", batteryLevel, chargingSymbol)
                batteryStatusTextView.setTextColor(getColor(android.R.color.holo_red_light))
                batteryStatusTextView.visibility = View.VISIBLE
                
                // Auto-hide after 10 seconds
                batteryStatusTextView.postDelayed({
                    batteryStatusTextView.visibility = View.GONE
                }, 10000)
            }
            
            // Log battery warning
            eventLogger.logEvent(EventType.BATTERY_WARNING)
        }
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
        Log.d("ExperimentActivity", "Starting audio recording, permissions granted: $permissionsGranted")
        
        // Log recording start
        eventLogger.logEvent(
            EventType.RECORDING_START
        )
        
        // Double-check permissions at runtime
        val micPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        
        if (!micPermission) {
            Log.e("ExperimentActivity", "Microphone permission is not granted at runtime check")
            Toast.makeText(
                this,
                "Cannot record audio: microphone permission not granted",
                Toast.LENGTH_LONG
            ).show()
            
            // Log permission error
            eventLogger.logError("Microphone permission denied during recording")
            
            // Request permission again if needed
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            
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
                Log.d("ExperimentActivity", "Recording completed successfully: ${file.absolutePath}")
                
                // Log recording end
                eventLogger.logRecordingEvent(
                    EventType.RECORDING_END, 
                    currentBlock, 
                    currentTrial, 
                    file.name
                )
                
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
                Log.e("ExperimentActivity", "Recording error: $error")
                
                // Log error
                eventLogger.logError("Recording error: $error")
                
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
