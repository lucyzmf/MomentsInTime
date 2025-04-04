
# Brain Recording Experiment App Blueprint

## Phase 1: Core Infrastructure

### Prompt 1: Create Participant Input and Config Foundation
Create a ParticipantInputActivity with:
1. EditText for numeric participant ID input
2. Input validation (must be ≥1)
3. Button to start experiment only when valid
4. Date retrieval using LocalDate.now()
5. Basic empty navigation to placeholder InstructionActivity

Create sealed class ExperimentConfig:
- Fixed parameters: blocks=3, trialsPerBlock=5, speechDurationMs=3000
- Enum-based structure for easy future expansion
- videoNames list with "video1" to "video15" as placeholders

Include unit tests for:
- Participant ID validation logic
- Config parameter verification

### Prompt 2: Instruction ViewPager and Navigation
Create InstructionActivity with:
1. ViewPager2 for swipeable instruction pages
2. 3 simple text pages (placeholder content)
3. Next/Previous button navigation
4. Start Experiment button on last page
5. Smooth transition to ExperimentActivity

Implement:
- ViewPagerAdapter with TextView fragments
- Page indicator dots
- Button state management (hide Next on last page)

Test coverage:
- Page navigation logic
- Button visibility states
- Swipe vs button interaction

### Prompt 3: Experiment State Machine Foundation
Create BaseExperimentActivity with:
1. StateFlow<ExperimentState> with states:
   (IDLE, BLOCK_START, TRIAL_VIDEO, FIXATION_DELAY, SPEECH_RECORDING, BLOCK_END, EXPERIMENT_END)
2. Time tracking for relative timestamps
3. Basic state transition scaffolding
4. Block/trial counters

Implement:
- State transition helper functions
- Elapsed time tracking using SystemClock.elapsedRealtime()
- Base UI with fullscreen mode and wake lock

Test:
- Initial state transitions
- Time tracking accuracy
- State sequence validation

## Phase 2: Core Experiment Components

### Prompt 4: Video Playback Implementation
Add to BaseExperimentActivity:
1. ExoPlayer instance with surface view
2. Video playback control tied to TRIAL_VIDEO state
3. Video duration tracking
4. Automatic transition to FIXATION_DELAY on completion

Implement:
- Video file loading from resources
- Error handling for missing videos
- Landscape orientation locking during playback

Test:
- Video start/end timing
- State transition after playback
- Memory leaks during video playback

### Prompt 5: Fixation Cross and Delay Handling
Add fixation cross UI component:
1. Centered '+' symbol with large text
2. Countdown timer display
3. 1000ms delay period
4. Transition to SPEECH_RECORDING after delay

Implement:
- Animated countdown text
- Orientation-aware layout
- Interim time tracking updates

Test:
- Delay duration accuracy
- UI visibility during state
- Orientation change handling

### Prompt 6: Speech Recording Component
Create AudioRecorder class with:
1. AudioRecord configuration (16-bit PCM, 44100Hz)
2. File saving to app-specific audio directory
3. Naming convention: participant_{id}_block_{b}_trial_{t}.wav
4. Fixed duration recording tied to config

Implement:
- Recording start/stop in SPEECH_RECORDING state
- Storage permission handling
- Error handling for recording failures

Test:
- File creation and duration validation
- Permission denial handling
- File naming convention compliance

## Phase 3: Data Logging System

### Prompt 7: Event Logging Implementation
Create EventLogger singleton with:
1. JSON structure following spec
2. Concurrent write capability
3. Incremental file saving after each block
4. Directory structure: logs/ and audio/

Implement:
- Event data class with absoluteTime, relativeTime, type, videoName, audioFileName
- Gson serialization
- Background thread writing

Test:
- JSON schema validation
- File rotation and incremental saves
- Concurrent access safety

### Prompt 8: Serial Port Trigger Integration
Create SerialPortHelper with:
1. USB device detection
2. Connection status monitoring
3. Trigger code sending (0-6 from spec)
4. Fallback logging when disconnected

Implement:
- Android USB host API integration
- Async write operations
- Connection status indicator in UI

Test:
- Trigger code sequencing
- Disconnected state handling
- USB permission flows

## Phase 4: Integration and Error Handling

### Prompt 9: State Machine Event Integration
Connect all components in BaseExperimentActivity:
1. Hook StateFlow transitions to EventLogger
2. Connect serial triggers to state changes
3. Wire AudioRecorder to SPEECH_RECORDING state
4. Link video playback to trial counter

Implement:
- Complete event lifecycle tracking
- Error recovery mechanisms
- Battery level monitoring

Test:
- End-to-end event sequencing
- File/trigger/video alignment
- Low battery simulation

```markdown
### Prompt 10: Error Handling and Pause UI
```kotlin
Implement error recovery system:
1. Error state with pause overlay
2. Error logging subsystem
3. Resume/Abort options
4. Error details view for developers

Create:
- ErrorActivity for post-experiment analysis
- Export functionality for logs
- Notification suppression

Test:
- File write failure scenarios
- Audio hardware failures
- Unexpected state transitions
```

# Iteration Plan
1. Implement Phase 1 prompts sequentially with tests
2. Review component isolation and integration points
3. Proceed to Phase 2 only after all Phase 1 tests pass
4. Use feature flags for experimental components
5. Final integration in Phase 4 must maintain <100ms state transitions

Each prompt builds on previous components while maintaining test coverage. All wiring happens in the final integration phase to prevent orphaned code. Error handling is implemented last to build on stable components.
```