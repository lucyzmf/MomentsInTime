# Brain Recording Experiment Checklist

## Phase 1: Core Infrastructure
### Participant Input & Config
- [ ] Create `ParticipantInputActivity` layout with:
  - [ ] Number input field (numeric keyboard)
  - [ ] Input validation (≥1)
  - [ ] Date display (auto-generated)
  - [ ] Start button disabled until valid input
- [ ] Implement config class `ExperimentConfig` with:
  - [ ] Fixed block/trial parameters
  - [ ] Expandable enum structure
  - [ ] Video name list validation
- [ ] Unit tests:
  - [ ] Participant ID boundary cases (0, 1, maxInt)
  - [ ] Config parameter immutability
  - [ ] Date format validation

### Instruction Pages
- [ ] Build `InstructionActivity` with:
  - [ ] ViewPager2 for swipe navigation
  - [ ] Three instruction screens (placeholders)
  - [ ] Progress dots indicator
  - [ ] Next/Previous button logic
  - [ ] Start Experiment button (last page only)
- [ ] Implement smooth activity transitions
- [ ] Tests:
  - [ ] Page navigation sequence
  - [ ] Button state transitions
  - [ ] Landscape/portrait persistence

### Experiment State Machine
- [ ] Create `BaseExperimentActivity` with:
  - [ ] StateFlow state management
  - [ ] Elapsed time tracker
  - [ ] Block/trial counters
  - [ ] Wake lock implementation
- [ ] Implement state transitions:
  - [ ] BLOCK_START → TRIAL_VIDEO
  - [ ] TRIAL_VIDEO → FIXATION_DELAY
  - [ ] FIXATION_DELAY → SPEECH_RECORDING
  - [ ] SPEECH_RECORDING → TRIAL_VIDEO/BLOCK_END
- [ ] Tests:
  - [ ] State transition timing
  - [ ] Counter increment logic
  - [ ] Wake lock acquisition/release

## Phase 2: Core Experiment Components
### Video Playback
- [ ] Integrate ExoPlayer with:
  - [ ] SurfaceView for rendering
  - [ ] Video resource loading
  - [ ] Playback completion detection
- [ ] Implement:
  - [ ] Orientation locking during playback
  - [ ] Video name ↔ resource mapping
  - [ ] Error fallback (missing videos)
- [ ] Tests:
  - [ ] Video duration matching state
  - [ ] Memory leak analysis
  - [ ] Interrupted playback handling

### Fixation Cross
- [ ] Create fixation UI component with:
  - [ ] Animated countdown timer
  - [ ] Center-aligned cross symbol
  - [ ] 1000ms delay logic
- [ ] Implement:
  - [ ] Smooth transition animations
  - [ ] Time remaining display
- [ ] Tests:
  - [ ] Delay accuracy (±50ms)
  - [ ] UI visibility states
  - [ ] Orientation change handling

### Audio Recording
- [ ] Build `AudioRecorder` class with:
  - [ ] PCM configuration
  - [ ] File naming convention
  - [ ] Fixed-duration recording
- [ ] Implement:
  - [ ] Storage directory creation
  - [ ] Permission handling flow
  - [ ] Error recovery attempts
- [ ] Tests:
  - [ ] File duration validation
  - [ ] File naming pattern
  - [ ] Concurrent access safety

## Phase 3: Data System
### Event Logging
- [ ] Create `EventLogger` with:
  - [ ] JSON schema validation
  - [ ] Concurrent write queue
  - [ ] Incremental file saves
- [ ] Implement:
  - [ ] Event data classes
  - [ ] Gson serialization
  - [ ] Background thread writing
- [ ] Tests:
  - [ ] JSON schema compliance
  - [ ] File rotation logic
  - [ ] Crash recovery (partial writes)

### Serial Port Triggers
- [ ] Build `SerialPortHelper` with:
  - [ ] USB device detection
  - [ ] Trigger code mapping
  - [ ] Connection status monitoring
- [ ] Implement:
  - [ ] Async write operations
  - [ ] Fallback error logging
  - [ ] UI status indicator
- [ ] Tests:
  - [ ] Trigger code sequencing
  - [ ] Disconnected state handling
  - [ ] Baud rate validation

## Phase 4: Integration
### State Machine Wiring
- [ ] Connect all subsystems:
  - [ ] State transitions → EventLogger
  - [ ] Video events → Serial triggers
  - [ ] Audio recording → State machine
- [ ] Implement:
  - [ ] Battery level monitoring
  - [ ] Notification suppression
  - [ ] Fullscreen UI locking
- [ ] Tests:
  - [ ] End-to-end timing sync
  - [ ] Subsystem interaction
  - [ ] Resource cleanup

### Error Handling
- [ ] Create error system with:
  - [ ] Pause overlay UI
  - [ ] Error details logging
  - [ ] Resume/abort options
- [ ] Implement:
  - [ ] ErrorActivity for analysis
  - [ ] Log export functionality
  - [ ] Critical error thresholds
- [ ] Tests:
  - [ ] Simulated file system errors
  - [ ] Audio hardware failures
  - [ ] Memory pressure scenarios

## Final Checks
- [ ] Integration testing:
  - [ ] Full experiment flow (3 blocks × 5 trials)
  - [ ] Storage space monitoring
  - [ ] Serial trigger timing audit
- [ ] User testing:
  - [ ] First-time user walkthrough
  - [ ] Accessibility validation
  - [ ] Export functionality verification
- [ ] Performance:
  - [ ] Memory usage profile
  - [ ] Battery consumption test
  - [ ] Cold start timing
- [ ] Documentation:
  - [ ] Configuration guide
  - [ ] Error code reference
  - [ ] Data format specs

> **Pro Tip:** Implement checks in order, validate each item with:
> 1. Unit test passing
> 2. Manual verification
> 3. Peer code review
> 4. Performance benchmark