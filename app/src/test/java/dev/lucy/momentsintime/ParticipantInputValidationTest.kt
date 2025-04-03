package dev.lucy.momentsintime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParticipantInputValidationTest {
    
    @Test
    fun `participant ID validation - valid IDs`() {
        // Valid IDs (≥ 1)
        assertTrue(ExperimentConfig.isValidParticipantId(1))
        assertTrue(ExperimentConfig.isValidParticipantId(5))
        assertTrue(ExperimentConfig.isValidParticipantId(100))
        assertTrue(ExperimentConfig.isValidParticipantId(Integer.MAX_VALUE))
    }
    
    @Test
    fun `participant ID validation - invalid IDs`() {
        // Invalid IDs (< 1)
        assertFalse(ExperimentConfig.isValidParticipantId(0))
        assertFalse(ExperimentConfig.isValidParticipantId(-1))
        assertFalse(ExperimentConfig.isValidParticipantId(Integer.MIN_VALUE))
    }
    
    @Test
    fun `experiment config parameters - verify defaults`() {
        val config = ExperimentConfig.Standard(
            participantId = 1,
            date = java.time.LocalDate.now()
        )
        
        // Verify default parameters
        assert(config.blocks == 3)
        assert(config.trialsPerBlock == 5)
        assert(config.speechDurationMs == 3000L)
        assert(config.videoNames.size == 15)
        assert(config.videoNames.first() == "video1")
        assert(config.videoNames.last() == "video15")
    }
}
