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

## Development Setup

### 1. Clone the Repository

```bash
git clone <repository-url>
cd javascript-brtc-sdk-sample-app
```

### 2. Install Dependencies

```bash
npm install
```

### 3. Configure Environment Variables

Copy the example environment file and fill in your Bandwidth credentials:

```bash
cp .env.example .env.local
```

Edit `.env.local` with your values:

```env
# Bandwidth API Configuration
HTTP_BASE_URL=https://api.bandwidth.com/v2

# Bandwidth Account Credentials (use either username/password OR client credentials)
ACCOUNT_ID=your_account_id
BW_USERNAME=your_username
BW_PASSWORD=your_password
# OR
# BW_ID_CLIENT_ID=your_client_id
# BW_ID_CLIENT_SECRET=your_client_secret

# Bandwidth Application Settings
APPLICATION_ID=your_application_id
FROM_NUMBER=+1XXXXXXXXXX

# Public Callback URL (ngrok or similar)
CALLBACK_BASE_URL=https://your-callback-url.ngrok-free.app
```

### 4. Set Up Callback URL

Start ngrok to expose your local server:

```bash
ngrok http 3000
```

Copy the ngrok URL and update `CALLBACK_BASE_URL` in `.env.local`.

Update your Bandwidth Voice Application settings:
- **Callback URL**: `https://your-ngrok-url.ngrok-free.app/api/callbacks/calls/initiate`
- **Call-initiated callback method**: `POST`
- **Status URL**: `https://your-ngrok-url.ngrok-free.app/api/callbacks/calls/status`
- **Status callback method**: `POST`

### 5. Start Backend Server

```bash
npm start
```
This starts the Express server on port 3000.

### 6. Android Studio Setup

First, connect your phone to the computer and boot up the Application in Android Studio.

Then, open a tunnel from your laptop the phone on port 3000

```
adb reverse tcp:3000 tcp:3000
```

And now, run the application

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
