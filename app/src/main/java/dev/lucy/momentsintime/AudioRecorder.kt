package dev.lucy.momentsintime

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Handles audio recording functionality for the experiment.
 */
class AudioRecorder(private val context: Context) {
    
    companion object {
        private const val TAG = "AudioRecorder"
        
        // Audio configuration
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BITS_PER_SAMPLE = 16
        private const val BUFFER_SIZE_FACTOR = 2
    }
    
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var isRecording = false
    private var outputFile: File? = null
    private var bufferSize = AudioRecord.getMinBufferSize(
        SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT
    ) * BUFFER_SIZE_FACTOR
    
    private val handler = Handler(Looper.getMainLooper())
    
    /**
     * Start recording audio for a fixed duration
     * @param participantId The participant ID
     * @param blockNumber The current block number
     * @param trialNumber The current trial number
     * @param durationMs The recording duration in milliseconds
     * @param onComplete Callback when recording is complete
     * @param onError Callback when an error occurs
     */
    fun startRecording(
        participantId: Int,
        blockNumber: Int,
        trialNumber: Int,
        durationMs: Long,
        onComplete: (File) -> Unit,
        onError: (String) -> Unit
    ) {
        if (isRecording) {
            onError("Recording already in progress")
            return
        }
        
        try {
            // Create output directory if it doesn't exist
            val outputDir = File(context.getExternalFilesDir(null), "audio")
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }
            
            // Create output file with naming convention
            val fileName = "participant_${participantId}_block_${blockNumber}_trial_${trialNumber}.wav"
            outputFile = File(outputDir, fileName)
            
            // Initialize AudioRecord
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
            
            // Check if AudioRecord was initialized properly
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                onError("Failed to initialize AudioRecord")
                releaseResources()
                return
            }
            
            isRecording = true
            
            // Start recording in a separate thread
            recordingThread = Thread {
                writeAudioDataToFile(onError)
            }
            recordingThread?.start()
            
            // Start the actual recording
            audioRecord?.startRecording()
            
            // Schedule recording stop after the specified duration
            handler.postDelayed({
                stopRecording()
                outputFile?.let { file ->
                    if (file.exists() && file.length() > 0) {
                        onComplete(file)
                    } else {
                        onError("Recording file is empty or does not exist")
                    }
                } ?: onError("Output file is null")
            }, durationMs)
            
            Log.d(TAG, "Started recording to ${outputFile?.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording: ${e.message}", e)
            onError("Error starting recording: ${e.message}")
            releaseResources()
        }
    }
    
    /**
     * Stop the current recording
     */
    fun stopRecording() {
        if (!isRecording) {
            return
        }
        
        isRecording = false
        
        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord: ${e.message}", e)
        }
        
        releaseResources()
        Log.d(TAG, "Stopped recording")
    }
    
    /**
     * Write audio data to WAV file
     */
    private fun writeAudioDataToFile(onError: (String) -> Unit) {
        val data = ByteArray(bufferSize)
        var outputStream: FileOutputStream? = null
        
        try {
            outputStream = FileOutputStream(outputFile)
            
            // Write WAV header
            writeWavHeader(outputStream)
            
            // Write audio data
            while (isRecording) {
                val read = audioRecord?.read(data, 0, bufferSize) ?: -1
                if (read > 0) {
                    outputStream.write(data, 0, read)
                }
            }
            
            // Update WAV header with final file size
            updateWavHeader(outputFile)
            
        } catch (e: IOException) {
            Log.e(TAG, "Error writing audio data: ${e.message}", e)
            onError("Error writing audio data: ${e.message}")
        } finally {
            try {
                outputStream?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error closing output stream: ${e.message}", e)
            }
        }
    }
    
    /**
     * Write WAV header to the beginning of the file
     */
    private fun writeWavHeader(outputStream: FileOutputStream) {
        val header = ByteBuffer.allocate(44).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            
            // RIFF header
            put("RIFF".toByteArray())  // ChunkID
            putInt(0)  // ChunkSize (placeholder, will update later)
            put("WAVE".toByteArray())  // Format
            
            // fmt subchunk
            put("fmt ".toByteArray())  // Subchunk1ID
            putInt(16)  // Subchunk1Size (16 for PCM)
            putShort(1)  // AudioFormat (1 for PCM)
            putShort(1)  // NumChannels (1 for mono)
            putInt(SAMPLE_RATE)  // SampleRate
            putInt(SAMPLE_RATE * BITS_PER_SAMPLE / 8)  // ByteRate
            putShort((BITS_PER_SAMPLE / 8).toShort())  // BlockAlign
            putShort(BITS_PER_SAMPLE.toShort())  // BitsPerSample
            
            // data subchunk
            put("data".toByteArray())  // Subchunk2ID
            putInt(0)  // Subchunk2Size (placeholder, will update later)
        }.array()
        
        outputStream.write(header)
    }
    
    /**
     * Update the WAV header with the final file size
     */
    private fun updateWavHeader(file: File?) {
        if (file == null || !file.exists()) {
            return
        }
        
        try {
            val fileSize = file.length()
            val headerBuffer = ByteBuffer.allocate(8).apply {
                order(ByteOrder.LITTLE_ENDIAN)
                
                // Update ChunkSize (file size - 8)
                putInt((fileSize - 8).toInt())
                
                // Update Subchunk2Size (file size - 44)
                putInt((fileSize - 44).toInt())
            }
            
            val randomAccessFile = java.io.RandomAccessFile(file, "rw")
            // Update ChunkSize at position 4
            randomAccessFile.seek(4)
            randomAccessFile.write(headerBuffer.array(), 0, 4)
            
            // Update Subchunk2Size at position 40
            randomAccessFile.seek(40)
            randomAccessFile.write(headerBuffer.array(), 4, 4)
            
            randomAccessFile.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating WAV header: ${e.message}", e)
        }
    }
    
    /**
     * Release resources
     */
    private fun releaseResources() {
        try {
            audioRecord?.release()
            audioRecord = null
            recordingThread = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing resources: ${e.message}", e)
        }
    }
}
