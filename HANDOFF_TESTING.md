# TalkLoop — Handoff for Two-Device Testing & Debugging

Written 2026-09-20 when the previous agent ran out of usage. Read this whole file first.

## Goal
Make the offline walkie-talkie app (project root = this folder) work as intended. Original complaint: two phones (Pixel 8 Pro, Xiaomi 11X, both with USB debugging) **could not discover each other**.

Spec: `C:\Users\reji2\Downloads\Offline_Walkie_Talkie_V3.2_Spec.md` (Section 19 overrides earlier sections; Section 20 = agent process rules). Status docs: `IMPLEMENTATION_STATUS.md`, `PHYSICAL_TEST_PLAN.md`.

## Files in this folder
| File | What it is |
| --- | --- |
| `TalkLoop-debug-fixed.apk` (71 MB) | **Current build with all fixes below.** Debug-signed. Use this for testing. |
| `TalkLoop-v3-release.apk` | OLD release build (before fixes). Do not test with it. |
| `app/src/main/java/com/example/offlinewalkietalkie/` | Kotlin sources |
| `HANDOFF_TESTING.md` | this file |

- Rebuild: `./gradlew :app:assembleDebug -q` -> `app/build/outputs/apk/debug/app-debug.apk`
- Unit tests: `./gradlew :app:testDebugUnitTest` (passed after the changes)
- Release build (`assembleRelease`) was NOT rebuilt after the fixes (the user interrupted it). `proguard-rules.pro` has no log stripping, so release keeps `TL/*` logs.

## Devices
- **Pixel 8 Pro**, adb serial `41221FDJG000M6`, Android 17 (SDK 37), screen 1008x2244. Has the fixed debug APK installed (the old release-signed install was uninstalled first; debug and release signatures differ, so switching needs `adb uninstall com.example.offlinewalkietalkie`).
- **Samsung J8 (SM-J810F)**, adb serial `7ad79009`, Android 9, screen 720x1480 (dp). Replaced the Xiaomi 11X for field testing; has the same debug build installed (signature `f8c9c0c8`). Note: `adb shell input` on some Samsung/MIUI builds needs Developer options -> **USB debugging (Security settings)** enabled.
- Both phones have Google Play services, so Nearby Connections is usable.
- Git Bash tip: prefix adb commands that use `/sdcard/...` paths with `MSYS_NO_PATHCONV=1`. Pixel tap for "Scan Nearby Devices" on Home: `adb -s 41221FDJG000M6 shell input tap 502 1467`.

## 2026-09-21 field testing result — core two-phone features PASS
Verified on Pixel 8 Pro + Samsung J8: symmetric discovery, SAS pairing, Active Session dashboard, bi-directional PTT voice, screen-off receive, notification Leave, reconnect, Mesh Relay toggle, and text messages (after implementing the Messages UI). See `PHYSICAL_TEST_PLAN.md` -> "Field Verification Log". Debug build passes, all unit tests pass.

Remaining DEFERRED (need 3+ phones): host-controlled channel floor control, QR/short-code join, and multi-hop mesh. Note: "Create Channel"/"Join via QR" buttons currently route to the same Nearby discovery screen — the channel/QR UI is not yet wired into navigation.

## Root causes found and fixed (all in source, all compile)
1. **Discovery was one-sided.** Only "advertise" or only "discover" ran depending on which button was pressed, so two phones could never see each other. Added `TransportManager.startSymmetricDiscovery` and `WalkieTalkieViewModel.startNearby()`; the Nearby screen calls it on entry, so every entry path advertises and discovers at once.
2. **Nearby strategy** default changed to `P2P_CLUSTER`. Both sides must use the same Strategy.
3. **Pairing screen never received the request.** `connectionRequests` was a replay-0 shared flow collected by a screen that mounts after the event. Now `ViewModel.pendingRequest` (StateFlow) plus replay=1 in `NearbyConnectionsTransport`. The pairing screen navigates on `isInSession`, not on the local Confirm tap.
4. **Errors were swallowed.** `NearbyConnectionsTransport` now logs (tag `TL/Nearby`), tracks `lastError` / `isAdvertising` / `isDiscovering`, treats ALREADY_ADVERTISING/DISCOVERING/CONNECTED as success, and the Nearby screen shows "Visible as X / Discoverable / Scanning" plus any error text.
5. **Permissions.** `PermissionManager` now requires ALL radio permissions (BT scan/advertise/connect, NEARBY_WIFI_DEVICES on 33+, fine location on <=31), and the permission gate only advances when mic AND radio are granted. Manifest: legacy BT limited to maxSdk 30, location maxSdk 31.
   - **Important finding:** removing `android:usesPermissionFlags="neverForLocation"` from BLUETOOTH_SCAN made `startDiscovery` fail with `ApiException 8034 MISSING_PERMISSION_ACCESS_COARSE_LOCATION` on the Pixel. The flag is restored in the manifest. Do not remove it.
6. **Session state.** `isInSession` started `true` (fake "Field Ops Alpha" session). It now starts `false`, becomes true when a peer connects, false when all disconnect or on leave.
7. **Control-frame dedupe bug.** `RoutingManager` dedupes on (sessionId, sequence, timestamp, type). Control frames all had sequence 0 / timestamp 0, so every FLOOR_RELEASE/ACK after the first was dropped as a duplicate. Control frames now use a monotonic `controlSequence` and a real timestamp.
8. **Handshake race.** On receiving AUTH for a peer with no session key, we now also send our AUTH back.
9. **Double capture.** `handleFloorGrant` now ignores grants unless a floor request is pending and not already recording.
10. Foreground-service wake lock no longer expires after 10 minutes; PTT release is a no-op when nothing is active; relay forwarding respects the relay toggle.
11. Without Google Play services, `TransportManager` starts on BLE L2CAP instead of Nearby.

## Verified so far (Pixel only)
- Debug build passes; unit tests pass.
- Idle Home screen shows correctly ("No Active Session").
- Tapping Scan Nearby: logcat shows `Advertising as 'Pixel 8 Pro' strategy=P2P_CLUSTER` and `Discovering strategy=P2P_CLUSTER` with no errors.
  Command: `adb -s 41221FDJG000M6 logcat -d -s TL/Nearby:* TL/TransportManager:* TL/ViewModel:*`

## NOT yet verified — do these next (nothing below was tested on two phones)
1. ~~Install on the Xiaomi~~ — DONE with Samsung J8 (serial `7ad79009`). Permissions/location/Bluetooth granted and verified on both.
2. ~~Discovery both directions~~ — DONE (PASS). Each phone lists the other (`onEndpointFound` both ways). Note: discovery is occasionally slow to surface the peer; re-entering the Nearby screen reliably restores mutual visibility. No `TL/Nearby` errors.
3. ~~Connect + SAS pairing + dashboard~~ — DONE (PASS). Same 6-digit code on both, `CONNECTED endpoint=`, `AUTH ... session key established`, Active Session dashboard.
4. ~~PTT both directions~~ — DONE (PASS). Opus voice round-trip both ways; no `VOICE decrypt failed`. Added `TL/AudioCapture` + `TL/Voice` logging to expose the previously-silent `AudioRecord` init (it initialises fine on both devices).
5. ~~Text messages, leave session, screen-off receive, reconnect~~ — DONE (PASS). Text round-trip verified (Pixel→Samsung). Leave via notification action works. Screen-off receive works. Reconnect after Leave works.
6. Channel create/join (QR/code) and mesh need 3+ phones — **DEFERRED**. Note: "Create Channel"/"Join via QR" currently route to the same Nearby discovery screen; the channel/QR UI is not wired into navigation.

## Known gaps / suspicions
- The live runtime path uses the older single-layer `Frame` protocol (`protocol/Frame.kt`) with a per-peer `CryptoManager` (ECDH + ChaCha20-Poly1305, unauthenticated key exchange). The spec's §19 two-layer codec (`TwoLayerCodec.kt`), `TinkCryptoEngine`, `MeshRelayRouter`, `ChannelHostController`, etc. exist with unit tests but are **not wired into the ViewModel**. The pairing screen shows Nearby's own auth digits, not a spec SAS. Get discovery/connect/PTT working first, then decide whether to wire these in.
- Direct 1:1 mode: `requestPtt()` starts capture immediately and broadcasts voice; the receiver plays it (no floor request/grant). Channel mode uses FLOOR_REQUEST/GRANT.
- Both phones may call `requestConnection` at once; handled as non-fatal `STATUS_OUT_OF_ORDER_API_CALL`, but confirm on hardware.
- Sender timestamps are fed into jitter stats as if clocks were comparable (spec 19.14 says not to); only affects telemetry.
- Dashboard "Security" tile shows "SAS Verified" regardless of state; some dashboard strings are hardcoded.
- Release APK (R8, ~7 MB) is much easier to push than the 71 MB debug APK, but must be rebuilt with `./gradlew :app:assembleRelease` (signed with `release.keystore`).

## Useful commands
```
adb devices -l
adb -s <serial> logcat -c
adb -s <serial> logcat -s TL/Nearby:* TL/TransportManager:* TL/ViewModel:* AndroidRuntime:E
adb -s <serial> shell am force-stop com.example.offlinewalkietalkie
adb -s <serial> shell am start -n com.example.offlinewalkietalkie/.MainActivity
MSYS_NO_PATHCONV=1 adb -s <serial> shell screencap -p /sdcard/s.png
MSYS_NO_PATHCONV=1 adb -s <serial> pull /sdcard/s.png
```

## Rules the user cares about
- No cloud/analytics/accounts; everything offline; never log audio, keys or payloads.
- Do not lower security or drop mesh/fallback features to make tests pass; report real FAILs.
- Don't claim physical tests passed unless observed on the devices.
