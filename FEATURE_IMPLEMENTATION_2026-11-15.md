# Six-feature implementation record

Scope: source implementation and host-side automated verification only. No ADB,
phone installation, device launch, emulator session, or physical test was run for
this work. Existing field evidence predates these protocol changes and does not
qualify this build.

## 1. Encrypted mesh

Group voice now enters `MeshRelayRouter` through a dedicated framed MESH packet.
Verified neighboring links receive domain-separated hop keys. Each voice packet
is sealed once, then its ciphertext is rewrapped on forwarding. SHA-256 ciphertext
identities suppress duplicates across different ingress links and returning echoes.
Outer authentication covers the complete sender ID and hop count.

Voice keys are independent of membership-proof secrets. The coordinator sends
keys over authenticated encrypted links, excludes relay-only joiners, and rotates
them on roster changes. Relay-only joining is selectable in Settings; such nodes
cannot originate voice and are excluded from coordinator election. Group recovery
retains that role without storing a voice key on a relay. Delayed key updates carry
a monotonic voice epoch. Group state changes require link encryption; only HMAC
membership proofs bootstrap before link authentication.

Tests include a four-node chain, relay-only admission, restart without a relay
voice key, tampered hop count, and origin echo rejection.

## 2. Adaptive audio

An idle active session periodically runs the existing bounded encrypted link test.
Measured loss/RTT can request AMR-NB or low-bitrate Codec2. A random proposal ID,
requested mode and exact peer set must be acknowledged before the sender commits.
Unsupported native Codec2, missing receipts, changing peer sets, or active recording
prevent a change. Received frames identify their codec independently. Settings
provides an adaptive-audio toggle. Probes currently measure the first verified peer;
they are not a simultaneous per-link quality survey of the entire mesh.

## 3. Recovery

The group identity, admitted roster, independent voice key (authorized members only),
membership secret and role are checkpointed in Keystore-backed encrypted preferences.
Restoration resets endpoint addresses, connection trust and proof state. A new
radio/identity handshake is required before audio or the session notification resumes.
Explicit Leave clears the recovery intent and group checkpoint. Microphone service
startup still waits for a visible activity as required by Android; recovery does not
bypass Android's background-start policy or a user force-stop.

## 4. Offline maps

User-requested small-area downloads use unique persistent WorkManager jobs, network
constraints, bounded retries, progress, a pause notification action and saved request
parameters. The app observes the selected job across recreation. A searchable starter
catalogue supplies centres for Dubai, Sharjah, Abu Dhabi, Kochi and London. These are
neighbourhood extracts within the existing 0.2–2 km radius limit, not whole-city packs.

Resume repeats the saved Overpass query: this generated POST response has no byte-range
resume contract. MBTiles import and existing offline rendering remain available.
No public map download was exercised by this test run.

## 5. Message history

History is bounded to 1,000 messages and encrypted at rest. Content, ownership, read
state, delivery state and IDs survive restart. Interrupted sends reopen as failed
and retryable. Retry uses a fresh encrypted packet while retaining the history entry.
Delivery receipts are authenticated, all intended direct recipients must acknowledge,
and retransmitted packets reach the receipt handler without duplicating saved history.
Voice/control encryption derives distinct contextual nonces for independent counters.

## 6. Bluetooth Classic

RFCOMM is registered and selectable from Nearby as an explicit fallback. It discovers
bonded devices and inquiry results, asks Android for discoverability, exchanges identity
fingerprints and random comparison-code material, and uses the same application AUTH
gate. Framing handles short stream reads, bounded lengths, truncation and serialized
writes. Stop closes both established and pending sockets. Classic remains experimental
until its later physical matrix passes; the default transport remains BLE GATT.

## Compatibility and acceptance

Use matching new builds on all peers: authenticated framing, admission messages,
receipts and nonce derivation changed. Earlier app builds are not wire compatible.
Automated results do not establish physical radio range, audible playback, background
OS scheduling, real process-kill recovery or Bluetooth Classic interoperability.
Those acceptance checks remain in the later field session requested by the user.

## Automated verification

- `testDebugUnitTest -PqaInstall=true`: 178 tests; zero failures or errors.
- `lintDebug -PqaInstall=true`: zero errors, 156 warnings.
- `assembleDebug -PqaInstall=true`: passed.
- `assembleRelease`: passed; APK Signature Scheme v2 and 16 KB ZIP alignment verified.
- Release APK: `app/build/outputs/apk/release/app-release.apk` (9,345,627 bytes).
- SHA-256: `300762C3E6F1421D6AD5748D6767657FB966190F0EBD63C537A67FFC4ADE2DFD`.
- `git diff --check`: passed.

The warning count is retained explicitly; a passing lint error gate is not a claim
that all existing warnings or translations have been polished away.
