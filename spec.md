App Specification for Brain Recording Experiment

1. Overview

The app is designed to run an experiment on an Android tablet. It involves a series of blocks and trials where participants view videos, followed by a speech phase where the app records audio. The app logs key events and triggers during the experiment and sends these events to a serial port. It also saves audio and event logs in a structured format and allows for manual export.

2. Requirements

2.1 Experiment Setup
	•	App Launch:
	•	Prompt the user to enter the participant number manually.
	•	Automatically retrieve the experiment date from the tablet’s wall time.
	•	Experiment Configuration:
	•	Handle the experiment configuration (e.g., number of blocks, trials per block, speech duration) using a fixed enum config class.
	•	Allow for incremental additions to configuration if needed (e.g., future parameters can be added easily).

2.2 Experiment Flow
	•	Instruction Pages:
	•	Before starting the experiment, show simple text-based instruction pages.
	•	Participants can navigate through instructions with a “Next” button.
	•	At the end of the instructions, provide a “Start Experiment” button to begin the experiment.
	•	Trials and Blocks:
	•	Each block contains multiple trials.
	•	In each trial, a video is played followed by a delay (fixation cross), then a speech phase where the app records audio.

2.3 Data Handling
	•	Event Logging:
	•	Log the following events in a JSON file:
	•	Absolute Time: Wall time at the moment of each event (e.g., video start, speech start).
	•	Relative App Time: Time elapsed since the start of the experiment, stored as a float.
	•	Video Name: The name of the video played during the trial.
	•	Audio File Name: The file name assigned to the audio recording.
	•	Audio Recording:
	•	Record audio during the speech phase for a fixed duration (configured in the enum).
	•	Audio files will be saved in a format: participant_{participant_id}_block_{block_number}_trial_{trial_number}.wav.
	•	Audio files will be stored in an app-specific directory on the device (getFilesDir()/audio/).
	•	Log File Saving:
	•	Log files will be saved incrementally at the end of each block in a directory (getFilesDir()/logs/).
	•	The log file will be named using the participant number and experiment date (e.g., participant_001_2025-04-03.json).
	•	Serial Port Triggers:
	•	Send event triggers via serial port for the following events (with fixed integer codes):
	•	Experiment start: 0
	•	Block start: 1
	•	Video start: 2
	•	Video end: 3
	•	Speech start: 4
	•	Speech end: 5
	•	Experiment end: 6

2.4 UI/UX
	•	Minimal and Clean Interface:
	•	Display a simple interface with essential elements such as the video player, speech icon, and timing information.
	•	During the speech phase, display an icon in the center of the screen to indicate speech.
	•	Connection Status:
	•	Show a minimal connection status at app launch (whether the serial port is connected or disconnected).
	•	Prevent Sleep & Notifications:
	•	Prevent the device from sleeping during the experiment.
	•	Suppress notifications during the experiment to avoid distractions.

2.5 Error Handling
	•	App Pause:
	•	If an error occurs (e.g., file failure, recording failure), pause the experiment and prompt the user to take action.
	•	Error Logging:
	•	Log all errors to a separate error log for debugging purposes.
	•	Provide a way for developers to view error logs after the experiment.

2.6 Power Management
	•	Battery Low Handling:
	•	If the battery is low, the app will not pause the experiment but should notify the user of low battery status.

3. Architecture Choices
	•	Event-Driven Architecture:
	•	The app will be driven by events, such as the start of a block, video playback, and speech recording.
	•	Events will trigger actions such as logging data, saving audio files, and sending serial port triggers.
	•	Config-Driven Approach:
	•	The experiment parameters (number of blocks, trials per block, etc.) will be set in a fixed configuration class that is easily extendable.
	•	Local Storage:
	•	All event logs and audio files will be stored locally in app-specific directories within the internal storage.

4. Data Handling Strategy
	•	Logging:
	•	Events will be logged immediately as they occur, including timing and associated data.
	•	Logs will be saved in JSON format to facilitate easy parsing and retrieval.
	•	Audio Files:
	•	Audio files will be stored in the app’s internal storage (getFilesDir()/audio/), named according to the participant and trial (e.g., participant_001_block_1_trial_1.wav).

5. Testing Plan

5.1 Unit Testing
	•	Test the serial port communication to ensure events are correctly triggered and sent.
	•	Test the audio recording functionality to ensure proper duration and naming conventions.
	•	Test the event logging to confirm the correct data is captured for each event (absolute time, relative time, video/audio names).

5.2 Integration Testing
	•	Verify the full experiment flow: From the instruction pages to the end of the experiment, ensuring all components work together seamlessly.
	•	Test the serial triggers to confirm they are sent immediately when events occur.

5.3 Usability Testing
	•	Test the app with participants to ensure the UI is intuitive and easy to follow.
	•	Test the manual export of logs after the experiment to ensure it works as expected.

5.4 Performance Testing
	•	Test the app’s performance on various devices to ensure smooth video playback, timely event logging, and audio recording without interruptions.

⸻