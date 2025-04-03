package dev.lucy.momentsintime

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@Config(sdk = [30])
class ExperimentStateTest {

    private lateinit var testExperimentActivity: TestExperimentActivity
    
    // Test implementation of BaseExperimentActivity for testing
    class TestExperimentActivity : BaseExperimentActivity() {
        var lastState: ExperimentState? = null
        val stateChangeLatch = CountDownLatch(1)
        
        override fun onStateChanged(state: ExperimentState) {
            super.onStateChanged(state)
            lastState = state
            stateChangeLatch.countDown()
        }
        
        fun resetLatch() {
            // Create a new latch for the next state change
            val newLatch = CountDownLatch(1)
            javaClass.getDeclaredField("stateChangeLatch").apply {
                isAccessible = true
                set(this@TestExperimentActivity, newLatch)
            }
        }
    }
    
    @Before
    fun setup() {
        testExperimentActivity = TestExperimentActivity()
    }
    
    @Test
    fun `test initial state is IDLE`() {
        testExperimentActivity.initializeExperiment(3, 5)
        assertEquals(ExperimentState.IDLE, testExperimentActivity.experimentState.value)
    }
    
    @Test
    fun `test block start transition`() {
        testExperimentActivity.initializeExperiment(3, 5)
        testExperimentActivity.startNextBlock()
        
        // Wait for state change
        assertTrue(testExperimentActivity.stateChangeLatch.await(1, TimeUnit.SECONDS))
        assertEquals(ExperimentState.BLOCK_START, testExperimentActivity.lastState)
        assertEquals(1, testExperimentActivity.currentBlock)
        assertEquals(0, testExperimentActivity.currentTrial)
    }
    
    @Test
    fun `test trial start transition`() {
        testExperimentActivity.initializeExperiment(3, 5)
        testExperimentActivity.startNextBlock()
        
        // Wait for state change
        assertTrue(testExperimentActivity.stateChangeLatch.await(1, TimeUnit.SECONDS))
        testExperimentActivity.resetLatch()
        
        testExperimentActivity.startNextTrial()
        
        // Wait for state change
        assertTrue(testExperimentActivity.stateChangeLatch.await(1, TimeUnit.SECONDS))
        assertEquals(ExperimentState.TRIAL_VIDEO, testExperimentActivity.lastState)
        assertEquals(1, testExperimentActivity.currentBlock)
        assertEquals(1, testExperimentActivity.currentTrial)
    }
    
    @Test
    fun `test block end when trials complete`() {
        testExperimentActivity.initializeExperiment(1, 2)
        testExperimentActivity.startNextBlock()
        
        // Wait for state change
        assertTrue(testExperimentActivity.stateChangeLatch.await(1, TimeUnit.SECONDS))
        testExperimentActivity.resetLatch()
        
        // First trial
        testExperimentActivity.startNextTrial()
        assertTrue(testExperimentActivity.stateChangeLatch.await(1, TimeUnit.SECONDS))
        testExperimentActivity.resetLatch()
        
        // Second trial
        testExperimentActivity.startNextTrial()
        assertTrue(testExperimentActivity.stateChangeLatch.await(1, TimeUnit.SECONDS))
        testExperimentActivity.resetLatch()
        
        // Third trial (should trigger block end)
        testExperimentActivity.startNextTrial()
        assertTrue(testExperimentActivity.stateChangeLatch.await(1, TimeUnit.SECONDS))
        
        assertEquals(ExperimentState.BLOCK_END, testExperimentActivity.lastState)
    }
    
    @Test
    fun `test experiment end when blocks complete`() {
        testExperimentActivity.initializeExperiment(1, 1)
        testExperimentActivity.startNextBlock()
        
        // Wait for state change
        assertTrue(testExperimentActivity.stateChangeLatch.await(1, TimeUnit.SECONDS))
        testExperimentActivity.resetLatch()
        
        // First trial
        testExperimentActivity.startNextTrial()
        assertTrue(testExperimentActivity.stateChangeLatch.await(1, TimeUnit.SECONDS))
        testExperimentActivity.resetLatch()
        
        // Second trial (should trigger block end)
        testExperimentActivity.startNextTrial()
        assertTrue(testExperimentActivity.stateChangeLatch.await(1, TimeUnit.SECONDS))
        testExperimentActivity.resetLatch()
        
        // Start next block (should trigger experiment end)
        testExperimentActivity.startNextBlock()
        assertTrue(testExperimentActivity.stateChangeLatch.await(1, TimeUnit.SECONDS))
        
        assertEquals(ExperimentState.EXPERIMENT_END, testExperimentActivity.lastState)
    }
    
    @Test
    fun `test elapsed time tracking`() {
        testExperimentActivity.initializeExperiment(3, 5)
        
        // Sleep to simulate time passing
        Thread.sleep(100)
        
        val elapsedTime = testExperimentActivity.getElapsedExperimentTime()
        assertTrue("Elapsed time should be at least 100ms", elapsedTime >= 100)
    }
}
