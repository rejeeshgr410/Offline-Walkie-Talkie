# TalkLoop — Offline Walkie-Talkie

TalkLoop is a privacy-first Android push-to-talk intercom designed for direct, nearby communication without relying on mobile data or the public internet.

It is being developed for field, travel, and local-team scenarios where an internet connection may be unavailable or undesirable. TalkLoop is **not** an emergency service and must not be relied on as the sole means of emergency communication.

## What it does

- Discovers nearby phones over Bluetooth Low Energy and establishes direct peer links.
- Uses a short authentication string (SAS) that both people confirm before a voice session is trusted.
- Sends encrypted push-to-talk voice once a secure peer session is established.
- Supports local device discovery, session status, contact-style peer views, channels, messages, voice history, radar/location views, and offline-map tooling.
- Provides audio-route controls and adaptive codec modes intended for changing RF conditions.
- Keeps the core nearby walkie-talkie flow independent of an internet connection.

## Security and privacy model

TalkLoop is built around an explicit, in-person pairing step: both people compare and accept the same short code before trusting a connection. This is intended to make an unexpected peer or a man-in-the-middle attempt visible to users.

As with any communication app, security and reliability depend on device permissions, operating-system behavior, radio conditions, and the current build. Treat the app as experimental until it has completed the validation appropriate for your environment.

## Technology

- Kotlin and Jetpack Compose
- Android Bluetooth / BLE GATT transport
- Android foreground services for active voice sessions
- Native Codec2 audio support with adaptive codec selection
- AndroidX Security / Google Tink-backed cryptographic building blocks
- Minimum Android API 29; target SDK 36

## Building locally

Use Android Studio with JDK 17 and the Android SDK/NDK versions declared by the project. From PowerShell in the repository root:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

For a release build, configure your own local signing properties in `keystore.properties`. That file and keystores are intentionally ignored by Git and must never be committed.

```powershell
.\gradlew.bat assembleRelease --no-configuration-cache
```

## Required permissions

The app requests only the permissions needed by enabled features, including microphone, nearby Bluetooth devices, notifications for background operation, and optional location access for map/radar functionality. Review Android's runtime permission prompts before enabling each feature.

## Project status

The project is under active development and field validation. Nearby discovery, secure pairing, reconnection, and voice delivery are being exercised across different Android devices with internet disabled. Range, latency, battery behavior, background survival, multi-peer behavior, and compatibility should be verified for the exact devices and Android versions you plan to use.

## Contributing

Please open an issue with reproducible device, Android-version, and connection-state details before proposing a fix. Do not include APK signing keys, passwords, device identifiers, or private logs in issues or pull requests.

## Maintainer

Maintained by [@rejeeshgr410](https://github.com/rejeeshgr410). Contributions are welcome through issues and pull requests.
