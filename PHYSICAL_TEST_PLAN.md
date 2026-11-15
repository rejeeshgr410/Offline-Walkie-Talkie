# Physical Test Plan & Verification Log (TalkLoop V3)

All physical radio and on-device scenarios must be validated on physical hardware according to Section 15 and Section 17. Automated tests do not substitute for physical device evidence.

Status Legend:
- `PASS`: Verified by owner on physical devices
- `FAIL`: Fails acceptance criterion
- `DEFERRED`: Automated build/emulator checks complete; awaiting multi-device hardware validation

---

## 1. Stage 0: Repository Baseline & OS Compatibility
- [ ] **Test 0.1: MinSdk 29 Installation**: Verify app installs and launches on Android 10+ (API 29) up to Android 16 (API 36).
  - *Result*: `PARTIAL` (Current QA build installed and visibly rendered on physical Android 17 Pixel 6 and Android 10/API 29 Samsung J8 on 2026-11-14. The existing API 36 AVD could not complete boot under Android Emulator 37.1.11: hardware and software rendering both entered a gfxstream/ADB boot failure before app installation, so Android 11-16 coverage remains pending and is not counted as an app pass.)
- [x] **Test 0.2: Baseline Build & Signing**: Both debug and release signed builds package correctly.
  - *Result*: `PASS` (2026-11-12: debug unit tests, debug assembly, lint, and minified release assembly passed. The signed release APK installed and launched on the physical Pixel 6 and Samsung J8.)

## 2. Stage 1: Liquid Glass UI & Accessibility
- [ ] **Test 1.1: Outdoor High-Contrast Mode**: Test outdoor readability under direct sunlight at full screen brightness.
  - *Result*: `PARTIAL` (2026-11-14: physically toggled Outdoor Sunlight Mode on Pixel 6 and verified immediate high-contrast opaque white surfaces, black 2 px card outlines, black icons, and readable controls; restored the original off state. Direct-sunlight/full-brightness field observation remains pending.)
- [ ] **Test 1.2: Arabic RTL Layout**: Verify bidirectional mirroring, text alignment, and chevrons in Arabic locale.
  - *Result*: `PARTIAL` (2026-11-14: used Android per-app locale `ar` on physical Pixel 6 and visually verified the dashboard, complete Settings surface, and Discover Map. Localized onboarding, dashboard, discovery, SAS verification, contacts, messages, channel create/join, group-management, SOS, radar, voice history, and offline-map surfaces; fixed clipped Arabic headers, auto-mirrored disclosure arrows, and the RTL GPS-coordinate ordering. Full TalkBack traversal and every secondary screen still require manual physical coverage.)
- [ ] **Test 1.3: TalkBack & Accessibility**: Verify semantic content descriptions, PTT double-tap action, and 48dp touch targets.
  - *Result*: `PARTIAL` (2026-11-14: physical Pixel accessibility hierarchy contained 17 interactive controls, all labeled and all at least 48 dp after fixing four undersized header targets. PTT now exposes a button role and a TalkBack double-tap start/stop action. TalkBack was enabled while preserving DeskLink, but Google's first-run seven-page tutorial intercepted the app and ADB-injected gestures could not finish it; the phone was restored to DeskLink-only accessibility and animation scale 1.0. Spoken end-to-end navigation remains pending.)
- [ ] **Test 1.4: Liquid Glass Rendering**: Verify smooth 60fps rendering of frosted glass cards and 176dp PTT orb on API 29-30 (fallback) and API 31+ (RenderEffect).
  - *Result*: `PARTIAL` (2026-11-14: visually verified current UI on physical API 29 Samsung J8 solid fallback and API 37 Pixel 6 enhanced surfaces, including PTT, cards, navigation, diagnostics, RTL, and outdoor mode. Frame pacing/60 fps has not yet been instrumented.)

## 3. Stage 2: Identity, Handshake & Cryptography
- [x] **Test 2.1: Out-of-Band SAS Verification**: Verify 6-digit SAS matches on both screens on first connection.
  - *Result*: `PASS` (2026-11-12: Pixel 6 + Samsung J8 displayed matching code `500268`; both confirmations completed and both dashboards reached `Encrypted (1)`.)
- [x] **Test 2.2: Trusted Contact Auto-Reconnect**: Reconnect without re-prompting SAS when identity is trusted.
  - *Result*: `PASS` (2026-11-13: after first-SAS verification, both QA processes were force-stopped and relaunched. The phones recovered under a new BLE privacy address without displaying the verification screen; Samsung logged `Trusted identity recognized for Pixel 6`, mutual AUTH completed, and both dashboards showed `Ready to Talk` / `1 Connected`, with Pixel showing `Encrypted (1)`.)
- [x] **Test 2.3: Identity Change Blocking**: Verify connection blocks when a known display name presents a different identity key.
  - *Result*: `PASS` (2026-11-13: Pixel's persistent EC identity was deliberately replaced while its app data and Samsung's trusted-contact record were preserved. Samsung logged `Blocked changed identity for known contact Pixel 6`; authentication stayed at zero and both phones remained on the dashboard without a stale verification screen. Restoring the original identity restored trusted auto-reconnect.)

## 4. Stage 3: Nearby Connections & Direct PTT
- [x] **Test 3.1: Direct P2P Point-to-Point**: 1:1 discovery and connection over the selected release transport.
  - *Result*: `PASS` (2026-11-12: clean-data, internet-disabled BLE GATT discovery, deterministic link, matching SAS, mutual AUTH, and encrypted session verified on Pixel 6 + Samsung J8.)
- [x] **Test 3.2: Foreground Service & Screen-Off**: Audio reception continues when device screen is turned off for 10 minutes (WalkieTalkieForegroundService with PARTIAL_WAKE_LOCK & NotificationController).
  - *Result*: `PASS` (2026-11-12 current signed release: Samsung remained in `Dozing` with the same app process for 609.7 seconds, then received, decrypted, and played 79 encrypted frames, sequence 63 through 141, before being awakened.)

## 5. Stage 4: Voice Pipeline, Opus & Latency
- [ ] **Test 4.1: Mouth-to-Ear Latency (Wi-Fi)**: Target < 150 ms measured by two-device acoustic clap.
  - *Result*: `PARTIAL` (Bi-directional Opus voice transmission, reception, decryption, and playback verified on Pixel 8 Pro + Samsung J8. Link bandwidth reported `high=false` (BLE) — measurable latency/live clap test still pending.)
- [ ] **Test 4.2: Mouth-to-Ear Latency (Bluetooth)**: Target < 250 ms.
  - *Result*: `PARTIAL` (2026-11-12: encrypted AMR-NB capture, BLE transfer, decrypt, decode, jitter-buffer playback, and AudioTrack writes verified in both directions. Acoustic latency remains unmeasured.)
- [ ] **Test 4.3: Audio Focus & Interruption**: Phone call interruption cleanly pauses and releases floor.
  - *Result*: `PARTIAL` (Static wiring verified: `AUDIOFOCUS_LOSS`/`LOSS_TRANSIENT` releases PTT and stops capture/playback. A 2026-11-12 offline surrogate test launched Samsung Voice Recorder while PTT was active, but it opened a permission screen and did not generate audio-focus loss; no real incoming-call interruption was produced, so physical call handling remains unproven.)


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
- [x] **Test 9.1: Link Test**: Real-time RTT, loss, and jitter telemetry display without inferred distance.
  - *Result*: `PASS` (2026-11-14: with Wi-Fi and mobile data disabled and no IP route on either phone, Pixel 6 sent eight authenticated encrypted PING frames to Samsung J8 over Bluetooth LE Direct and received 8/8 authenticated PONG replies. The on-device UI displayed 187 ms RTT, 0% loss, 0.0 ms jitter, and 3/5 quality; the implementation makes no distance claim.)
- [x] **Test 9.2: Redacted Diagnostics Export**: Verify no keys, plaintext, or sensitive IDs in exported logs.
  - *Result*: `PASS` (2026-11-14: generated and visually inspected the on-device report after the physical link test. It contained only app/API/transport labels, capability booleans, aggregate packet counters, jitter, and a coarse latency bucket. Automated extraction found no 64-hex secret, MAC address, IP address, or forbidden sensitive JSON key; Copy, Share Report, and Close actions were present with accessible semantics.)

## 10. Stage 9: Full Regression & Hardening
- [ ] **Test 10.1: Complete Offline Operation**: Verify full flow with Mobile Data off, Wi-Fi disconnected from internet, and SIM removed.
  - *Result*: `PARTIAL` (2026-11-12: mobile data and Wi-Fi were disabled on both phones; discovery, pairing, encryption, and bidirectional PTT passed. SIM removal and every non-PTT feature remain pending.)

---

## Field Verification Log (2026-11-14)

Devices: **Pixel 6** (Android 17/API 37, serial `26021FDF60020R`) + **Samsung J8 SM-J810F** (Android 10/API 29, serial `7ad79009`). Wi-Fi and mobile data were disabled on both; `ip route` was empty on both during the tests.

1. Added and physically passed authenticated encrypted Link Test: 8/8 PING/PONG replies over Bluetooth LE Direct, 187 ms RTT, 0% loss, 0.0 ms jitter, and no inferred-distance claim.
2. Generated the on-device redacted report and verified its copy/share/close UI. Extracted JSON contained no 64-hex secret, MAC address, IP address, or sensitive JSON key.
3. Reworked and visually verified Arabic RTL for the primary user journey, Settings, and Discover Map. Localized group/SOS/radar/history/offline-map surfaces in source; Discover Map’s GPS-coordinate ordering was physically corrected with an LTR isolate.
4. Fixed accessibility targets and PTT semantics. The physical hierarchy now has 17/17 labeled controls at or above 48 dp and exposes PTT as a clickable accessibility button.
5. Installed the current QA APK on API 29 Samsung and API 37 Pixel. Both rendered correctly; API 29 used the intended opaque fallback.
6. A clean synchronized offline restart recognized both trusted identities and established authenticated session keys on both devices without a SAS prompt.
7. A post-fix 1.8-second Pixel-to-Samsung PTT regression captured/encoded AMR-NB, sent to one authenticated peer, received encrypted/authenticated frames through sequence 82, and wrote PCM to Samsung `AudioTrack`.
8. Outdoor Sunlight Mode toggled to high-contrast opaque surfaces and back without navigation or state loss.
9. Post-mesh-fix direct location sharing passed offline: Pixel Discover Map displayed the connected Samsung as `SM-J810F` at `25.32287°, 55.39051°`, approximately 1 m away, with a live update age of 7 seconds.
10. Focused deterministic mesh regression passed: group admission/topology simulations cover 3, 4, 5, and 6 participants, and a dedicated A → B → C → D relay test proves a relay-only B plus a member-and-relay C delivers the original packet to D while preserving the remaining hop limit.
11. Android 15+ foreground-service lifecycle smoke test passed on Pixel: a trusted reconnect while the activity was visible created the `microphone|connectedDevice` foreground service (`startForegroundCount=1`, background-start denied=false); after Home, the same service remained foreground. Background reconnect starts are now deliberately deferred until the activity is visible rather than risking an Android policy exception.

---

## Field Verification Log (2026-11-12)

Devices: **Pixel 6** (serial `26021FDF60020R`) + **Samsung J8 SM-J810F** (serial `7ad79009`). Wi-Fi and mobile data were disabled on both devices.

### Verified PASS on current BLE GATT build
1. Clean app-data launch and symmetric discovery established one stable direct BLE link.
2. Both devices displayed the same six-digit SAS (`500268`), accepted confirmation, exchanged AUTH, and showed one connected encrypted peer.
3. Pixel → Samsung PTT delivered more than 150 encrypted AMR-NB frames; Samsung logged successful decryption and `AudioTrack` playback writes.
4. Samsung → Pixel PTT captured/encoded 100+ frames and every server notification completed with GATT success; Pixel remained connected/encrypted and its instant-replay buffer increased to five seconds.
5. No internet transport was available during the test.
6. The previous Android 16 16 KB compatibility dialog was absent when the current debug APK launched on the Pixel 6. The signed release APK passed 16 KB ZIP alignment and signature verification, installed, and opened its intact onboarding UI on that phone offline.
7. The current signed release survived a 609.7-second Samsung screen-off/doze receive soak with the same process and then decrypted/played 79 encrypted frames (sequence 63-141).
8. Two clean two-phone restart/address-rotation cycles produced matching fresh SAS values (`352511`, then `684728`). The first cycle reached mutual AUTH and one encrypted peer; the second was intentionally rejected on Samsung and correctly ended at zero connected/zero authenticated with no AUTH.

### Fixes proven by this run
- Durable pairing requests now survive cold-start collection races.
- Strict Lean-header validation no longer misclassifies framed AUTH packets.
- GATT server notifications are serialized and wait for `onNotificationSent`.
- A locally confirmed SAS may use a received AUTH as proof of a lost remote-confirm control packet, preventing an asymmetric pairing deadlock during BLE address rotation.
- Fresh SAS codes clear confirmation and session state from the previous attempt.
- After both production apps were restarted, a stale unauthenticated GATT-server entry under a rotated BLE address is evicted when the same named device is rediscovered. The phones recovered to a matching fresh SAS (`223069`), re-established `Encrypted (1)`, and Pixel-to-Samsung encrypted PTT again decrypted successfully (2026-11-12).
- Pairing now retains separate local and remote confirmation state. A rotated scan address can no longer evict the live GATT link in the short interval between SAS confirmation and AUTH completion.
- The QA regression run displayed matching SAS `607270`, reached exactly one encrypted peer on each phone, decrypted 82 consecutive Pixel-to-Samsung AMR-NB frames, and logged Samsung PTT fan-out to exactly `1 peer(s)`.
- Stopping and relaunching the Pixel process produced a real disconnect, a new BLE privacy address, and a matching fresh SAS (`842895`) on both phones without a duplicate peer.
- Final signed release artifact `AA813DF7FCA15208D072749C18055166ABBC80C9772C761E76BE0EBF59F51BCC` displayed matching SAS `800249`, reached `Encrypted (1)`, and decrypted/played 62 consecutive Pixel-to-Samsung voice frames offline.
- Remote SAS rejection blocks authentication and disconnects the rejecting phone. This run originally exposed a stale verification page on the other phone; the symmetric reject/disconnect handling and delayed identity-bearing pairing receipt implemented on 2026-11-13 removed it.
- The 2026-11-12 build did not support trusted no-prompt reconnect. Persistent identity and trusted-contact runtime integration were implemented and physically passed on 2026-11-13; see the field log below.

### Still required before release
- Measure true acoustic mouth-to-ear latency with a synchronized acoustic stimulus/recorder. ADB/UI timestamps are not a valid substitute.
- Perform a real incoming-call interruption test. The offline Voice Recorder surrogate did not request conflicting audio focus.
- Android 11-16 OS coverage, full TalkBack/outdoor tests, and Wi-Fi transports remain open. The deterministic 3+ mesh suite now passes, but an out-of-range A ↔ B ↔ C test still requires a third physical Bluetooth-capable phone; the Android emulator cannot stand in for a BLE mesh radio.

---

## Field Verification Log (2026-11-13)

Devices: **Pixel 6** (serial `26021FDF60020R`) + **Samsung J8 SM-J810F** (serial `7ad79009`). Both remained offline during the test.

### Verified PASS on the persistent-identity QA build
1. First pairing displayed matching SAS `979587`; both phones confirmed, exchanged AUTH, established the encrypted session, and persisted the verified identity fingerprint.
2. Force-stopping and relaunching both apps caused a fresh BLE transport/address cycle but no SAS prompt. Samsung recognized the trusted Pixel identity, AUTH completed, and both dashboards returned to one connected peer.
3. Replacing Pixel's EC identity while preserving its display name and app data was blocked as an identity change. No authenticated session formed and neither phone retained a stale verification page.
4. Restoring Pixel's original identity restored trusted auto-reconnect. Both dashboards showed `Ready to Talk` / `1 Connected`; Pixel showed `Encrypted (1)`.
5. A final 3.5-second Pixel-to-Samsung PTT produced encrypted AMR-NB frames. Samsung logged authenticated encrypted frames, successful decrypts through sequence 283, jitter-buffer playback, and successful `AudioTrack` writes at `playFrame #200` and `#250`.
6. A signed-release smoke test exposed and then verified the fix for a one-sided process restart: an actual new GATT bearer for a known handset now retires stale bearers under the previous BLE privacy address. With Samsung left running and only Pixel restarted, both phones re-established trusted AUTH without SAS. The final signed artifact (SHA-256 `369DCCE317F50F4DAC00DD6B82712BFBE6E2523653DE6EB81FADFCEBC718FE24`) delivered 114 authenticated frames; Samsung reported `hasKey=true` and successful decryption for all 114, played the stream, and recorded zero missing-key frames.
7. The Contacts Back control was tested on the Pixel after a restored Navigation3 stack. Root-tab navigation now collapses duplicate/restored Contacts, Messages, or Settings entries to Home; the control returned to the dashboard showing `Ready to Talk`, `1 Connected`, and `Encrypted (1)`.

Evidence screenshots: `.test-artifacts/trusted-reconnect/trusted-restart-pixel.png`, `.test-artifacts/trusted-reconnect/trusted-restart-samsung.png`, `.test-artifacts/trusted-reconnect/final-identity-block-pixel-awake.png`, `.test-artifacts/trusted-reconnect/final-identity-block-samsung-awake.png`, `.test-artifacts/trusted-reconnect/final-restored-pixel.png`, `.test-artifacts/trusted-reconnect/final-restored-samsung.png`, `.test-artifacts/signed-release-2026-11-13/fixed-pixel-after-back.png`, and `.test-artifacts/signed-release-2026-11-13/final-release-pixel.png`.

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
