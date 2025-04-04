package dev.lucy.momentsintime.logging

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Event data class for logging experiment events
 */
data class ExperimentEvent(
    val absoluteTime: Long = System.currentTimeMillis(),
    val relativeTime: Long,
    val type: EventType,
    val blockNumber: Int? = null,
    val trialNumber: Int? = null,
    val videoName: String? = null,
    val audioFileName: String? = null,
    val state: String? = null,
    val details: Map<String, Any>? = null
)

/**
 * Enum defining the types of events that can be logged
 */
enum class EventType {
    EXPERIMENT_START,
    EXPERIMENT_END,
    BLOCK_START,
    BLOCK_END,
    TRIAL_START,
    TRIAL_END,
    VIDEO_START,
    VIDEO_END,
    FIXATION_START,
    FIXATION_END,
    RECORDING_START,
    RECORDING_END,
    STATE_CHANGE,
    ERROR
}

/**
 * Singleton for logging experiment events to JSON files
 */
class EventLogger private constructor(
    private val context: Context,
    private val experimentStartTime: Long
) {
    private val events = CopyOnWriteArrayList<ExperimentEvent>()
    private val mutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO)
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    private var participantId: Int = -1
    private var sessionDate: String = ""

    companion object {
        private const val TAG = "EventLogger"
        private var instance: EventLogger? = null

        fun initialize(context: Context, experimentStartTime: Long): EventLogger {
            return instance ?: synchronized(this) {
                instance ?: EventLogger(
                    context.applicationContext,
                    experimentStartTime
                ).also { instance = it }
            }
        }

        fun getInstance(): EventLogger {
            return instance ?: throw IllegalStateException("EventLogger not initialized")
        }
    }

    /**
     * Set experiment metadata
     */
    fun setExperimentInfo(participantId: Int, date: String) {
        this.participantId = participantId
        this.sessionDate = date
        Log.d(TAG, "Experiment start time set: $experimentStartTime")
    }

    /**
     * Log an experiment event
     */
    fun logEvent(event: ExperimentEvent) {
        scope.launch {
            events.add(event)
            Log.d(TAG, "Logged event: ${event.type}")

            // Save after certain important events
            if (event.type in listOf(
                    EventType.EXPERIMENT_START,
                    EventType.BLOCK_START,
                    EventType.FIXATION_START,
                    EventType.TRIAL_START,
                    EventType.FIXATION_END,
                    EventType.TRIAL_END, 
                    EventType.BLOCK_END,
                    EventType.ERROR
                )
            ) {
                saveEvents(is_intermediate = true)
            }
        }
    }

    /**
     * Log a simple event with just a type
     */
    fun logEvent(type: EventType) {
        scope.launch {
            logEvent(
                ExperimentEvent(
                    type = type,
                    relativeTime = SystemClock.elapsedRealtime() - experimentStartTime
                )
            )
        }
    }

    /**
     * Log a state change event
     */
    fun logStateChange(state: String) {
        scope.launch {
            events.add(
                ExperimentEvent(
                    type = EventType.STATE_CHANGE,
                    state = state,
                    relativeTime = SystemClock.elapsedRealtime() - experimentStartTime
                )
            )
            Log.d(TAG, "Logged state change: $state")
        }
    }

    /**
     * Log a block event
     */
    fun logBlockEvent(type: EventType, blockNumber: Int) {
        scope.launch {
            events.add(
                ExperimentEvent(
                    type = type,
                    blockNumber = blockNumber,
                    relativeTime = SystemClock.elapsedRealtime() - experimentStartTime
                )
            )
            Log.d(TAG, "Logged block event: $type, block: $blockNumber")
        }
    }

    /**
     * Log a trial event
     */
    fun logTrialEvent(type: EventType, blockNumber: Int, trialNumber: Int) {
        scope.launch {
            events.add(
                ExperimentEvent(
                    type = type,
                    blockNumber = blockNumber,
                    trialNumber = trialNumber,
                    relativeTime = SystemClock.elapsedRealtime() - experimentStartTime
                )
            )
            Log.d(TAG, "Logged trial event: $type, block: $blockNumber, trial: $trialNumber")
        }
    }

    /**
     * Log a video event
     */
    fun logVideoEvent(type: EventType, blockNumber: Int, trialNumber: Int, videoName: String) {
        scope.launch {
            events.add(
                ExperimentEvent(
                    type = type,
                    blockNumber = blockNumber,
                    trialNumber = trialNumber,
                    videoName = videoName,
                    relativeTime = SystemClock.elapsedRealtime() - experimentStartTime
                )
            )
            Log.d(TAG, "Logged video event: $type, block: $blockNumber, trial: $trialNumber, video: $videoName")
        }
    }

    /**
     * Log a recording event
     */
    fun logRecordingEvent(
        type: EventType,
        blockNumber: Int,
        trialNumber: Int,
        audioFileName: String
    ) {
        scope.launch {
            events.add(
                ExperimentEvent(
                    type = type,
                    blockNumber = blockNumber,
                    trialNumber = trialNumber,
                    audioFileName = audioFileName,
                    relativeTime = SystemClock.elapsedRealtime() - experimentStartTime
                )
            )
            Log.d(TAG, "Logged recording event: $type, block: $blockNumber, trial: $trialNumber, file: $audioFileName")
        }
    }

    /**
     * Log an error event
     */
    fun logError(message: String, details: Map<String, Any>? = null) {
        scope.launch {
            events.add(
                ExperimentEvent(
                    type = EventType.ERROR,
                    relativeTime = SystemClock.elapsedRealtime() - experimentStartTime,
                    details = details?.plus("message" to message) ?: mapOf("message" to message)
                )
            )
            Log.e(TAG, "Logged error: $message")
            saveEvents(is_intermediate = true) // Always save immediately on errors
        }
    }

    /**
     * Save events to a JSON file
     */
    fun saveEvents(is_intermediate: Boolean = false) {
        if (events.isEmpty()) {
            Log.d(TAG, "No events to save")
            return
        }

        scope.launch {
            mutex.withLock {
                try {
                    val logsDir = ensureLogsDirectory()
                    val timestamp = SimpleDateFormat("HHmmss", Locale.US).format(Date())
                    val prefix = if (is_intermediate) "intermediate_" else ""
                    val fileName = "${prefix}p${participantId}_${sessionDate}_${timestamp}.json"
                    val logFile = File(logsDir, fileName)

                    // Create a copy of events to avoid concurrent modification
                    val eventsCopy = ArrayList(events)

                    FileWriter(logFile).use { writer ->
                        val json = gson.toJson(eventsCopy)
                        writer.write(json)
                        writer.flush()
                    }

                    Log.d(TAG, "Saved ${eventsCopy.size} events to ${logFile.absolutePath}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving events: ${e.message}", e)
                }
            }
        }
    }

    /**
     * Ensure the logs directory exists
     */
    private fun ensureLogsDirectory(): File {
        val baseDir = File(context.getExternalFilesDir(null), "moments_in_time")
        val logsDir = File(baseDir, "logs")

        if (!logsDir.exists()) {
            if (logsDir.mkdirs()) {
                Log.d(TAG, "Created logs directory: ${logsDir.absolutePath}")
            } else {
                Log.e(TAG, "Failed to create logs directory: ${logsDir.absolutePath}")
            }
        }

        return logsDir
    }

    /**
     * Get the audio directory
     */
    fun getAudioDirectory(): File {
        val baseDir = File(context.getExternalFilesDir(null), "moments_in_time")
        val audioDir = File(baseDir, "audio")

        if (!audioDir.exists()) {
            if (audioDir.mkdirs()) {
                Log.d(TAG, "Created audio directory: ${audioDir.absolutePath}")
            } else {
                Log.e(TAG, "Failed to create audio directory: ${audioDir.absolutePath}")
            }
        }

        return audioDir
    }

    /**
     * Clear all events (typically after saving)
     */
    fun clearEvents() {
        events.clear()
        Log.d(TAG, "Cleared all events")
    }
}
