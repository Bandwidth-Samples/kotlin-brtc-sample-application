# Bandwidth BRTC Kotlin Sample App

A sample Android application demonstrating how to integrate Bandwidth Real-Time Communications (BRTC) in a Kotlin Android client. This app shows how to create and manage a BRTC endpoint, fetch tokens, and place/receive audio calls using Bandwidth's services.

## Overview

This Android sample contains a single app module that demonstrates:
- Creating and managing a Bandwidth RTC endpoint
- Fetching a token from a token server (see [app/src/main/kotlin/com/bandwidth/brtcsample/service/TokenService.kt](app/src/main/kotlin/com/bandwidth/brtcsample/service/TokenService.kt))
- Placing and receiving audio calls
- Basic UI for dialing, connecting, and disconnecting calls

## Features

- Make outbound calls to phone numbers
- Receive inbound calls routed to the app endpoint
- Simple call controls (call, hang up, disconnect/delete endpoint)
- Basic audio device handling

## Prerequisites

- Android Studio (Arctic Fox or newer) with Kotlin support
- Android SDK (API 21+ recommended)
- A Bandwidth account with an application and credentials able to mint BRTC tokens
- A token server or backend that provides a token at `/token` for the app to consume (see `TokenService`)

## Build & Run

Recommended: open the project in Android Studio and run on a device (real device recommended for audio).

From the command line you can build or install an APK:

```bash
./gradlew assembleDebug
./gradlew installDebug
```

Or run the app directly from Android Studio.

## Configuration

- The app's `TokenService` expects a server URL and calls `${serverURL.trimEnd('/')}/token` to fetch a token. Update the code or UI entry where you provide your token server URL.
- Ensure the token server returns a valid Bandwidth BRTC token and optional endpointId as JSON (the `TokenService` parses a `token` field and optional `endpointId`). See [app/src/main/kotlin/com/bandwidth/brtcsample/service/TokenService.kt](app/src/main/kotlin/com/bandwidth/brtcsample/service/TokenService.kt) for implementation.

## UI & Code Structure

- `app/src/main/kotlin/com/bandwidth/brtcsample/ui/screen/CallScreen.kt` — Call screen UI and controls
- `app/src/main/kotlin/com/bandwidth/brtcsample/ui/component/DialpadView.kt` — Dial pad and number input
- `app/src/main/kotlin/com/bandwidth/brtcsample/viewmodel/CallViewModel.kt` — Call state and business logic
- `app/src/main/kotlin/com/bandwidth/brtcsample/service/TokenService.kt` — Token fetcher using OkHttp

## Common Tasks

- Debugging network/SSL issues: if your token server uses a self-signed certificate, see `app/src/main/res/xml/network_security_config.xml` and [app/src/main/AndroidManifest.xml](app/src/main/AndroidManifest.xml) which include debug overrides to allow user-installed CAs while debugging.
- Running on device: grant microphone permission when prompted. If audio is missing, verify microphone permission and that the device's audio isn't muted.

## Troubleshooting

- "Failed to fetch auth token": verify your token server URL and that it returns a JSON object with a `token` field.
- SSL trust errors: ensure the server provides a full certificate chain or install the certificate on the device for debugging.

## Contributing

Feel free to open issues or PRs to improve the sample. Keep changes focused and include device/Android Studio details when reporting bugs.

## License

See the repository license file for details.
