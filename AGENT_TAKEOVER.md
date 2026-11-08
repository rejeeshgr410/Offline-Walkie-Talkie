# AGENT_TAKEOVER.md — TalkLoop / Offline Walkie Talkie Project Handover

> **Welcome Agent!** This document provides everything you need to understand the project architecture, set up the environment, run tests, build debug/release APKs, and seamlessly continue development.

---

## 1. Project Overview & Architecture

- **Project Name**: TalkLoop (Offline Walkie Talkie)
- **Application ID / Package**: `com.example.offlinewalkietalkie`
- **Language & UI**: Kotlin 2.0+, Jetpack Compose with Material 3 + Custom Liquid Glass Theme
- **Target OS**: Android 10 (API 29) to Android 16 (API 36)
- **Primary Functionality**: Fully offline, peer-to-peer / mesh walkie-talkie communication using Google Nearby Connections API, Wi-Fi Direct, and BLE.

### Core Modules & Responsibilities
- **Audio & Voice Pipeline (`voice/`)**:
  - `AudioCapture.kt`: High-performance 48 kHz mono PCM voice capture with hardware acoustic echo cancellation (`AcousticEchoCanceler`), noise suppressor, and AGC.
  - `AudioPlayer.kt`: Low-latency PCM streaming playback via `AudioTrack`.
  - `OpusCodec.kt` / `PcmCodec.kt`: Voice compression (Opus 20 ms frames) with Packet Loss Concealment (PLC).
  - `JitterBuffer.kt`: Adaptive jitter buffer (40–60 ms target delay), RFC 3550 jitter telemetry, and 200 ms stale voice drop.
  - `AudioRouteManager.kt`: Audio routing between speakerphone and earpiece (`setCommunicationDevice` on API 31+) and transient audio focus management.
- **Cryptography & Security (`crypto/`)**:
  - `TinkCryptoEngine.kt`: Google Tink 1.16.0 engine for X25519 key exchange, HKDF-SHA256, and ChaCha20-Poly1305 AEAD.
  - `TwoLayerCodec.kt`: Two-layer packet envelope (§19.1) enabling multi-hop relay nodes to route hop-by-hop outer packets without exposing end-to-end inner encrypted audio/data.
  - `CryptoManager.kt`: Channel & link key lifecycle, ephemeral identity keys, and 6-digit Short Authentication String (SAS) derivation.
- **Network & Mesh Transport (`network/` & `mesh/`)**:
  - `NearbyConnectionsTransport.kt`: Google Nearby Connections transport engine (`P2P_STAR` / `P2P_CLUSTER` / `P2P_POINT_TO_POINT`).
  - `MeshRelayRouter.kt` & `MeshRouteTable.kt`: Multi-hop forwarding tree, loop detection via `SeenPacketCache.kt`, and hop count enforcement (up to 5 hops).
  - `ChannelHostController.kt`: Channel host coordination, floor control state machine (`FloorController.kt`), member validation, and epoch rotation.
  - `QrJoinManager.kt`: Auto-rotating QR code generation (ZXing) with one-time secrets and 10-minute validity for verified joining.
- **Background & System Services (`service/`)**:
  - `WalkieTalkieForegroundService.kt`: Persistent Foreground Service keeping connections alive with CPU WakeLocks when the screen is turned off.
  - `NotificationController.kt`: Rich notifications with quick PTT actions.
- **User Interface (`ui/`)**:
  - `MainScreen.kt`, `PttOrb.kt`, `GlassSurface.kt`, `WalkieTalkieViewModel.kt`: Liquid glass visual styling, 176dp illuminated PTT Orb, audio visualization waveforms, RTL support (Arabic), and contact verification views.
  - `RadarScreen.kt`: Offline relative directional radar scope with dynamic distance range rings, cardinal north pointer, peer blips, target tracking, location freshness timestamps, and external navigation handoff.
  - `MapScreens.kt`:
    - `DiscoverMapScreen`: Schematic coordinate grid plotting real-time and historical peer locations with freshness coloring (Cyan for fresh, Amber for stale, White for local device).
    - `OfflineMapsScreen`: User-initiated downloader for bounded OpenStreetMap Overpass road geometry extracts (0.2–2.0 km radius) with progress tracking, pause/resume, and local vector road rendering.
  - `VoiceHistoryScreen.kt`: Chronological offline voice transmission timeline with duration formatting, sender badges, and 1-tap PCM replay.
  - `SosDialog.kt`: Emergency distress broadcast modal and high-priority distress alert banner with direct SOS radar tracking.
- **Offline Maps & Data (`data/`)**:
  - `OfflineMapRepository.kt`: Manages download and local JSON persistence of bounded OSM road networks from Overpass API into `filesDir/offline-map-areas/`.
- **Offline SOS Distress Subsystem (`sos/`)**:
  - `SosManager.kt`: Offline SOS beacon broadcast loop, multi-hop mesh forwarding with TTL decrement, deduplication, and siren control.
  - `EmergencyAlarmPlayer.kt`: Hardware-independent dual-frequency PCM siren generator (850 Hz / 1100 Hz alternating pattern) on `AudioTrack` max volume stream.
- **Radar & Compass Subsystem (`radar/`)**:
  - `RadarMath.kt`: Haversine great-circle distance, forward azimuth bearing calculation, relative bearing against heading, and low-pass angular filter.
  - `CompassManager.kt`: Sensor fusion (`TYPE_ROTATION_VECTOR` with fallback to Accelerometer + Magnetometer) with azimuth stabilization.
  - `LocationProvider.kt`: Offline GPS/network location listener acquiring high-accuracy offline fix without external map services.
- **Offline Voice History & Replay (`history/`)**:
  - `VoiceHistoryManager.kt`: Automatic rolling 50-message persistence of inbound and outbound raw PCM transmissions with anti-pop `VolumeShaper` playback.
- **Peer Telemetry (`telemetry/`)**:
  - `TelemetryManager.kt`: Broadcasts local battery percentage and BLE RSSI levels to peers; displays battery health badges on contact avatars and radar nodes.
- **System Overlay & Hardware PTT (`service/` & `voice/`)**:
  - `FloatingPttService.kt`: Draggable `SYSTEM_ALERT_WINDOW` floating PTT orb over other apps with real-time transmit/receive visual glow and tap-to-talk.
  - `HardwarePttManager.kt`: Interception of Volume Down and Headset Hook hardware keys for screen-off PTT with haptic vibration feedback and Bluetooth SCO management.

---

## 2. Prerequisites & Environment Setup

- **JDK**: Java 17 (recommended: OpenJDK 17 or Android Studio JBR 17)
- **Android SDK**:
  - `compileSdk`: 36
  - `minSdk`: 29
  - `targetSdk`: 36
- **Gradle**: Gradle 8.11.1 (managed via `./gradlew`)
- **Android Studio**: Android Studio Ladybug / Meerkat or compatible CLI tools

Ensure your `local.properties` file in the root directory points to your Android SDK installation, for example:
```properties
sdk.dir=C\:\\Users\\<username>\\AppData\\Local\\Android\\Sdk
```

---

## 3. How to Build & Test

All commands can be run directly from the project root using the Gradle wrapper:

### Run Unit Tests
```powershell
.\gradlew.bat testDebugUnitTest
```
*All tests (crypto, jitter buffer, mesh relay, packet encoding, and floor control) must pass with zero failures.*

### Build Debug APK
```powershell
.\gradlew.bat assembleDebug
```
- **Output Artifact**: `app/build/outputs/apk/debug/app-debug.apk`
- **Root Convenience Copy**: `TalkLoop-debug.apk`

### Build Release APK
```powershell
.\gradlew.bat assembleRelease
```
- **Output Artifact**: `app/build/outputs/apk/release/app-release.apk`
- **Root Convenience Copy**: `TalkLoop-release.apk`

---

## 4. Keystore & Signing Configuration

- **Debug Builds**: Use standard Android debug keys automatically.
- **Release Builds**:
  - The project root contains `keystore.properties` (gitignored to protect secrets) and `release.keystore`.
  - Properties required in `keystore.properties`:
    ```properties
    storeFile=release.keystore
    storePassword=<keystore_password>
    keyAlias=<key_alias>
    keyPassword=<key_password>
    ```
  - When `keystore.properties` is present, `assembleRelease` automatically signs and aligns the release APK using R8 minify and resource shrinking.
  - If `keystore.properties` is absent, `assembleRelease` will build an unsigned release APK without throwing an error.

---

## 5. Key Documentation Files in Repository

- [VIVO_FEATURE_COMPARISON.md](file:///c:/Reji/Android%20Apps/Offline%20Walkie%20Talkie/VIVO_FEATURE_COMPARISON.md): Comprehensive comparative feature mapping between Vivo Walkie-Talkie (`com.vivo.walkietalkie`) and TalkLoop.
- [IMPLEMENTATION_STATUS.md](file:///c:/Reji/Android%20Apps/Offline%20Walkie%20Talkie/IMPLEMENTATION_STATUS.md): Complete progress breakdown across Stages 0 through 9.
- [PHYSICAL_TEST_PLAN.md](file:///c:/Reji/Android%20Apps/Offline%20Walkie%20Talkie/PHYSICAL_TEST_PLAN.md): End-to-end multi-device physical testing procedures and checklist.
- [HANDOFF_TESTING.md](file:///c:/Reji/Android%20Apps/Offline%20Walkie%20Talkie/HANDOFF_TESTING.md): Verification steps and testing guidelines.
- [FIX_PLAN.md](file:///c:/Reji/Android%20Apps/Offline%20Walkie%20Talkie/FIX_PLAN.md): Past architectural decisions, bug resolution plans, and verification records.
- [AGENT_FIXES_PROMPT.md](file:///c:/Reji/Android%20Apps/Offline%20Walkie%20Talkie/AGENT_FIXES_PROMPT.md): Historical prompt reference for agent fix requirements.

---

## 6. How to Deploy to a Connected Physical Device / Emulator

To install the latest debug APK via ADB:
```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```
Or to install the release APK:
```powershell
adb install -r app\build\outputs\apk\release\app-release.apk
```
Launch the main activity:
```powershell
adb shell am start -n com.example.offlinewalkietalkie/.MainActivity
```
