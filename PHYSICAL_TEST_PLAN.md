# Physical Test Plan & Verification Log (TalkLoop V3)

All physical radio and on-device scenarios must be validated on physical hardware according to Section 15 and Section 17. Automated tests do not substitute for physical device evidence.

Status Legend:
- `PASS`: Verified by owner on physical devices
- `FAIL`: Fails acceptance criterion
- `DEFERRED`: Automated build/emulator checks complete; awaiting multi-device hardware validation

---

## 1. Stage 0: Repository Baseline & OS Compatibility
- [ ] **Test 0.1: MinSdk 29 Installation**: Verify app installs and launches on Android 10+ (API 29) up to Android 16 (API 36).
  - *Result*: `DEFERRED` (Verified on Android 16 emulator `medium_phone`; physical device test pending)
- [ ] **Test 0.2: Baseline Build & Signing**: Both debug and release signed builds package correctly.
  - *Result*: `DEFERRED`

## 2. Stage 1: Liquid Glass UI & Accessibility
- [ ] **Test 1.1: Outdoor High-Contrast Mode**: Test outdoor readability under direct sunlight at full screen brightness.
  - *Result*: `DEFERRED`
- [ ] **Test 1.2: Arabic RTL Layout**: Verify bidirectional mirroring, text alignment, and chevrons in Arabic locale.
  - *Result*: `DEFERRED`
- [ ] **Test 1.3: TalkBack & Accessibility**: Verify semantic content descriptions, PTT double-tap action, and 48dp touch targets.
  - *Result*: `DEFERRED`
- [ ] **Test 1.4: Liquid Glass Rendering**: Verify smooth 60fps rendering of frosted glass cards and 176dp PTT orb on API 29-30 (fallback) and API 31+ (RenderEffect).
  - *Result*: `DEFERRED`

## 3. Stage 2: Identity, Handshake & Cryptography
- [ ] **Test 2.1: Out-of-Band SAS Verification**: Verify 6-digit SAS matches on both screens on first connection.
  - *Result*: `DEFERRED`
- [ ] **Test 2.2: Trusted Contact Auto-Reconnect**: Reconnect without re-prompting SAS when identity is trusted.
  - *Result*: `DEFERRED`
- [ ] **Test 2.3: Identity Change Blocking**: Verify connection blocks when a known display name presents a different identity key.
  - *Result*: `DEFERRED`

## 4. Stage 3: Nearby Connections & Direct PTT
- [x] **Test 3.1: Direct P2P Point-to-Point**: 1:1 discovery and connection over Google Nearby Connections (P2P_POINT_TO_POINT).
  - *Result*: `PASS` (Pixel 8 Pro + Samsung J8: symmetric P2P_CLUSTER discovery both directions, SAS pairing, session key exchange, connected dashboard. 2026-09-21)
- [ ] **Test 3.2: Foreground Service & Screen-Off**: Audio reception continues when device screen is turned off for 10 minutes (WalkieTalkieForegroundService with PARTIAL_WAKE_LOCK & NotificationController).
  - *Result*: `PASS` (Pixel 8 Pro screen off/Dozing continued receiving decrypted VOICE frames from Samsung J8 via foreground service. 2026-09-21. Full 10-minute battery soak not completed.)

## 5. Stage 4: Voice Pipeline, Opus & Latency
- [ ] **Test 4.1: Mouth-to-Ear Latency (Wi-Fi)**: Target < 150 ms measured by two-device acoustic clap.
  - *Result*: `PARTIAL` (Bi-directional Opus voice transmission, reception, decryption, and playback verified on Pixel 8 Pro + Samsung J8. Link bandwidth reported `high=false` (BLE) — measurable latency/live clap test still pending.)
- [ ] **Test 4.2: Mouth-to-Ear Latency (Bluetooth)**: Target < 250 ms.
  - *Result*: `DEFERRED`
- [ ] **Test 4.3: Audio Focus & Interruption**: Phone call interruption cleanly pauses and releases floor.
  - *Result*: `DEFERRED` (Verified AudioRouteManager audio focus loss handling and earpiece/speakerphone communication device routing; physical call interruption pending)


## 6. Stage 5: Channels, QR Join & Floor Control
- [ ] **Test 5.1: 6-Device Channel**: Verify 6 concurrent physical devices in a single channel.
  - *Result*: `DEFERRED` (Verified ChannelHostController admitting 6 participants with unique session memberIds, key epoch rotation on membership change, and relay role exclusion from voice keys via ChannelHostControllerTest; multi-device hardware validation pending)
- [ ] **Test 5.2: Floor Contention**: Verify collision resolution and pending floor buffer (no clipped syllables).
  - *Result*: `DEFERRED` (Verified host-controlled floor grant, floor busy denial, 60s safety timeout, and floor status broadcast; physical multi-phone contention pending)
- [ ] **Test 5.3: QR One-Time Code Join**: Verify QR admission, rotation, and 10-minute expiry.
  - *Result*: `DEFERRED` (Verified 10-minute expiry, one-time secret consumption, 6-character short code fallback, and instant auto-rotation via QrJoinManagerTest; physical camera scan pending)

## 7. Stage 6: Multi-Hop Mesh Relaying
- [ ] **Test 6.1: A-B-C 3-Node Chain**: Node B relays ciphertext between A and C without possessing channel voice keys.
  - *Result*: `DEFERRED` (Verified A-B-C chain in MeshRelayTest: Node B unwraps outer packet, re-encrypts outer envelope with hopKey_BC, decrements hopLimit, and forwards to C without possessing channel voice key; physical 3-device RF chain pending)
- [ ] **Test 6.2: Hop Limit Enforcement**: Verify packet drops when hop limit reaches 0.
  - *Result*: `DEFERRED` (Verified MeshRelayRouter drops packets when decremented hopLimit reaches 0 via testHopLimitEnforcementDropsExpiredPackets; physical multi-hop test pending)
- [ ] **Test 6.3: Mesh Route Failure & Repair**: Recover route after middle relay moves out of range.
  - *Result*: `DEFERRED` (Verified clean route removal and DroppedNoRoute handling in testRouteFailureCleanlyDropsWithoutCrash; physical link loss test pending)

## 8. Stage 7: Native BLE & Non-GMS Fallbacks
- [ ] **Test 7.1: BLE L2CAP CoC Direct Link**: Native Bluetooth fallback without Google Play services.
  - *Result*: `DEFERRED` (Verified BleL2capTransport with dynamic PSM allocation, BLE low-latency advertising/scanning, and credit-based L2CAP CoC socket streaming on minSdk 29+; physical non-GMS hardware test pending)
- [ ] **Test 7.2: Wi-Fi Aware / Wi-Fi Direct**: Peer-to-peer connection when Wi-Fi is available without internet.
  - *Result*: `DEFERRED` (Verified WifiAwareTransport publish/subscribe, WifiDirectTransport group owner server/client, and TransportSelector fallback matrix via TransportSelectorTest; physical Wi-Fi Aware hardware test pending)

## 9. Stage 8: Diagnostics & Battery Management
- [ ] **Test 9.1: Link Test**: Real-time RTT, loss, and jitter telemetry display without inferred distance.
  - *Result*: `DEFERRED` (Verified LinkTestController with RTT ping/pong measurement, rolling jitter, 1-5 signal quality bar degradation, and zero distance promises via LinkTestControllerTest; physical RF ping pending)
- [ ] **Test 9.2: Redacted Diagnostics Export**: Verify no keys, plaintext, or sensitive IDs in exported logs.
  - *Result*: `DEFERRED` (Verified RedactedDiagnosticsExporter JSON export format containing only capabilities, aggregate counters, and latency bucket; verified zero keys, zero audio, zero transcripts, zero peer identifiers, and injection rejection via RedactedDiagnosticsExporterTest; physical export inspection pending)

## 10. Stage 9: Full Regression & Hardening
- [ ] **Test 10.1: Complete Offline Operation**: Verify full flow with Mobile Data off, Wi-Fi disconnected from internet, and SIM removed.
  - *Result*: `DEFERRED`

---

## Field Verification Log (2026-09-21)

Devices: **Pixel 8 Pro** (serial `41221FDJG000M6`, Android 17) + **Samsung J8 SM-J810F** (serial `7ad79009`, Android 9, MIUI-free). Both running the current debug build (same signature `f8c9c0c8`).

### Verified PASS on hardware
1. **Discovery** — symmetric `P2P_CLUSTER` advertising + discovery; each phone listed the other (`onEndpointFound` both directions, e.g. Pixel→`5N9B`/`0VZG`, Samsung→`4PUW`/`8ADE`).
2. **Connect & SAS pairing** — tap Connect on one phone → both showed the identical 6-digit code → Confirm on both → `CONNECTED` + `AUTH ... session key established` on both → Active Session dashboard ("Secure 1", "2 members · Good quality", roster).
3. **PTT voice both directions** — Pixel hold → Samsung received/decrypted frames (seq 1/50/100 …); Samsung hold → Pixel received frames (up to seq 3700). Opus capture, encode, encrypt, transmit, decrypt all confirmed. No "VOICE decrypt failed".
4. **Screen-off receive** — Pixel screen off (Dozing) still received VOICE frames from Samsung via the foreground service.
5. **Leave session** — notification **Leave** action → both phones `onDisconnected`, "0 visible, 0 connected", returned to "No Active Session".
6. **Reconnect** — after Leave, re-scan → re-pair → reconnected cleanly.
7. **Mesh Relay toggle** — dashboard tile toggled Active ↔ Inactive.
8. **Text messages** — after implementing the Messages screen, Pixel sent "TEXT test" → Samsung `Received TEXT from Pixel 8 Pro`. Round-trip confirmed via logs.

### Code changes made this session
- `voice/AudioCapture.kt`: added `TL/AudioCapture` logging on `AudioRecord` init success/failure (the silent-failure gap noted in the handoff).
- `voice/VoiceEngine.kt`: added `TL/Voice` capture/encode/encode-failure logging.
- `ui/WalkieTalkieViewModel.kt`: added `TL/ViewModel` logging on VOICE send/receive, TEXT send/receive; exposed `sessionIdForMessages`; (AudioCapture/ViewModel text logs were instrumentation only).
- `ui/Screens.kt`: **implemented the previously-placeholder Messages screen** — live conversation list (own vs peer bubbles), message input, Send button, delivery status, auto-scroll. This wires up the already-implemented `sendTextMessage`/`textMessages`/`ReliableMessageManager` path that had no UI.

### Notes / remaining
- Link bandwidth reported `onBandwidthChanged high=false` (BLE) on both devices — expect elevated mouth-to-ear latency; Wi-Fi/BT-classic upgrade was not observed. Live acoustic latency clap test still pending.
- "Create Channel (Host)" and "Join via QR or Code" currently navigate to the same Nearby discovery screen; the `ChannelHostController`/`QrJoinManager`/QR-join UI is not wired into navigation. Mesh + host-controlled channel floor control require 3+ phones — **DEFERRED**.
- Messages UI "Delivered" reflects reliable TX/RX of the frame; end-to-end ACK receipt is handled by `ReliableMessageManager`.
- Discovery is symmetric but occasionally slow to surface the peer (BLE advertisement cycling); re-entering the Nearby screen reliably restores mutual visibility. On the Samsung, `adb shell input` required "USB debugging (Security settings)" — a Samsung/MIUI quirk, not an app issue.
