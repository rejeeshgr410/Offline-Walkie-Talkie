# TalkLoop — Core Issues Analysis & Fix Plan (Revised)

**Date:** 2026-09-21  
**Devices:** Pixel 8 Pro (Android 17, API 37) + Samsung J8 SM-J810F (Android 9, API 28)

---

## Issue 1: No Sound Heard After Connection (CRITICAL)

### Symptom
After connecting and pressing PTT, logs show voice frames are sent, received, and decrypted — but **no audio is heard on the receiving phone at all**, not through the speaker and not through the earpiece, even though the speaker option is selected in the UI.

### Root Cause (5 combined problems)

**A. AudioManager mode never set to MODE_IN_COMMUNICATION**  
The `AudioPlayback` creates an `AudioTrack` with `USAGE_VOICE_COMMUNICATION`, which routes to Android's `STREAM_VOICE_CALL`. But the `AudioManager.mode` is never set to `MODE_IN_COMMUNICATION` — it stays at the default `MODE_NORMAL`. On modern Android, `USAGE_VOICE_COMMUNICATION` audio with `MODE_NORMAL` is either:
- Not routed to any audible output at all, OR
- Routed at the `STREAM_VOICE_CALL` volume, which is typically 0 if the user hasn't made a phone call recently

This is the **primary cause of complete silence** on both phones.

**B. setSpeakerphone() never called at session init**  
`_speakerphoneEnabled` starts as `true` in the ViewModel, but `AudioRouteManager.setSpeakerphone(true)` is never called when entering a session. The UI shows "Speaker" as enabled, but the actual AudioManager routing was never configured to match. On the Pixel (API 31+), `setCommunicationDevice()` is never called. On the Samsung (API 28), `isSpeakerphoneOn` is never set.

**C. On API 31+, setCommunicationDevice() called without MODE_IN_COMMUNICATION**  
`AudioRouteManager.setSpeakerphone()` on API 31+ calls `setCommunicationDevice()` but never sets `audioManager.mode = MODE_IN_COMMUNICATION`. The Android docs require the audio mode to be `MODE_IN_COMMUNICATION` for `setCommunicationDevice()` to take effect. Without it, the call is essentially a no-op.

**D. AudioPlayback silently swallows ALL exceptions**  
`start()`, `playFrame()`, and `stop()` all have `catch (e: Throwable) {}` with no logging. If AudioTrack construction or `.play()` fails, `audioTrack` stays null or in a non-PLAYING state. Then `playFrame()` checks `playState == PLAYSTATE_PLAYING` and silently drops every frame. There is zero indication of failure.

**E. Shared OpusCodec across encoder and decoder threads**  
`VoiceEngine` creates one `OpusCodec()` instance. The encoder (`codec.encode()`) runs on the capture coroutine (Dispatchers.IO), while the decoder (`codec.decode()` in JitterBuffer.push) runs from the ViewModel's flow collection (Main dispatcher), and PLC (`codec.decodeLostFrame()` in JitterBuffer.pop) runs from the playback coroutine (Dispatchers.IO). The Opus native decoder is stateful and not thread-safe. Concurrent decode/decodeLostFrame calls from different threads can corrupt decoder state, producing garbage audio or empty frames that are silently dropped.

### Fix

1. **Set MODE_IN_COMMUNICATION** in `AudioRouteManager.requestAudioFocus()` and in `setSpeakerphone()` on API 31+:
   ```kotlin
   // In requestAudioFocus(), before requesting focus:
   audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
   
   // In setSpeakerphone() API 31+ branch, before setCommunicationDevice():
   audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
   ```

2. **Call setSpeakerphone() at session start**: In `VoiceEngine.startPlayback()`, after `requestAudioFocus()`, call `routeManager?.setSpeakerphone(true)` (or whatever the current state is).

3. **Add logging to AudioPlayback**: Log AudioTrack init success/failure, playState after play(), and frame write results.

4. **Give each JitterBuffer its own OpusDecoder**: Instead of sharing the VoiceEngine's single codec, create a dedicated decode-only codec per JitterBuffer so there's no cross-thread decoder contention.

5. **Boost STREAM_VOICE_CALL volume**: When entering a session, ensure the voice call stream volume is at a reasonable level (at least 70% of max).

---

## Issue 2: Discovery Fails Without Wi-Fi (CRITICAL)

### Symptom
Phones discover each other only when connected to the same Wi-Fi network. With Wi-Fi radio turned off on both phones, discovery fails completely. **Location Services (GPS) was ON on both phones.**

### Root Cause

**A. Nearby Connections requires Wi-Fi radio ON (not internet, the radio itself)**  
Google Nearby Connections with `P2P_CLUSTER` uses multiple mediums for discovery and connection:
- **BLE** for initial advertising/discovery
- **Wi-Fi Direct / Wi-Fi Hotspot** for upgrading to a data connection

When the user turns Wi-Fi OFF in Quick Settings, it disables the Wi-Fi radio entirely. This kills:
- Wi-Fi LAN discovery (mDNS)
- Wi-Fi Direct capability (needs Wi-Fi radio)
- Wi-Fi Hotspot medium (needs Wi-Fi radio)

BLE advertising/scanning can still work with Bluetooth alone, but Nearby Connections needs Wi-Fi for the **connection upgrade phase** — even if BLE discovers a peer, the API may fail to establish the actual data channel without Wi-Fi. On some Android versions/devices, Nearby Connections won't even attempt BLE-only discovery when Wi-Fi is off.

**This is a fundamental Google API limitation, not a bug in our code.** Nearby Connections was designed for "offline" meaning "no internet" — NOT "no Wi-Fi radio."

**B. No fallback to native transports**  
`TransportSelector` exists with proper fallback logic (BLE L2CAP → Wi-Fi Direct → Bluetooth Classic) but is **never used at runtime**. `TransportManager` hardcodes `NEARBY_CONNECTIONS` when GMS is available and never switches. If Nearby can't discover, the user is stuck.

**C. No user guidance about Wi-Fi radio requirement**  
The Nearby Devices screen shows Bluetooth/Location status but never checks or warns about Wi-Fi radio state. The user reasonably assumes "offline walkie-talkie" works with Wi-Fi completely off.

### Fix

1. **Add Wi-Fi radio state detection**: Check `WifiManager.isWifiEnabled()` on the Nearby Devices screen. If Wi-Fi is off, show a prominent banner:
   > "Turn on Wi-Fi for faster discovery. No internet connection needed — just the Wi-Fi radio."
   
   with a button to open Wi-Fi Settings.

2. **Add automatic transport fallback timer**: If Nearby Connections fails to discover any peer after 15 seconds AND Wi-Fi is off, automatically switch to BLE L2CAP transport (which works with Bluetooth alone). Show a toast: "Switching to Bluetooth-only mode."

3. **Wire up TransportSelector**: Use `TransportSelector.selectTransport()` in `TransportManager` when the primary transport fails, instead of staying stuck on Nearby Connections.

4. **Show transport status on Nearby screen**: Display which transport is active and what radios are available/enabled, so the user can troubleshoot.

---

## Issue 3: PTT Button Doesn't Release When Finger Lifts (CRITICAL)

### Symptom
The "Hold to Talk" button starts recording when pressed, but continues recording after the user lifts their finger. It behaves like a toggle instead of a hold-to-talk.

### Root Cause
In `PttOrb.kt` line 110, the `pointerInput` modifier uses `floorState` as its key:

```kotlin
.pointerInput(floorState) {   // <-- BUG: key changes mid-gesture
    if (!isHeldByOther) {
        detectTapGestures(
            onPress = {
                isPressed = true
                onPressStart()              // changes floorState Idle → Talking
                val released = tryAwaitRelease()  // CANCELLED by recomposition
                isPressed = false
                onPressEnd()                // NEVER REACHED
            }
        )
    }
}
```

When `onPressStart()` fires, it calls `viewModel.requestPtt()` which changes `floorState` from `Idle` to `Talking`. Since `floorState` is the `pointerInput` key, **Compose restarts the pointerInput coroutine**, cancelling `tryAwaitRelease()`. The `onPressEnd()` callback is never called — the finger-lift event is lost.

### Fix
Change the `pointerInput` key from `floorState` to `Unit`. Move the `isHeldByOther` check inside the `onPress` callback:

```kotlin
.pointerInput(Unit) {
    detectTapGestures(
        onPress = {
            if (!isHeldByOther) {
                isPressed = true
                onPressStart()
                tryAwaitRelease()
                isPressed = false
                onPressEnd()
            }
        }
    )
}
```

Since `isHeldByOther` is derived from a Compose state (`floorState`), reading it inside the callback still captures the latest value via the snapshot system.

---

## Issue 4: All Three Home Buttons Open the Same Page (HIGH)

### Symptom
"Create Channel (Host)", "Join via QR or Code", and "Scan Nearby Devices" all navigate to the same Nearby Devices scanning screen.

### Root Cause
In `Screens.kt` lines 322-333, all three button callbacks are identical:
```kotlin
onCreateChannel = { viewModel.startNearby(); onNavigateToNearby() },
onJoinChannel   = { viewModel.startNearby(); onNavigateToNearby() },
onScanNearby    = { viewModel.startNearby(); onNavigateToNearby() }
```

The `ChannelHostController` and `QrJoinManager` exist with unit tests but are not wired into UI navigation. There are no separate routes in `Navigation.kt`.

### Fix
- **A.** Add `CreateChannel` route and screen: enter channel name → `viewModel.createChannel(name)` → show QR code / short code → wait for peers → start session.
- **B.** Add `JoinChannel` route and screen: enter short code (and optionally scan QR) → validate via `QrJoinManager` → join channel.
- **C.** "Scan Nearby Devices" keeps the existing direct 1:1 flow.

---

## Issue 5: Mesh Network Not Functional (LOW)

### Symptom
Mesh relay toggle exists on the dashboard but does not relay voice between 3+ devices.

### Root Cause
The `MeshRelayRouter` exists with unit tests but is not wired into the live ViewModel. Current relay is a simple "forward to all connected peers except sender" — correct for star topology but not true multi-hop mesh.

### Fix
Change the "Mesh Relay" tile subtitle from "Relay Active" to "Direct Relay" to be accurate. True multi-hop mesh requires 3+ phones to test and is deferred.

---

## Issue 6: Contacts Screen Not Functional (MEDIUM)

### Symptom
The Contacts tab exists but doesn't persist or manage trusted contacts.

### Root Cause
No persistent storage for trusted contact identities. The contact trust system (identity key pinning, auto-reconnect) exists in design but is not implemented.

### Fix
- Add `TrustedContactStore` using encrypted SharedPreferences.
- When SAS pairing succeeds, offer to save the peer as a trusted contact.
- On reconnection, skip SAS verification if identity key matches a trusted contact.

---

## Issue 7: Messaging Verification (LOW)

### Symptom
Text messages verified in logs but UI round-trip needs re-verification with current build.

### Fix
Test-only. Verify after issues 1-4 are fixed.

---

## Priority Order

| # | Issue | Severity | Effort | Why this order |
|---|-------|----------|--------|----------------|
| 1 | **PTT doesn't release** (Issue 3) | Critical | Small — 1 file, ~5 lines | Blocks all voice testing |
| 2 | **No sound heard** (Issue 1) | Critical | Medium — 3 files | Core feature completely broken |
| 3 | **Discovery without Wi-Fi** (Issue 2) | Critical | Medium — 3 files + new UI | Blocks offline use case |
| 4 | **3 buttons same page** (Issue 4) | High | Large — new screens + routes | UX confusion |
| 5 | **Contacts** (Issue 6) | Medium | Large — new persistence | Feature gap |
| 6 | **Mesh label** (Issue 5) | Low | Tiny — string change | Honesty fix |
| 7 | **Messaging** (Issue 7) | Low | None — test only | Verification |

---

## Approach

Fix issues 1-3 first (all critical). Then issue 4 (high). Rebuild and test on both phones after each fix. Issues 5-7 are lower priority or test-only.
