# Implementation Status - TalkLoop V3

## Current Stage
2026-11-15 update: the six requested implementation areas are described in
`FEATURE_IMPLEMENTATION_2026-11-15.md`. This includes live two-layer group voice,
independent voice keys and relay-only admission, acknowledged codec negotiation,
encrypted recovery checkpoints, persistent map work, encrypted message history,
and RFCOMM registration/pairing. No physical testing was performed for these changes.
Earlier physical results below apply to earlier builds and must not be used as
acceptance evidence for the changed wire protocol.

Latest software revision (2026-11-15): trusted BLE reconnect now requires a fresh
private-key possession proof before session activation. The automated results
below apply to this revision; the historical device passes in this document
predate this proof exchange and do **not** qualify the new reconnect path.

Software build and unit-test gates pass. Current BLE GATT discovery, matching
SAS pairing, mutual authentication, and encrypted bidirectional PTT passed on
Pixel 6 + Samsung J8 with Wi-Fi and mobile data disabled (2026-11-12). A
process restart also recovered under a new BLE privacy address, produced a fresh
matching SAS, and retained one canonical peer after re-authentication. A
609.7-second screen-off receive soak and repeated fresh-SAS restart cycles now
pass. Persistent device identity, trusted no-prompt reconnect, known-contact
identity-change blocking, and symmetric reject/disconnect UI cleanup passed on
the same two phones on 2026-11-13. The release path is still **not fully
field-qualified** because measured acoustic latency, real-call interruption,
broader OS coverage, and 3+-device channel/mesh validation remain open. The 16 KB native
library packaging defect has been remediated and verified on the Pixel 6 and
against the release artifact.

## Trusted BLE session hardening - 2026-11-15

### Flow traced
- `TransportManager` defaults to `BleGattTransport`. GATT CONNECT_REQ and PAIR
  receipts carry the six-digit SAS, peer name and SHA-256 public-key fingerprint.
  The event flow and durable pending-request flow both reach the ViewModel.
- `WalkieTalkieViewModel` records local/remote SAS confirmation, exchanges AUTH
  public keys and installs ECDH-derived candidate session keys in `CryptoManager`.
  Persistent EC identity storage in encrypted preferences and the existing
  SHA-256/ECDH key derivation remain unchanged by this revision.
- The live contact authority is `TrustedContactsRepository` (DataStore, verified
  fingerprints). `identity/TrustedContactStore` is a separate legacy boolean
  store and is not consulted by this session path. `ReconnectManager` backoff
  alone does not authorize a live GATT session.

### Changes in this revision
- Added `DirectPeerTrust`: only an exact, well-formed verified fingerprint can
  auto-confirm the current SAS. New/unverified peers still require manual SAS
  approval. A normalized known name with a changed or missing fingerprint is
  blocked. Names are a conservative change alarm, never authentication; a
  changed identity with a different name appears as an untrusted new peer.
- The ViewModel uses one contact snapshot and an attempt object to invalidate
  work suspended across contact lookup, accept or confirmation. Duplicate
  requests retain the attempt's decision. A fresh SAS resets confirmations,
  identity binding, candidate keys and proof state. Replacing an identity on the
  same approved SAS rejects the link. A delayed identity-bearing receipt may
  replace an unapproved identity-less fallback, clearing its state first.
- Added `TrustedReconnectProof` on direct AUTH_ACK frames. Returning peers must
  answer a random 32-byte challenge using the ECDH candidate key. AES-GCM proof
  packets use random 12-byte nonces and authenticate protocol version, packet
  direction, sender/recipient fingerprints and current SAS. Reflection, packet
  tampering and proofs from previous reconnect attempts are rejected.
- A matching advertised fingerprint and received AUTH key is still mandatory.
  Trusted auto-confirmation retains remote confirmation handling; a public AUTH
  alone cannot activate a returning session. Pending proofs retry through the
  existing three-second AUTH loop and fail closed after 20 seconds.
- Candidate keys cannot authorize session/UI activation, voice, text or normal
  incoming control traffic until proof succeeds. Group membership control keeps
  its separate admission path. Group membership alone no longer creates a
  SAS-verified direct contact. A manually SAS-approved peer can answer a trusted
  peer's proof even when trust exists on only one device.
- Reject, explicit disconnect, fresh attempt, peer loss and leave clear direct
  pairing/proof state, candidate keys and authentication indicators. Failed
  proofs do not replace stored trusted contacts. Unrelated working-tree changes
  were preserved.

### Automated verification and limits
- `./gradlew.bat testDebugUnitTest lintDebug assembleDebug -PqaInstall=true`:
  **167 tests, zero failures/errors; lint and QA debug assembly passed**.
- Added 12 focused JVM tests in `DirectPeerTrustTest` and
  `TrustedReconnectProofTest`: returning verified identity with changed address,
  name and SAS; changed/missing identity; unverified and unknown peers;
  non-unique names; locale-independent name comparison; mutual possession
  proof; copied public identity; wrong identity binding; reconnect/SAS replay;
  reflection/tampering/malformed packets; asymmetric trust; removed keys and
  retry after packet loss.
- These tests cover decision/proof components used by the live ViewModel; they
  do not instantiate Android GATT or prove end-to-end device/UI timing.
- Both devices need the new proof responder for trusted reconnect. Older apps
  cannot complete it and time out; there is no automatic downgrade to public-key
  acceptance. Fresh manual SAS pairing keeps its existing confirmation flow.
- This revision has not been installed or tested on physical phones and no new
  signed release is claimed. Historical device evidence below remains historical.

### Physical-device verification still required for this revision
1. Install the updated QA build on both phones. Clear test contacts and confirm
   matching fresh SAS on both; verify no session before approval, then encrypted
   voice/text in both directions and persisted verified contacts.
2. Restart each phone/app separately and together, including BLE privacy address
   rotation. Verify no SAS prompt for returning identities, exactly one secured
   peer, successful challenge response and bidirectional encrypted PTT/text.
3. Retain one trusted store and clear the other. Verify the cleared side requires
   SAS approval, the returning side waits for possession proof, and both recover.
4. Reset/reinstall a peer identity while retaining its old name in the other
   phone's contacts. Verify the warning/rejection, symmetric UI cleanup, zero
   secured peers/application traffic and preservation of the original contact.
   Also verify fingerprint substitution between PAIR and AUTH is rejected.
5. Exercise duplicate/delayed PAIR receipts, packet loss, same-address reconnect,
   reject/disconnect during confirmation and the 20-second proof timeout. Verify
   stale confirmations/keys/proofs cannot activate a new link or leave phantom UI.
6. Repeat locked/background reconnect and foreground-service recovery, confirm
   mixed-version timeout fails closed, and regression-test group admission/PTT.
   Existing acoustic latency, real-call interruption, wider OS coverage and
   3+-device field gates remain open in `PHYSICAL_TEST_PLAN.md`.

## Completed Stages
- **Stage 0: Repository Baseline & OS Compatibility**
  - Updated minSdk to 29, compileSdk to 36, targetSdk to 36.
  - Added pinned dependencies: Google Tink 1.16.0 (X25519/HKDF/AEAD), DataStore Preferences 1.1.2, ZXing 3.5.3, Play Services Base 18.5.0.
  - Initialized `PHYSICAL_TEST_PLAN.md` with numbered tests for all 10 stages.
  - Verified debug and release baseline builds succeed without compilation breaks.

- **Stage 1: Compose Shell, Liquid Glass Theme Foundations, Navigation, i18n, Capability Manager**
  - Implemented the TalkLoop Liquid Glass theme foundations (`Color.kt`, `Theme.kt`, `Type.kt`).
  - Created `GlassSurface.kt` with RenderEffect blur on API 31+ and solid high-contrast fallback for API 29-30 and outdoor mode.
  - Built 176dp luminous `PttOrb.kt` with hold-to-talk gestures, radial gradient lighting, state animations, and accessibility semantics.
  - Implemented canonical Active Session and Idle dashboard states per Section 11 & Section 19.12 (Brand lock-up, status capsule, current channel card with horizontal avatar row, 2x2 quick action grid, connection telemetry strip, 4-tab bottom navigation).
  - Added full Arabic RTL translation (`res/values-ar/strings.xml`) and comprehensive string resources (`res/values/strings.xml`).
  - On 2026-11-14, removed hardcoded primary-flow copy from onboarding, dashboard, discovery, verification, contacts, messages, channel create/join, Settings, groups, SOS, radar, voice history, and map management; physically verified Arabic RTL mirroring on Pixel 6 and corrected clipped headers, disclosure arrows, and GPS-coordinate ordering.
  - Raised every dashboard interactive target to at least 48 dp and added an explicit TalkBack button role plus double-tap start/stop action for PTT. Physical accessibility-tree audit passed 17/17 labeled controls at or above 48 dp.
  - Physically verified the current API 29 solid fallback on Samsung J8 and API 37 enhanced UI on Pixel 6. The existing API 36 emulator remains a host-runtime blocker due to Emulator 37.1.11 gfxstream/ADB boot failure.
  - Created `CapabilityManager.kt` handling non-GMS detection, BLE LE, Wi-Fi Direct/Aware support.
  - Resolved permission screen lock-up with resilient `PermissionManager.kt` and bypass navigation, validated on Android 16 (`emulator-5554`).

- **Stage 2: Versioned Binary Codec, Two-Layer Packet Envelope, Identity/Handshake, Trusted Contacts**
  - Implemented §19.1 Two-Layer Packet Envelope (`InnerPacket` end-to-end + `OuterPacket` hop-by-hop framing).
  - Implemented §19.3 session-scoped random 8-byte `memberId` to prevent permanent identity key exposure.
  - Created `TinkCryptoEngine.kt` implementing X25519 ECDH, HKDF-SHA256 key derivation (`talkloop-v3-hop`, `talkloop-v3-channel`, `talkloop-v3-sas`), ChaCha20Poly1305 AEAD, and deterministic 6-digit SAS codes.
  - Built `TwoLayerCodec.kt` allowing intermediate mesh nodes to decrypt and authenticate hop-by-hop outer packets without possessing end-to-end voice keys.
  - Implemented DataStore-backed `TrustedContactsRepository.kt` with verified SAS tracking.
  - Integrated a stable P-256 device identity persisted through Android Keystore-backed encrypted preferences. The BLE pairing receipt carries its SHA-256 fingerprint, AUTH must match that fingerprint before ECDH session derivation, and a verified identity is saved only after mutual SAS confirmation.
  - Integrated trusted-contact lookup into the live BLE path: an exact trusted fingerprint reconnects without a second SAS prompt, while a known display name presenting a different fingerprint is rejected before AUTH. Physical trusted-reconnect and identity-change tests passed on Pixel 6 + Samsung J8 on 2026-11-13.
  - Fixed one-sided process restart/address rotation: when an actual GATT connection arrives for the same named handset under a fresh privacy address, stale bearers and their pairing state are retired before the new link is published. The final signed artifact survived a Pixel-only restart, re-established trusted AUTH, and delivered 114/114 decryptable frames to the continuously running Samsung with zero missing-key packets.
  - Unit tests in `TwoLayerPacketTest` passing: encode/decode round-trip, outer unwrap, inner unwrap, tamper detection, ECDH agreement, and SAS derivation.

- **Stage 3: Nearby Connections Direct Link, FGS/Notification, PTT Pending-Floor Buffer**
  - Built `PendingFloorBuffer.kt` implementing §19.4 500 ms in-memory ring buffer (up to 25 frames of 20 ms audio) so the first syllables are never clipped while floor request is pending.
  - Implemented `FloorController.kt` with full state machine (`Idle`, `Requesting`, `Granted`, `Talking`, `Denied`, `Releasing`, `HeldByOther`), 60s safety timeout, and deterministic 1:1 collision resolution via lower `memberId`.
  - Built persistent `WalkieTalkieForegroundService.kt` with partial CPU wake lock for screen-off receiving and `NotificationController.kt` providing session status and Open, Talk, Leave actions.
  - Updated `NearbyConnectionsTransport.kt` with configurable Strategy (`P2P_POINT_TO_POINT` direct 1:1, `P2P_STAR`, `P2P_CLUSTER`) and bandwidth quality detection.
  - Built `FakeTransport.kt` for mock transport unit/integration tests and added hardware volume key PTT override in `MainActivity.kt`.
  - Verified with 15 passing automated unit/integration tests including `PendingFloorBufferTest`, `FloorControllerTest`, and `FakeTransportDirectPttTest` with test `PcmCodec`. Verified on Android 16 (`emulator-5554`).

- **Stage 4: Opus Voice Pipeline, Audio Routing, Adaptive Jitter Buffer, Latency Tracking**
  - Integrated `OpusCodec` (48 kHz mono, 20 ms frames) with native Packet Loss Concealment (PLC via empty decode) and `PcmCodec` fallback.
  - Configured `AudioCapture` with hardware acoustic echo cancellation (`AcousticEchoCanceler`), noise suppression (`NoiseSuppressor`), and automatic gain control (`AutomaticGainControl`).
  - Implemented `AudioRouteManager` managing transient audio focus (`AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE`), phone call interruption auto-pause, and speakerphone vs earpiece routing (`setCommunicationDevice` on API 31+).
  - Built `JitterBuffer` with 40–60 ms adaptive delay target, 200 ms stale voice cutoff on receiver clock (§19.14), 1–2 frame Opus PLC concealment on packet loss, and RFC 3550 inter-arrival jitter telemetry.
  - Verified with automated unit test suite `JitterBufferTest` and `PcmCodecTest`.

- **Stage 5: Channels, Group Membership, Auto-Rotating QR Join, and Floor Control State Machine**
  - Implemented `ChannelHostController` managing up to 6 tested members with session-scoped 64-bit `memberId`s (§19.3).
  - Built host-controlled floor control with collision resolution, 60s safety timeout, and `FLOOR_STATUS` broadcast (§2.2, §3).
  - Implemented key epoch rotation (§19.2) on membership change: new voice keys distributed to authorized members; relays strictly excluded.
  - Built `QrJoinManager` with auto-rotating QR code, 10-minute expiry, one-time secret, 6-character short code, and ZXing bitmap generation (§19.10).
  - Implemented `ReliableMessageManager` for `TEXT`, `FLOOR_*`, and `KEY_UPDATE` with ACK and 3-attempt exponential backoff (300/600/1200 ms) (§19.8).
  - Built `ReplayBuffer` storing up to 3 most recent voice transmissions in memory only (§14).
  - Unit tests in `ChannelHostControllerTest`, `QrJoinManagerTest`, `ReliableMessageManagerTest`, and `ReplayBufferTest` all passing cleanly.

- **Stage 6: Multi-Hop Mesh Relaying & Bounded Tree Forwarding**
  - Built `MeshRelayRouter` implementing hop-by-hop OuterPacket re-encryption without possessing channel voice keys (§19.1 & §19.2).
  - Built `MeshRouteTable` maintaining bounded distribution trees, neighbor metrics, and next-hop selection (initial 3 hops, max 5 hops per §2).
  - Implemented `SeenPacketCache` bounded LRU cache (1,000 entries) preventing loops, echoes, and duplicate playback.
  - Added `rewrapOuter` in `TwoLayerCodec` allowing constant-time outer envelope re-encryption under egress link keys.
  - Automated unit test suite `MeshRelayTest` passing: A-B-C 3-node chain ciphertext relaying without voice keys, hop limit decrement and enforcement, duplicate/loop drop, and route failure resilience.

- **Stage 7: Native BLE L2CAP CoC & Wi-Fi Aware / Wi-Fi Direct Fallbacks**
  - Implemented `BleL2capTransport` on `minSdk 29` (Android 10+) using dynamic PSM allocation, low-latency BLE advertising/scanning, and credit-based L2CAP CoC sockets (§19.15).
  - Built `WifiAwareTransport` using `WifiAwareManager` publish/subscribe and link-local sockets for hardware supporting `FEATURE_WIFI_AWARE`.
  - Maintained `WifiDirectTransport` group owner server/client for devices where Aware is unavailable.
  - Implemented `TransportSelector` and integrated fallback matrix into `TransportManager`: Nearby Connections (primary when GMS is available), Wi-Fi Aware, Wi-Fi Direct, and BLE L2CAP CoC without crashing when Google Play services is absent.
  - Automated unit test suite `TransportSelectorTest` passing.

- **Stage 8: Link Test, Reconnect Backoff & Redacted Diagnostics Export**
  - Implemented `LinkTelemetry` and `LinkTestController` tracking real-time RTT, rolling jitter, 1–5 signal quality bars, and strictly zero distance promises (§0).
  - Wired the controller into Settings with authenticated encrypted PING/PONG probes, live packet counters, RTT/loss/jitter tiles, a bounded eight-probe run, and an explicit no-distance result.
  - Built `ReconnectManager` implementing exponential backoff (1s, 2s, 4s, 8s, 16s), untrusted peer reconnection rejection (§3, §19.4), and 10s host disconnect freeze (§13).
  - Created `RedactedDiagnosticsExporter` exporting privacy-safe JSON reports containing capability booleans, aggregate telemetry, and latency buckets while guaranteeing zero keys, audio, transcripts, or peer IDs (§14).
  - Added an on-device redacted-report dialog with privacy notice, copy/share actions, and a fail-closed sensitive-field check before display.
  - Automated unit test suites `LinkTestControllerTest`, `ReconnectManagerTest`, and `RedactedDiagnosticsExporterTest` all passing cleanly.
  - Physically passed the offline diagnostic flow on 2026-11-14 using Pixel 6 + Samsung J8: 8/8 encrypted replies, 187 ms RTT, 0% loss, no IP route on either device, and a report extraction free of secrets, addresses, messages, audio, and identities.

- **Stage 9: Full Regression & Hardening, ProGuard/R8, Release Signing, and Desktop APK Delivery**
  - Configured `app/proguard-rules.pro` keeping Google Tink, Kopus JNI, Nearby Connections, ZXing, DataStore, Coroutines, and serializable packet models.
  - Built minified, optimized release APK with R8 and signed with `release.keystore`.
  - Rebuilt after the 2026-11-15 six-feature implementation. Current artifact: `app/build/outputs/apk/release/app-release.apk` (9,345,627 bytes; SHA-256 `300762C3E6F1421D6AD5748D6767657FB966190F0EBD63C537A67FFC4ADE2DFD`), verified with APK Signature Scheme v2 and 16 KB zip alignment. This artifact has automated verification only; no physical testing was performed for it.
  - Delivered signed release APK to user desktop at `C:\Users\reji2\Desktop\TalkLoop-v3-release.apk` (6.86 MB).
  - Validated clean permission flow and full Liquid Glass UI dashboard navigation on Android 16 (`emulator-5554`).
  - Passed all 52 unit tests cleanly across the test suite.

## Current Blockers
- Persistent identity, trusted no-prompt reconnect across BLE address rotation,
  known-name/different-key rejection, and symmetric rejection UI cleanup now
  pass on the Pixel 6 + Samsung J8 QA build. A restored trusted session also
  transferred, decrypted, and played a final 3.5-second encrypted PTT stream.
  The one-sided restart case also passes after stale GATT-bearer retirement.
- The 10-minute current-release screen-off receive soak passes. True acoustic
  mouth-to-ear latency and an actual incoming-call interruption still require
  physical stimulus/call equipment; the attempted offline app-based surrogate
  did not generate audio-focus loss.
- The 16 KB compatibility warning is resolved: native `LOAD` segments and APK
  ZIP alignment are 16 KB compliant, and the current debug build launches on
  the Android 16 Pixel 6 without the compatibility dialog. The signed release
  APK installs and launches on both the Pixel and Samsung; the signed-release
  two-phone SAS, AUTH, one-peer, and encrypted Pixel-to-Samsung PTT smoke test passed.
- Wi-Fi Direct, Wi-Fi Aware, BLE L2CAP, and mesh remain experimental until they
  pass their own physical acceptance tests.
- Mesh routing now rejects group-advertising discovery hints until membership is
  proven, forwards trusted location and telemetry frames only when relay mode is
  active and hop-bounded, and exposes a member-plus-relay outcome instead of
  dropping its rewrapped packet. The 3-6 node deterministic suites and an
  A → B → C → D relay test pass; a third physical BLE handset is still required
  for an out-of-range topology acceptance test.
- The Android foreground-service lifecycle now defers a reconnect completed in
  the background until a visible activity returns, as required for microphone
  services on Android 14+. A Pixel Android 17 smoke test confirmed the service
  starts from the visible app (`microphone|connectedDevice`, denied=false) and
  stays foreground after Home. A real process-death/restart recovery test is
  still required before release.
- Transport QoS and codec negotiation need measured link inputs plus an
  authenticated acknowledgement path before being called adaptive.

## Next Action
Produce and verify the updated signed release artifact, then prioritize true
acoustic mouth-to-ear latency, real incoming-call interruption, Android 11-16
coverage, accessibility/RTL/outdoor inspection, and physical 3+-device
channel/mesh validation. `PHYSICAL_TEST_PLAN.md`, not feature inventory or unit
tests, remains the release authority.
