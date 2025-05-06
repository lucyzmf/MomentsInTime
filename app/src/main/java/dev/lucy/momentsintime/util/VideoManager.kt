package dev.lucy.momentsintime.util

import android.content.Context
import android.util.Log
import java.util.Random

/**
 * Manages video resources for the experiment
 */
class VideoManager(private val context: Context) {
    
    companion object {
        private const val TAG = "VideoManager"
    }
    
    /**
     * Scans for all video files in the raw resources directory
     * @return List of video resource names
     */
    fun scanForVideos(): List<String> {
        val videoScanner = VideoResourceScanner(context)
        return videoScanner.scanForVideos()
    }
    
    /**
     * Prepares a shuffled queue of videos for the experiment
     * @param totalBlocks Number of blocks in the experiment
     * @param trialsPerBlock Number of trials per block
     * @return List of video resource names in shuffled order
     */
    fun prepareVideoQueue(totalBlocks: Int, trialsPerBlock: Int): List<String> {
        val allVideos = scanForVideos()
        Log.d(TAG, "Found ${allVideos.size} videos: ${allVideos.joinToString()}")
        
        val totalVideosNeeded = totalBlocks * trialsPerBlock
        
        if (allVideos.isEmpty()) {
            Log.e(TAG, "No videos found in resources")
            return emptyList()
        }
        
        if (allVideos.size < totalVideosNeeded) {
            Log.w(TAG, "Not enough videos (${allVideos.size}) for experiment " +
                    "configuration ($totalVideosNeeded needed). Will reuse videos.")
        }
        
        // Take the first N videos needed (or all if we don't have enough)
        val videosToUse = if (allVideos.size <= totalVideosNeeded) {
            allVideos
        } else {
            allVideos.take(totalVideosNeeded)
        }
        
        // Create the final queue with repetition if needed
        val videoQueue = mutableListOf<String>()
        
        while (videoQueue.size < totalVideosNeeded) {
            videoQueue.addAll(videosToUse)
        }
        
        // Trim to exact size needed
        val finalQueue = videoQueue.take(totalVideosNeeded)
        
        // Shuffle the queue
        return finalQueue.shuffled(Random(System.currentTimeMillis()))
    }
    
    /**
     * Prepares a shuffled queue of videos from a specific list
     * @param videoList List of video file names to use
     * @param totalBlocks Number of blocks in the experiment
     * @param trialsPerBlock Number of trials per block
     * @return List of video resource names in shuffled order
     */
    fun prepareVideoQueueFromList(videoList: List<String>, totalBlocks: Int, trialsPerBlock: Int): List<String> {
        Log.d(TAG, "Preparing video queue from list of ${videoList.size} videos: ${videoList.joinToString()}")
        
        val totalVideosNeeded = totalBlocks * trialsPerBlock
        
        if (videoList.isEmpty()) {
            Log.e(TAG, "No videos provided in list")
            return emptyList()
        }
        
        if (videoList.size < totalVideosNeeded) {
            Log.w(TAG, "Not enough videos (${videoList.size}) for experiment " +
                    "configuration ($totalVideosNeeded needed). Will reuse videos.")
        }
        
        // Take the first N videos needed (or all if we don't have enough)
        val videosToUse = if (videoList.size <= totalVideosNeeded) {
            videoList
        } else {
            videoList.take(totalVideosNeeded)
        }
        
        // Create the final queue with repetition if needed
        val videoQueue = mutableListOf<String>()
        
        while (videoQueue.size < totalVideosNeeded) {
            videoQueue.addAll(videosToUse)
        }
        
        // Trim to exact size needed
        val finalQueue = videoQueue.take(totalVideosNeeded)
        
        // Shuffle the queue
        return finalQueue.shuffled(Random(System.currentTimeMillis()))
    }
    
    /**
     * Gets the video for a specific block and trial
     * @param videoQueue The prepared video queue
     * @param block Current block number (1-based)
     * @param trial Current trial number (1-based)
     * @param trialsPerBlock Number of trials per block
     * @return The video resource name for this trial
     */
    fun getVideoForTrial(
        videoQueue: List<String>,
        block: Int,
        trial: Int,
        trialsPerBlock: Int
    ): String {
        val index = (block - 1) * trialsPerBlock + (trial - 1)
        
        if (index >= videoQueue.size) {
            Log.e(TAG, "Video index out of bounds: $index, queue size: ${videoQueue.size}")
            return videoQueue.firstOrNull() ?: ""
        }
        
        return videoQueue[index]
    }
}
