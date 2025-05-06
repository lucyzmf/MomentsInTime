package dev.lucy.momentsintime

import java.time.LocalDate

/**
 * Sealed class representing experiment configuration parameters.
 * This structure allows for easy future expansion of experiment types.
 */
sealed class ExperimentConfig {
    
    /**
     * Standard experiment configuration with fixed parameters.
     */
    data class Standard(
        val participantId: Int,
        val date: LocalDate,
        val blocks: Int = 2,
        val trialsPerBlock: Int = 3,
        val speechDurationMs: Long = 3000,
        val videoNames: List<String> = List(15) { "video${it + 1}" },
        val fixationDurationMs: Long = 2000,
    ) : ExperimentConfig()
    
    companion object {
        /**
         * Validates if a participant ID is valid (must be ≥ 1)
         * @param id The participant ID to validate
         * @return true if valid, false otherwise
         */
        fun isValidParticipantId(id: Int): Boolean {
            return id >= 1
        }
    }
}
