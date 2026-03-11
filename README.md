# Bandwidth RTC Kotlin Sample Application

An Android sample app demonstrating the Bandwidth RTC SDK for WebRTC-based voice calling.


## Prerequisites

- Android Studio
- A running token server on port 3000 (see server setup below)
- An Android device or emulator

## Setup

### 1. Forward localhost to your device

If running on a **physical device**, use `adb reverse` so the app can reach your local server via `localhost`:

```bash
adb reverse tcp:3000 tcp:3000
```

If running on an **emulator**, this step is not needed — the emulator already routes `localhost` to your machine.

### 2. Run the app

Open the project in Android Studio and run the app. The default server URL is pre-filled as `http://localhost:3000`.

### 3. Connect

Tap **Connect** on the launch screen. The app will fetch a token from your local server and register with Bandwidth RTC.

## Configuration

The server URL can be changed from the connect screen. The default value is `http://localhost:3000`.

## Notes

- Microphone permission is required for calling.
- The app supports outbound calls, inbound calls, DTMF, call hold, and call history.
