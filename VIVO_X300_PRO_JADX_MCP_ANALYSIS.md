# Vivo Walkie-Talkie 1.2.0.16.0 — JADX MCP static analysis

## Scope and method

Target: `reference-apks/vivo-x300-pro/Vivo-WalkieTalkie-1.2.0.16.0.apk`

The target was pulled from the connected Vivo device and its remote/local SHA-256 values matched:

`2ad3164ec83dacf9d3a0d1a2c6a91d8ec16a7f31fbac89b4bf82171083b8db2e`

Analysis was performed **statically** through the configured `jadx-headless-mcp` 0.7.1 JAR, running as an MCP Streamable-HTTP service on localhost. It loaded 7,797 classes and 3,029 resources. The APK was not installed or launched.

The REA wrapper itself still does not inherit `REA_JADX_MCP_JAR`; that wrapper limitation does not affect this report because the configured JADX MCP JAR was used directly for all APK queries.

## Observed artifact identity

| Field | Observed value |
| --- | --- |
| Package | `com.vivo.walkietalkie` |
| Version | `1.2.0.16.0` (`versionCode=120160`) |
| SDK range | min 35, target 37 |
| Launcher | `com.vivo.walkietalkie.HomePage` |
| Main feature host | `HomePage2` supports Vivo HOME, OPEN_FEATURE and START_SOS actions |

## Observed feature surface

| Area | Static evidence |
| --- | --- |
| Nearby discovery | `DiscoverFragment`, Bluetooth scan/advertise/connect permissions, a `BluetoothAdvertiseReceiver`, and permission gating for location plus Bluetooth Scan/Advertise/Connect. |
| Intercom session | Dedicated panel, request/receive, unlock, flip-receive, device list, and message-list activities. |
| Connect request | `BluetoothConnectReceiver` accepts `DEVICE_CONNECT_REQUEST`, reads `did` and `connected_cost_time`, and calculates a remaining deadline capped at 30 seconds before dispatching connection work. |
| Audio | `IntercomAudioManager` is represented by obfuscated `qh.h`: it owns `AudioRecord`, `AudioTrack`, audio-device callback, AudioManager, SoundPools and an optional `GainProc`. |
| Audio format | The manager creates `AudioRecord(7, 8000, 16, 2, buffer)`; the numeric setup corresponds to a voice-oriented input source, 8 kHz, mono channel mask and PCM 16-bit encoding. The symbol mapping is an inference from Android constants; the constructor values are directly observed. |
| Gain processing | `GainProc` loads native library `native-gain`; its `nativeProcess(byte[], int)` is used as a native audio-processing boundary. It is enabled only for the observed `MT6991(ENG)` SoC condition. |
| Playback/mute | Audio playback is driven through `AudioTrack`; mute sets track volume to `0.0`, otherwise `1.0`. Audio history files can be deleted from the app files directory. |
| Background/screen-off | `ForegroundService` declares microphone plus connected-device foreground types. Its start path handles floating and screen-off intercom registration, listens for start/stop recording, location updates and screen-off intercom actions, and refuses microphone operation without the required runtime grants. |
| Screen-off control | Localized UI text says press and hold Volume Up to talk while the display is off, with constraints during music playback or blocked IR sensor operation. |
| SOS | `SendSOSActivityKt`, `SOSNotificationWorker`, a lock-screen receive activity, and `SOS_ADVERTISE_RECEIVED` receiver are present. The receiver routes the SOS advertisement event to application-managed work. |
| Map/location | The discover surface contains a `VivoMapView`; `User` records latitude, longitude and location timestamp. `VOfflineMapActivity`, `MapService` and `MapDownloadReceiver` implement offline-map management and pause/continue download actions. |
| Messages/history | `Messages` records payload, type, title, device ID(s), timestamp, duration, read/deleted state, send-success status and message key. `DraftInfo` supports saved message drafts. |
| Device state | `User` records connection/intercom state, online state, signal strength, battery, screen state, relay flag, SOS timestamp, location and deletion/read flags. |
| Transfer/backup | An exported Vivo EasyShare service is declared for a `WalkieTalkieBackupFile`; a backup service is also declared. |

## Component and permission observations

The manifest declares Bluetooth Scan, Advertise and Connect; coarse/fine/background location; microphone; notifications; foreground microphone and connected-device services; wakelock; vibration; and internet/network-state access. Several further permissions are privileged Vivo/system capabilities. Those privileged entries cannot be reproduced by a normal Play-distributed application and should not be copied.

Important app-owned components include:

- `IntercomPanelActivity`, `IntercomMessageListActivity`, `ReceiveIntercomReqActivity`, `UnlockReceiveReqActivity`, and `FlipReceiveActivity`.
- `ForegroundService` for microphone/connected-device work.
- `MapService` and `VOfflineMapActivity` for map downloads and map UI.
- `BluetoothAdvertiseReceiver` and `BluetoothConnectReceiver` for advertised SOS and device-connect events.
- Notification actions for disconnect, sound on/off, and offline map download pause/continue.

## Practical design takeaways for this project

These are independent product ideas, not a request to copy Vivo code or wire protocol:

1. Treat discovery and connection as an explicit state machine with a visible deadline, cancellation and recoverable failure state. The observed 30-second budget provides a useful UX constraint.
2. Maintain durable per-peer state: last seen, connection state, signal, battery, location timestamp, SOS timestamp and message delivery/read state.
3. Make microphone use resilient by keeping audio, BLE and screen-off control in a foreground service only while an intercom session needs it.
4. Separate live audio from persisted voice-message/history records. A mute action should suppress live playback without destroying history.
5. Make location sharing opt-in and visibly stale when no recent timestamp exists.
6. Treat offline-map download as a separate, network-dependent feature with progress and pause/resume, not as a prerequisite for nearby voice transport.

## Boundaries and remaining unknowns

- Static code proves class/component presence and the cited control flow; it does **not** prove end-to-end behavior on two devices.
- The on-air advertisement payload, BLE GATT/L2CAP service UUIDs, packet framing, authentication, encryption, retries, and full connection coroutine are obscured in non-app package classes and were not inferred.
- No claim is made that this project can interoperate with Vivo Walkie-Talkie. Protocol compatibility would require authorized packet-level observation and controlled tests.
- Vivo system/privileged APIs and its EasyShare/backup integration are device-vendor features, not portable Android app functionality.
