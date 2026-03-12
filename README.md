# Bandwidth WebRTC Kotlin Android Dialer Sample

A native Android calling app powered by [Bandwidth's WebRTC (BRTC) SDK](https://dev.bandwidth.com/docs/brtc), demonstrating outbound PSTN dialing and live call quality monitoring.

## Overview

This sample application is a single Android app module that demonstrates:

- **Bandwidth RTC Integration**: Uses the Bandwidth BRTC SDK to establish RTC connections and manage endpoints
- **Token Fetching**: Retrieves a session token from a local token server to authenticate with Bandwidth
- **Call Controls**: Dial phone numbers, accept/decline incoming calls, mute, speaker, DTMF, and call hold
- **Call History**: View recent calls and redial from the recents tab

## Features

### Currently Supported
- ✅ Make outbound calls from the Android app to phone numbers
- ✅ Receive inbound calls routed to the app endpoint
- ✅ Real-time audio via Bandwidth RTC
- ✅ DTMF tones during a call
- ✅ Mute and speakerphone controls
- ✅ Call history / recents tab

## Prerequisites

- Android Studio (Hedgehog or newer) with Kotlin support
- Android SDK (API 26+)
- A Bandwidth account with:
  - Account ID
  - API credentials able to mint BRTC tokens
  - A configured Voice Application
  - Access to the BRTC role on your credentials
- ngrok or similar tunneling service if your token server is running locally

## Getting the App

### Option A — Clone and build

```bash
git clone <repository-url>
cd brtc-kotlin-sample-application
```

Open the project in Android Studio, let Gradle sync, then run the `app` target on your device or emulator.

### Option B — Download a release APK

1. Go to the **Releases** page of this repository.
2. Download the latest `.apk` file.
3. Install it on your device:

```bash
adb install brtc-sample-<version>.apk
```

> Enable **Install from unknown sources** on the device if prompted.

## Development Setup

### 1. Start your token server

The app expects a token server running on port 3000 that returns a JSON object with a `token` field at `GET /token`. Start your backend server and note its address.

### 2. Expose the server to your device

**Physical device** — use `adb reverse` so the app can reach your local server via `localhost`:

```bash
adb reverse tcp:3000 tcp:3000
```

**Emulator** — this step is not needed. The emulator already routes `localhost` to your machine.

**Remote server** — if your token server is not running locally, start ngrok to expose it:

```bash
ngrok http 3000
```

Then use the ngrok URL as the server URL in the app's connect screen.

### 3. Open and run in Android Studio

1. Open the project in Android Studio.
2. Connect your device or start an emulator.
3. Run the `app` configuration.
4. Grant microphone permission when prompted.

### 4. Connect

On the launch screen, verify the server URL (default: `http://localhost:3000`) and tap **Connect**. The app will fetch a token and register the endpoint with Bandwidth RTC.

## How to Use

1. **Connect**: Enter your token server URL and tap **Connect** to register a Bandwidth RTC endpoint.
2. **Dial**: Use the keypad to enter a phone number and tap the green call button.
3. **Receive a call**: When someone calls your Bandwidth number, the app will ring and prompt you to accept or decline.
4. **In-call controls**: Use Mute, Speaker, and Keypad (DTMF) during a call. Tap End to hang up.
5. **Recents**: Switch to the Recents tab to view call history and tap any entry to redial.
6. **Disconnect**: Tap Disconnect on the connect screen to unregister the endpoint.

## Code Structure

```
app/src/main/kotlin/com/bandwidth/brtcsample/
├── ui/
│   ├── screen/
│   │   ├── CallScreen.kt        # Dialing, ringing, and in-call UI
│   │   └── ConnectScreen.kt     # Token server URL input and connect flow
│   └── component/
│       ├── DialpadView.kt       # Dialpad grid component
│       ├── AudioWaveformView.kt # Real-time audio level visualizer
│       ├── RecentsScreen.kt     # Call history list
│       └── StatsOverlay.kt      # In-call RTC stats overlay
├── viewmodel/
│   └── CallViewModel.kt         # Call state, SDK integration, business logic
└── service/
    └── TokenService.kt          # Fetches a BRTC token from the token server
```

## Troubleshooting

**Issue**: "Failed to fetch auth token"
- **Solution**: Verify the token server URL in the connect screen and confirm it returns `{ "token": "..." }` at `GET /token`.

**Issue**: Calls not connecting
- **Solution**: Ensure `adb reverse tcp:3000 tcp:3000` is running for physical devices, or that the token server URL is reachable from the device.

**Issue**: No audio in calls
- **Solution**: Grant microphone permission when prompted. If audio is still missing, verify the device is not muted and check that the speakerphone setting is as expected.

**Issue**: SSL / certificate errors
- **Solution**: If your token server uses a self-signed certificate, see `app/src/main/res/xml/network_security_config.xml` and `AndroidManifest.xml`, which include debug overrides to allow user-installed CAs.

## Learn More

- [Bandwidth RTC Endpoint API reference](https://dev.bandwidth.com/apis/brtc-apis/)
- [Bandwidth RTC Overview](https://dev.bandwidth.com/docs/brtc/)
- [Bandwidth Programmable Voice APIs](https://dev.bandwidth.com/docs/voice/programmable-voice)

## Questions and Feedback

Reach out to `support@bandwidth.com` for any questions or feedback regarding this product.

## Contributing

Feel free to open issues or PRs to improve the sample. Keep changes focused and include device model, Android version, and Android Studio version when reporting bugs.

## License

See the repository license file for details.
