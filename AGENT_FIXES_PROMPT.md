# Agent Task Prompt — Offline Walkie Talkie: Audio-Path Bug Fix Package

You are a senior Android engineer working on **TalkLoop / Offline Walkie Talkie**
(`com.example.offlinewalkietalkie`, Kotlin + Compose, minSdk 29, targetSdk 36).
Your mission: implement the fixes below **exactly and in priority order**, keeping the
project compiling and all existing unit tests green.

Reported symptom: **two physical devices connect via Nearby Connections, but voice is
not heard when holding Push-To-Talk.** Frames are captured, encoded and transmitted, but
end up dropped, stuck in a buffer, or never written to `AudioTrack`.

---

## 0. Ground rules (non-negotiable)

1. Minimal, surgical diffs. Do not rewrite files or rename public APIs unless a fix says so.
2. Do NOT remove or weaken the existing debug logging (`TL/*` tags). Add to it.
3. Do NOT touch `proguard-rules.pro` keep rules for Tink/Kopus/Nearby/Zxing (they are correct).
4. After **each P0/P1 fix**, run the verification commands in §7. Fix regressions before moving on.
5. Do not commit unless asked.
6. Architecture context: the live voice path is **only**:
   `VoiceEngine → AudioCapture → OpusCodec → WalkieTalkieViewModel.broadcastVoiceFrame
   (AES-GCM per-peer via CryptoManager) → TransportManager (Nearby) → RoutingManager →
   ViewModel frame handler → VoiceEngine.handleIncomingFrame → JitterBuffer (per peer)
   → playback loop (20 ms tick) → AudioPlayback/AudioTrack`.
   `MeshRelayRouter`, `TwoLayerCodec`, `ReconnectManager`, `QrJoinManager` are NOT in the
   live path — do not "fix" audio by wiring them in.

---

## 1. The three P0 bugs (fix these first — they explain the missing audio)

### P0-1 — Channel-mode floor grant can never fire: dead guard in `handleFloorGrant`

**File:** `app/src/main/java/com/example/offlinewalkietalkie/ui/WalkieTalkieViewModel.kt` (lines 429–440)

**Symptom:** In host-controlled channel mode, the talker stays in *Requesting* forever after
the peer grants the floor. The 500 ms `PendingFloorBuffer` is never drained, so **zero audio
frames are ever transmitted**. The receiver even shows "peer is speaking" (it granted), while
hearing nothing.

**Root cause:** `requestPtt()` (channel branch, lines 854–862) sets `_isRecording.value = true`
and starts capture *while requesting*. When `FLOOR_GRANT` arrives, `handleFloorGrant` begins
with `if (_isRecording.value) return` — which is **always true** at that moment, so
`floorController.onFloorGranted()` (which drains the pending buffer and switches to
*Talking*) never executes. The guard was meant to prevent a double capture loop; it
instead blocks the grant itself.

**Fix — replace the whole function with:**

```kotlin
private fun handleFloorGrant(peerId: String) {
    // A grant we never asked for must not open the microphone.
    if (floorController.floorState.value !is FloorState.Requesting) return

    // Applies the grant: drains PendingFloorBuffer via onTransmitPendingFrames,
    // plays the chirp and moves the state machine to Talking.
    floorController.onFloorGranted()

    // Capture was already started at request time; only start it if it is not running
    // (e.g. grant raced a release).
    if (!_isRecording.value) {
        _isRecording.value = true
        voiceEngine.startRecording { sequence, encodedFrame ->
            if (floorController.onAudioCaptured(sequence, System.currentTimeMillis(), encodedFrame)) {
                broadcastVoiceFrame(sequence, encodedFrame)
            }
        }
    }
}
```

**Verify:** unit-test `FloorController` invariants still pass; on device, channel-mode PTT
must transition Requesting → Talking within one RTT of the grant and transmit buffered
frames first (log `TL/ViewModel: Sending VOICE frame` right after `onFloorGranted`).

---

### P0-2 — Live audio frames silently dropped after grant: `onAudioCaptured` return value ignored

**File:** same file, `requestPtt()` channel branch (lines 854–862).

**Symptom:** Even with P0-1 fixed, once the floor is granted no live audio is sent.

**Root cause:** `FloorController.onAudioCaptured(...)` returns `true` to mean
"send this frame live now" — but the caller at line 860 ignores the return value.
Frames are neither buffered (buffering only happens in *Requesting*) nor broadcast:
**they are discarded**.

**Fix — the channel-branch capture callback must be:**

```kotlin
voiceEngine.startRecording { sequence, encodedFrame ->
    val sendLive = floorController.onAudioCaptured(
        sequence, System.currentTimeMillis(), encodedFrame
    )
    if (sendLive) broadcastVoiceFrame(sequence, encodedFrame)
}
```

(`FloorController.onAudioCaptured` already pushes to `PendingFloorBuffer` while in
*Requesting* and returns `true` in *Talking/Granted/HeldByMe*. Do not change
`FloorController` itself.)

**Verify:** on device, continuous audio flows for the whole hold; the first frames on the
wire are the buffered ones, then live frames without a gap.

---

### P0-3 — Playback loop can die silently: non-thread-safe `jitterBuffers` map

**File:** `app/src/main/java/com/example/offlinewalkietalkie/voice/VoiceEngine.kt` (line 34)

**Symptom:** Intermittent total silence on the receiver after some seconds of talk, with no
error surfaced; `TL/AudioPlayback: playFrame` logs stop.

**Root cause:** `private val jitterBuffers = mutableMapOf<String, JitterBuffer>()` is a
`java.util.HashMap`. It is mutated from the **main** thread (`handleIncomingFrame` →
`getOrPut`, called from the `viewModelScope` frame collector) and iterated from the
**IO** thread (`for (peer in jitterBuffers.keys.toList())` in the playback loop). An
unsynchronized put during iteration throws `ConcurrentModificationException`, which kills
the playback coroutine (`playbackJob` completes with an unhandled exception). Nothing
restarts it, so all further audio is silently swallowed.

**Fix:**

```kotlin
private val jitterBuffers = java.util.concurrent.ConcurrentHashMap<String, JitterBuffer>()
```

and harden the playback loop so a single bad frame can never kill the whole loop:

```kotlin
playbackJob = coroutineScope.launch {
    var nextWakeTime = System.currentTimeMillis()
    while (isActive) {
        try {
            val due = ArrayList<ShortArray>(2)
            for (peer in jitterBuffers.keys.toList()) {
                val pcm = jitterBuffers[peer]?.pop() ?: continue
                due.add(pcm)
            }
            if (due.isNotEmpty()) {
                playback.playFrame(shortsToBytes(mixFrames(due)))
            }
        } catch (t: Throwable) {
            Log.w(TAG, "playback tick failed, continuing: ${t.message}")
        }
        nextWakeTime += 20
        val sleepTime = nextWakeTime - System.currentTimeMillis()
        if (sleepTime > 0) delay(sleepTime) else nextWakeTime = System.currentTimeMillis()
    }
}
```

**Verify:** grep the codebase for other unsynchronized shared maps crossed by threads
(`securedPeerIds` is Main-only, that one is fine). Run a long talk session (>2 min) on
hardware; `playFrame` logs must continue for the whole session.

---

## 2. P1 fixes (correctness bugs found during the audit)

### P1-1 — Replay buffer stores Opus bytes but plays them as raw PCM

**File:** `app/src/main/java/com/example/offlinewalkietalkie/channel/ReplayBuffer.kt`

**Root cause:** `onFrameReceived(pcmBytes)` is called from the ViewModel with the
**decrypted Opus payload** (not PCM, despite the parameter name). `playTransmission`
feeds those bytes straight to `AudioPlayback.playFrame`, so a replay emits loud static.

**Fix:** decode before playback. Give `ReplayBuffer` a codec and decode at replay time
(keeps memory small), e.g.:

```kotlin
class ReplayBuffer(
    private val playback: AudioPlayback? = null,
    private val codec: AudioCodec = OpusCodec(),   // new dependency
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
)
```

```kotlin
fun onFrameReceived(encodedOpus: ByteArray) {
    if (!isEnabled) return
    currentRecordingFrames.add(encodedOpus.clone())
}

fun playTransmission(transmissionId: String, onFinished: () -> Unit = {}) {
    val target = synchronized(transmissions) {
        transmissions.firstOrNull { it.id == transmissionId }
    } ?: return
    replayJob?.cancel()
    replayJob = scope.launch {
        playback?.start()
        for (frame in target.frames) {
            val pcm = try { codec.decode(frame) } catch (t: Throwable) { ShortArray(960) }
            playback?.playFrame(shortsToBytes(pcm))
            delay(20)
        }
        onFinished()
    }
}
```

Add a private `shortsToBytes` (copy the one in `VoiceEngine`) or move both converters to a
shared util. Update `ReplayBufferTest` to use `PcmCodec` so the test stays JVM-pure.

### P1-2 — ACK identity mismatch: every reliable TEXT send ends `FAILED`

**Files:**
- `app/src/main/java/com/example/offlinewalkietalkie/ui/WalkieTalkieViewModel.kt`
  (TEXT branch of the frame handler; ACK branch)
- `app/src/main/java/com/example/offlinewalkietalkie/channel/ReliableMessageManager.kt`

**Root cause:** `sendTextMessage` keys pending deliveries as `"$sessionId:$seq"` (the
**sender's** sessionId). The receiver replies with `Frame(type = ACK, sessionId = <its own
sessionId>, sequence = ...)`. The sender then calls
`onAckReceived(decodedFrame.sessionId, decodedFrame.sequence)` — the receiver's sessionId —
which never matches a pending key. Result: every TEXT is retried 3× and reported `FAILED`
even though it was delivered exactly once.

**Fix (two halves):**
1. ACK must **echo the original frame's identity**. In the TEXT branch, build the ACK with
   `sessionId = decodedFrame.sessionId` (keep `sequence = decodedFrame.sequence`).
2. Key pending deliveries by endpoint, not by memberId: in `ReliableMessageManager`,
   replace the `"$memberId:$sequence"` key with `"$endpointId:$sequence"` (you already
   receive `endpointId` in `sendReliable`), and change the signature of
   `onAckReceived(endpointId: String, sequence: Int)`. In the ViewModel ACK branch call
   `reliableMessageManager.onAckReceived(peerId, decodedFrame.sequence)`.

Update `ReliableMessageManagerTest` for the new keying. `processIncomingReliable` remains
unused — leave it, but note it in a comment.

### P1-3 — Direct-collision resolution is asymmetric (both sides can "win")

**File:** `app/src/main/java/com/example/offlinewalkietalkie/ui/WalkieTalkieViewModel.kt`
(`handleFloorRequest`, ~line 414)

**Root cause:** the receiver feeds `peerId.hashCode().toLong() and 0xFFFFFFFFL` into
`FloorController.handleDirectCollision` as the peer's member id, but the local side
compares against its own `sessionId` (a `currentTimeMillis()` value). The two devices run
comparisons over **different value pairs**, so both can decide they won (or both lose) →
simultaneous capture, garbled overlap.

**Fix:** the `FLOOR_REQUEST` frame already carries the sender's sessionId on the wire
(`sendControlFrame` sets `sessionId = sessionId`). Change `handleFloorRequest` to accept
the decoded frame and use the wire value:

```kotlin
private fun handleFloorRequest(peerId: String, senderSessionId: Long) {
    ...
    val localWon = floorController.handleDirectCollision(senderSessionId, System.currentTimeMillis())
    ...
}
```

and at the call site pass `decodedFrame.sessionId`. Now both sides compare
`(local sessionId, peer sessionId)` — identical pairs, deterministic winner (lower
unsigned sessionId wins, per `FloorController`).

### P1-4 — `KEY_UPDATE` will crash the app if ever received; also sends a raw key

**File:** `WalkieTalkieViewModel.kt`, `KEY_UPDATE` branch; `crypto/CryptoManager.kt`.

**Root cause:** the branch calls `cryptoManager.deriveSessionKey(peerId, payload)` with a
**raw 32-byte symmetric key**. `deriveSessionKey` runs it through
`KeyFactory.generatePublic(X509EncodedKeySpec(...))`, which throws
`InvalidKeySpecException` — uncaught inside a `launchIn` collector → process crash. The
`ChannelHostController.onKeyEpochRotated` sender also transmits the channel voice key with
no app-layer encryption (only Nearby's transport encryption).

**Fix:**
1. Add a dedicated setter to `CryptoManager`:

```kotlin
fun setRawSessionKey(peerId: String, keyBytes: ByteArray) {
    require(keyBytes.size == 32) { "Expected 32-byte AES-256 key" }
    sessionKeys[peerId] = SecretKeySpec(keyBytes, "AES")
}
```

2. In the `KEY_UPDATE` branch, wrap in `runCatching { cryptoManager.setRawSessionKey(peerId, decodedFrame.payload) }`
   and log failures at WARN instead of crashing. Because the channel host flow is not
   reachable from the current UI, do NOT remove the branch; just make it safe.

### P1-5 — Foreground service can crash on notification "Talk"/"Leave" taps

**File:** `app/src/main/java/com/example/offlinewalkietalkie/service/WalkieTalkieForegroundService.kt`

**Root cause:** `onStartCommand` handles `ACTION_TOGGLE_PTT` / `ACTION_LEAVE` and returns
without ever calling `startForeground`. If the service is (re)started by the notification
PendingIntent while not already foreground (e.g. after the system killed and
`START_STICKY`-restarted it, or a stale notification after Leave), Android 12+ throws
`ForegroundServiceDidNotStartInTimeException` ~5 s later.

**Fix:** in those two branches, if `!isRunning`, first call the same defensive
`startForeground(NotificationController.NOTIFICATION_ID, notificationController.buildNotification(), serviceType)`
used in `startServiceInternal` (factor that into a small `goForeground()` helper), then
handle the action. For `ACTION_LEAVE` call `goForeground()` then `stopServiceInternal()`
(stopping immediately after a startForeground is legal and avoids the ANR-window crash).

Also **delete the legacy duplicate service** `app/src/main/java/com/example/offlinewalkietalkie/WalkieTalkieService.kt`
and its `<service android:name=".WalkieTalkieService" .../>` entry in
`AndroidManifest.xml` — it is dead code that collides with `NotificationController`'s
notification id 1001 and confuses debugging.

### P1-6 — Home screen channel name gets clobbered with the raw advert string

**File:** `WalkieTalkieViewModel.kt`, `transport.peers.onEach` collector (`setInSession(true)` block).

**Root cause:** on any connection the collector sets
`_currentChannelName.value = connected.first().name`. In group mode the peer's endpoint
name is the encoded advert (`"TAB12MJohns Ph"`), which the Home screen then displays.

**Fix:**

```kotlin
if (connected.isNotEmpty() && !_isInSession.value) {
    _currentChannelName.value = groupState.value.group?.name ?: connected.first().name
    setInSession(true)
}
```

### P1-7 — Missing kotlinx.serialization R8 rules (release builds will break navigation)

**File:** `app/proguard-rules.pro`. `NavigationKeys.kt` types are `@Serializable` and the
release build has `isMinifyEnabled = true`. Without keep rules the generated serializers
can be stripped → crash restoring the nav back stack **only in release**.

**Fix — append:**

```pro
# kotlinx.serialization (Navigation3 NavKey back stack persistence)
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.offlinewalkietalkie.**$$serializer { *; }
```

### P1-8 — BLE fallback advert exceeds 31-byte legacy ADV payload; fallback UI is a dead end

**Files:**
- `transport/bluetooth/BleL2capTransport.kt` — `startAdvertising` uses
  `setIncludeDeviceName(true)` + 128-bit service UUID + 4-byte service data ⇒ >31 bytes on
  most devices ⇒ `onStartFailure` ⇒ silent `TransportState.ERROR`.
- `ui/Screens.kt` — the "Switch to Bluetooth-only mode?" banner calls
  `viewModel.switchToBleTransport()`, but `BleL2capTransport.acceptConnection` is a no-op
  and PSM discovery only works when the peer's advert was receivable. The user ends up in a
  mode that can never complete a session.

**Fix (minimal, honest):**
1. In `BleL2capTransport.startAdvertising`, drop `setIncludeDeviceName(true)` and put the
   display name (truncated, see `GroupAdvert.MAX_NAME`) into the service data instead.
2. Hide the fallback banner until the transport is complete: gate it behind
   `CapabilityManager` + a `BuildConfig`-style feature flag (a simple `const val
   BLE_FALLBACK_ENABLED = false` in `Screens.kt` is acceptable) so users are not routed
   into a broken path.

### P1-9 — `WifiDirectTransport` registers its BroadcastReceiver twice

**File:** `transport/wifidirect/WifiDirectTransport.kt`.
`startAdvertising` and `startDiscovery` both call `context.registerReceiver(receiver, intentFilter)`.
`stop()` unregisters once. Double registration leaks a receiver and duplicates events.

**Fix:** add `private var receiverRegistered = false`; register only when `!receiverRegistered`
(set true), and in `stop()` unregister only when `receiverRegistered` (set false).

---

## 3. P2 hardening & diagnosability (do after P0/P1)

1. **Surface silent voice drops (top on-device suspect for the reported bug).**
   `broadcastVoiceFrame` drops frames with **no log** when `isVoiceTrusted(peer)` is false
   or `cryptoManager.hasSessionKey(peer.id)` is false. Add a throttled one-shot WARN:
   `"VOICE not sent: peer=%s trusted=%s hasKey=%s"` (log only on first drop per PTT press,
   or every 25th frame). On the receive side the equivalent drop already logs
   (`"VOICE dropped ... not group-verified"`). This turns "silence" into an actionable log
   line that tells you exactly which gate failed (AUTH not finished vs group verification).

2. **Off-main-thread audio processing.** The `routingManager.incomingFrames` collector runs
   in `viewModelScope` (Main): every 20 ms it does AES-GCM decrypt + Opus decode on the UI
   thread. Move the collector to a dedicated background dispatcher while keeping ordering:
   ```kotlin
   private val audioScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
   ...
   }.launchIn(audioScope)   // instead of viewModelScope for THIS collector
   ```
   All state it touches is `MutableStateFlow` (thread-safe) or already dispatches into
   `viewModelScope`. Cancel `audioScope` in `onCleared()`.

3. **JitterBuffer stale-drop is arrival-based.** `STALE_VOICE_CUTOFF_MS = 200` measured from
   *arrival* means a normal Nearby/BLE burst of >200 ms silently discards large chunks
   (recursive `return pop()` drops the whole backlog). Change the semantics: compute
   staleness from the **sender timestamp** (`senderTimestampMs`) when non-zero, falling back
   to arrival time; keep the cutoff at 200 ms of *playout age*, not arrival age. Add a
   counter to `JitterBufferMetrics` for stale drops per transmission and log the first one.

4. **`AudioCapture.start` must return success.** Make `start(...): Boolean`, propagate through
   `VoiceEngine.startRecording(...): Boolean`, and in `requestPtt()` set a new
   `_captureError = MutableStateFlow<String?>` + expose it. Show a one-line Compose banner
   on `HomeScreen` when non-null ("Microphone unavailable — check permissions"). Today the
   permission screen's "Continue" bypass allows a session with a dead mic and zero feedback.

5. **Direct mode never signals floor state.** The receiver's orb cannot show "peer is
   speaking" and `ReplayBuffer.onTransmissionStarted` never fires because `FLOOR_STATUS` is
   only broadcast by `ChannelHostController`. In direct mode, broadcast the same
   `FLOOR_STATUS` frame from `requestPtt()` (holderId = `sessionId`, holderName =
   `Build.MODEL`) and `releasePtt()` (holderId = 0) — factor the payload-building code out
   of `createChannel(...)` into a `broadcastFloorStatus(holderId: Long, holderName: String)`
   helper and call it from both places. The receive path already handles `FLOOR_STATUS`.
   Note: `FloorState.HeldByOther.peerId` will hold the holder's sessionId string, so the
   `AvatarBadge` highlight check (`floorState.peerId == peer.id`) will not match — extend
   the badge check to compare names, or leave the orb-only indication. Keep it simple.

6. **Wire the busy tone / chirp.** `FloorController.onPlayBusyTone` and
   `onPlayPermissionChirp` are never assigned. Assign them in the ViewModel to a short
   local tone through `AudioPlayback` (generate a 200 ms PCM beep inline) so floor denials
   and grants are audible.

7. **`ContactsScreen` lies about verification.** It marks every discovered peer "Verified".
   Gate the badge on `TrustedContactStore.isContactTrusted(peer.id)`; show "Not verified"
   otherwise. Do not build new trust flows — just stop displaying false "Verified".

8. **Backup of encrypted prefs.** `android:allowBackup="true"` + `EncryptedSharedPreferences`
   means restored devices cannot decrypt restored prefs (Keystore key is not restored).
   Either set `allowBackup="false"` or add the prefs file to
   `app/src/main/res/xml/data_extraction_rules.xml` / `backup_rules.xml` exclusion list.

9. **`RoutingManager` incoming buffer.** `MutableSharedFlow(extraBufferCapacity = 100)`
   silently drops (`tryEmit` returns false) under main-thread pressure — at 50 frames/s that
   is 2 s of headroom, and drops are invisible. Raise to 512 with
   `onBufferOverflow = BufferOverflow.DROP_OLDEST` **only for the RoutingManager flow**
   (it already uses DROP_OLDEST; just enlarge), and add a drop counter log every 50 drops.

10. **Dead code to remove (safe, mechanical, do last):**
    - `data/DataRepository.kt` (`DefaultDataRepository` emits a constant, unused).
    - `session/ReconnectManager.kt` + its test only if you also delete; it is never
      instantiated. Prefer: leave code, delete nothing you cannot rebuild — mark "unused".
    - `ui/Screens.kt`: `CreateChannelScreen` and `JoinChannelScreen` are unreachable
      (`Navigation.kt` routes both Home buttons to `GroupLobby`), and the matching
      `CreateChannel`/`JoinChannel`/`Session` nav keys are dead. **Decision for reviewer:**
      either delete the two screens + keys, or wire them; do not leave both. Default: delete.
    - Root-package `WalkieTalkieService` (already covered in P1-5).

---

## 4. Tests to add (and keep green)

Existing suites that must stay green:
`PendingFloorBufferTest`, `PcmCodecTest`, `JitterBufferTest`, `FloorControllerTest`,
`GroupSessionManagerTest`, `GroupSecurityTest`, `GroupAdvertTest`, `RelayPlannerTest`,
`MeshRelayTest`, `TwoLayerPacketTest`, `ChannelHostControllerTest`, `QrJoinManagerTest`,
`ReplayBufferTest`, `ReliableMessageManagerTest`, `RedactedDiagnosticsExporterTest`,
`LinkTestControllerTest`, `ReconnectManagerTest`, `TransportSelectorTest`,
`FakeTransportDirectPttTest`.

Add:

1. `FloorControllerTest`: "grant after buffered capture drains pending frames and switches
   to Talking" (captures the P0-1/P0-2 contract at the controller level).
2. `ReliableMessageManagerTest`: update for endpoint-keyed pending map; new case — ACK
   carrying the echoed original sessionId/sequence completes delivery and stops retries.
3. `ReplayBufferTest`: replay of stored **encoded** frames produces decoded PCM of the
   right length (inject `PcmCodec`).
4. `JitterBufferTest`: burst of 15 frames older than 200 ms by *sender* age is no longer
   wholesale-dropped when arrival was recent (encodes the P2-3 semantics change).

---

## 5. On-device verification runbook (two physical devices)

Logcat filter:

```
adb logcat -s TL/Voice TL/AudioCapture TL/AudioPlayback TL/AudioRoute TL/ViewModel TL/Nearby TL/Routing TL/GroupPort
```

Healthy direct 1:1 session must show, in order:

```
TL/Nearby: onConnectionInitiated ... / CONNECTED endpoint=...
TL/ViewModel: Sending AUTH to ... (65 bytes)
TL/ViewModel: AUTH from ... - session key established (secured=1)
TL/ViewModel: PTT start (direct) -> 1 peer(s)
TL/AudioCapture: AudioRecord initialized, session=..., buffer=...
TL/Voice: captured+encoded frame 1 ...
TL/ViewModel: Sending VOICE frame 1 to 1 peer(s)      ← both sides
TL/ViewModel: Received VOICE seq=1 ...              ← receiver
TL/ViewModel: VOICE decrypt OK seq=1 ...
TL/JitterBuffer: buffering -> playing (buffer=2 frames)
TL/AudioPlayback: AudioTrack started: useSpeaker=true usage=MEDIA ...
TL/Voice: playback pop #1 from <peer> (960 samples)
TL/AudioPlayback: playFrame #1: wrote 1920/1920 bytes, maxAmp=<non-zero>
```

`maxAmp` must be non-zero on speech; if it hovers near 0 the sender side is capturing
silence (mic source/permission issue), if decrypt fails you will see the explicit
`VOICE decrypt FAILED` line, and if frames never arrive there will be no `Received VOICE`
at all (transport — check `TL/Nearby` for bandwidth/strategy lines).

Test matrix (run all):
1. Direct 1:1, both devices on same screen (`Scan Nearby` → Connect → confirm SAS code).
2. PTT within 1 s of connecting (exercises the AUTH race + new drop logs).
3. Group of 3: create → two joins → both approvals → talk from each member.
4. Speaker ↔ earpiece toggle mid-session (both directions audible).
5. Screen off on receiver → audio still plays (foreground service + wake lock).
6. Phone call arrives mid-PTT → floor released, session survives after call ends.
7. Volume-up hold-to-talk on both devices.
8. TEXT send/receive → status shows **Delivered** (validates P1-2).
9. Replay latest transmission → intelligible speech (validates P1-1).

---

## 6. Acceptance criteria

- [ ] `gradlew.bat assembleDebug` and `testDebugUnitTest` pass with zero new warnings about changed files.
- [ ] Direct 1:1: voice audible both directions within ~1 s of PTT press, sustained >30 s.
- [ ] Channel/host-controlled mode: grant transitions Requesting→Talking, buffered frames
      transmit first, live frames continue with no gap (P0-1, P0-2).
- [ ] No `ConcurrentModificationException` or silent playback death over a 2 min talk (P0-3).
- [ ] TEXT messages show "Delivered" on the sender (P1-2).
- [ ] No crash paths: KEY_UPDATE receipt, notification Talk tap after system restart,
      volume-key PTT, incoming call (P1-4, P1-5).
- [ ] Release build (`assembleRelease` if a keystore is configured, otherwise lint only)
      — serialization keep rules present (P1-7).
- [ ] Every intentional frame drop now logs a reason.

---

## 7. Build & verify commands (Windows PowerShell, repo root)

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

Run unit tests for a single class while iterating:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.example.offlinewalkietalkie.voice.*"
```

Install on both devices:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

---

## 8. Summary table (order of execution)

| # | Severity | File(s) | One-line root cause |
|---|----------|---------|---------------------|
| P0-1 | Critical | ui/WalkieTalkieViewModel.kt:429 | `_isRecording` guard always true → floor grant never applied → nothing transmitted |
| P0-2 | Critical | ui/WalkieTalkieViewModel.kt:859 | `onAudioCaptured` return ignored → live frames dropped after grant |
| P0-3 | Critical | voice/VoiceEngine.kt:34 | HashMap crossed Main/IO threads → playback coroutine dies silently |
| P1-1 | High | channel/ReplayBuffer.kt | Opus bytes played as raw PCM (static) |
| P1-2 | High | ViewModel + ReliableMessageManager | ACK carries wrong sessionId → all TEXT marked FAILED |
| P1-3 | High | ViewModel handleFloorRequest | Collision IDs asymmetric → both sides can "win" |
| P1-4 | High | ViewModel + CryptoManager | KEY_UPDATE feeds raw key into ECDH parser → guaranteed crash |
| P1-5 | High | WalkieTalkieForegroundService (+ delete legacy WalkieTalkieService) | FGS branches without startForeground → API 31+ crash |
| P1-6 | Med | ViewModel peers collector | Advert string clobbers channel name |
| P1-7 | Med | proguard-rules.pro | Missing kotlinx.serialization rules → release nav crash |
| P1-8 | Med | BleL2capTransport + Screens | 31-byte ADV overflow; broken BLE fallback exposed in UI |
| P1-9 | Med | WifiDirectTransport | BroadcastReceiver double-registration |
| P2-1..10 | Low | various | Drop logging, off-main-thread audio, jitter staleness semantics, mic-failure surfacing, floor signaling in direct mode, tones, contacts honesty, backup safety, buffer sizing, dead code |
