# Vivo Walkie Talkie feature comparison

Reference artifact: `Walkie-Talkie (1).apk`  
SHA-256: `6238673655AAD0C66ACAF31A3A329550200BD788A4E3CBD442E368AD89321F9D`

## Evidence boundary

REA package inventory found an APK with 2,914 ZIP entries and no entry-integrity contradictions (inventory Evidence `ev_5050b0f2268d3f001bf1273cd367e159bfcb88ad699d10ceab0d299fffe81f6a`; summary view `ev_60d020e2ec9b65aeebb6e57ca11457700037870b7638dc7e47ed5253f97c97c1d89c9ce861a5543d`). JADX 0.7.1 identifies package `com.vivo.walkietalkie`, version `1.1.2.15.0` (version code `112150`), min SDK 35 and target SDK 36. REA class-search Evidence: chat `ev_e1308642b3746b0e40c2835ff710221b97dfa8e0d6066e8173f7061eb24a3482`, discovery/SOS `ev_396fb5b27e16729a7bbbc4ef3a6899ad48f52c2144cb3b818e1a904d8da2cc7a`, offline-map classes `ev_f034c78ea7f17b7d7d8ae3d3c596881490c6aea748119648e146b3eb09ba7b39`, settings `ev_fa51aefb983813c8bd4e34f64ae0d0e5086758387b8d57ab6f8f92030a6d2963`, and shared/background services `ev_7751f8f72240f9ac2bd4d658c2fd6e05b4f6362c8ddfddec89cbcdf28041bf58`.

JADX 0.7.1 was installed and used for static DEX inspection. The full archive extraction could not complete because the APK has case-colliding paths (`res/0x.png` and `res/0X.png`) on Windows. Static source review establishes feature families and some UI strings, but does not prove runtime behavior. The Vivo app was not launched for this comparison.

## Feature mapping

| Vivo feature family (static artifact evidence) | TalkLoop source mapping | Status |
|---|---|---|
| Two primary home tabs: Discover and Intercom devices | nearby-device scanner and trusted contacts | Partial. The app has discovery and contacts, but no unified friend/device map view. |
| Bluetooth intercom with request/accept flow and PTT | transport, pairing, floor control and PTT | Similar workflow exists; separate protocol and peer interoperability is not established. |
| Live receive mute, with an explanation that intercom messages remain replayable | active-session mute and recorded voice history/replay | Implemented in this change as receive-playback mute; capture/history and outgoing PTT continue. |
| Intercom device list, connected state, message history and resend/read/delete actions | contacts, messages and voice history/replay | Text messages now expose mark-read, retry for failed outgoing messages, and delete actions. Persistence and Vivo's complete message semantics are not matched. |
| Background foreground service, notification actions, screen-off intercom setting, floating intercom icon | foreground service, notification actions, hardware PTT, floating PTT | Similar capabilities exist in source; Vivo OS policy and screen-off behavior need device verification. |
| Nearby discovery and location sharing, with map/address context and SOS messages | peer location frames, SOS coordinates, GPS radar/compass | A schematic Discover map plots the user's and peers' reported coordinates and freshness. Location protocols are not interoperable by evidence. |
| Location freshness and navigation handoff | added freshness label and external map intent in radar | Implemented in this change; a compatible navigation app is needed. |
| Offline city maps: city/province list, city download list, download progress, notification pause/resume | OpenStreetMap Overpass road-geometry downloader, local storage and vector preview | Partial implementation: user-selected areas (0.2–2 km radius) download as local JSON with progress, pause/cancel, resume by restarting the request, delete, and a basic offline road preview. There is no tile pack, city catalog, place labels, or background download notification. |
| Settings for background intercom, SOS/notification disturbance, receive requests | TalkLoop settings | Incoming legacy peer requests can be allowed or rejected; allowed requests still require manual approval. Disturbance mode silences incoming SOS alarms while leaving alerts visible. |
| Vivo OS features, account/backend integration, proprietary intercom SDK/protocol | Vivo-specific classes and SDK references in the APK | Not portable by copying APK code. The manifest includes Vivo/system permissions unavailable to ordinary third-party apps. |

## Changed source

- `app/src/main/java/com/example/offlinewalkietalkie/voice/VoiceEngine.kt`: suppress live received playback while continuing to collect decoded PCM for voice history.
- `app/src/main/java/com/example/offlinewalkietalkie/ui/WalkieTalkieViewModel.kt`: receive-mute state; outgoing PTT and receive history remain enabled; mute clears on session leave.
- `app/src/main/java/com/example/offlinewalkietalkie/ui/Screens.kt`: active-session live-audio mute control and confirmation explaining history remains replayable.
- `app/src/main/java/com/example/offlinewalkietalkie/ui/RadarScreen.kt`: location freshness label and a navigation-app handoff for a selected peer.
- `app/src/main/java/com/example/offlinewalkietalkie/ui/MapScreens.kt`: schematic Discover map and saved offline-area download manager.
- `app/src/main/java/com/example/offlinewalkietalkie/data/OfflineMapRepository.kt`: bounded OpenStreetMap road extract download and local persistence.
- `app/src/main/java/com/example/offlinewalkietalkie/channel/ReliableMessageManager.kt`: per-peer ACK tracking for reliable message delivery and retry.
- `app/src/main/AndroidManifest.xml`: internet permission for user-initiated map area downloads.

## Remaining work

The APK analysis is static; the Vivo app was not launched, and UI behavior was not observed at runtime. Its manifest declares background location, broad Bluetooth and network capabilities, Vivo permissions, and platform/system privileges. Static declarations do not prove grants or runtime behavior. The exact permission/use mapping and complete minified implementation remain open.

The new offline map feature stores OpenStreetMap road geometry for a small selected area, but does not render it as a map; map tile/vector rendering and a curated city catalog remain future work. The public Overpass service is subject to availability and usage limits. Vivo network/backend and peer-protocol compatibility require protocol documentation or controlled tests using devices/accounts the operator is authorized to use. A Vivo-specific signature or privileged permission cannot be made available to a normal third-party TalkLoop APK just by copying its declaration.
