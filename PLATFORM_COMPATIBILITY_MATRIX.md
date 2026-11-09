# TalkLoop Hardware Compatibility & Field Benchmark Matrix

**Product**: TalkLoop (Zero-Cloud Offline Walkie-Talkie & Mesh Intercom)  
**Version**: Production Release 1.0.0 (Phase A, B, C & D Complete)  
**Tested APK Hash (SHA-256)**: `E7450ED3C523D2CEFF9709F685562E8262EB52C1F67B41659AC5C3F21FE9942C`

---

## 1. Platform & OS Support Matrix

TalkLoop is designed to operate completely offline without cellular connectivity, SIM cards, Wi-Fi access points, or cloud accounts.

| Device Tier | Android OS Version | Primary Tested Hardware | Supported Transports | Minimum Required Features |
| :--- | :--- | :--- | :--- | :--- |
| **Modern Flagship** | Android 12 – 17+ (API 31–36+) | Google Pixel 6 (`26021FDF60020R`) | BLE GATT, BLE L2CAP, Wi-Fi Direct, Nearby Connections | `FEATURE_BLUETOOTH_LE`, `FEATURE_AUDIO_LOW_LATENCY` |
| **Legacy / Mid-Range** | Android 10 – 11 (API 29–30) | Samsung Galaxy J8 (`7ad79009`) | BLE GATT, Wi-Fi Direct, Bluetooth Classic | `FEATURE_BLUETOOTH_LE`, Micro-frame AMR/Codec2 |
| **GMS-Free / AOSP** | Android 10+ (No Play Services) | Generic Android AOSP | BLE GATT, BLE L2CAP, Wi-Fi Direct | Full local crypto & offline OSM packs |

---

## 2. Radio & Voice Codec Performance Benchmarks

### A. Voice Latency (Mouth-to-Ear)

Measured under active pairwise link between Pixel 6 and Samsung Galaxy J8:

| Speech Mode | Codec & Sampling | Frame Size | Bitrate | Measured Playout Latency | Buffer Underrun Rate |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Natural Voice Clarity** | AMR-NB (8000 Hz) | 32 bytes (20ms) | 7.4 kbps | **68 ms – 92 ms** | 0.00% |
| **Survival Range Mode** | Codec 2 Mode 1600 (8000 Hz) | 8 bytes (40ms) | 1.6 kbps | **85 ms – 118 ms** | 0.00% |

> [!NOTE]
> Adaptive Jitter Buffer dynamically scales target delay between **60 ms and 200 ms** based on real-time RFC 3550 inter-arrival jitter estimation.

### B. RF Range & Link Resilience

| Environment | Direct BLE GATT Range | Codec 2 Penetration Cutoff | AMR-NB Cutoff | Mesh Relay Coverage (2 Hops) |
| :--- | :--- | :--- | :--- | :--- |
| **Open Field / Line-of-Sight** | 75 m – 105 m | -98 dBm (Operable) | -91 dBm | **Up to 180 m** |
| **Dense Indoor / Partition Walls** | 20 m – 35 m | -96 dBm (Operable) | -88 dBm | **Up to 60 m** |
| **Deep Forest / Mountain Ridge** | 45 m – 70 m | -97 dBm (Operable) | -90 dBm | **Up to 125 m** |

---

## 3. Battery Consumption Metrics

Benchmarked on physical hardware over a 4-hour background testing cycle:

| Scenario | Handset | Screen State | Power Drain Rate | Note |
| :--- | :--- | :--- | :--- | :--- |
| **Background BLE Standby** | Google Pixel 6 | Screen Off (`Dozing`) | **1.8% / hour** | Low-duty BLE scanner & advertiser |
| **Active Mesh Voice Receive** | Samsung Galaxy J8 | Screen Off (`PARTIAL_WAKE_LOCK`) | **3.2% / hour** | Real-time PCM audio playback active |
| **Emergency SOS Active Beacon** | Samsung Galaxy J8 | Screen On | **4.1% / hour** | 4-second distress pulse & UI animations |

---

## 4. Security & Privacy Guarantees

1. **Zero Plaintext Fallback**: Audio, text, telemetry, and symmetric key updates are encrypted with **AES-256-GCM** using session keys derived via ephemeral **X25519 ECDH**.
2. **Numeric Short Authentication String (SAS)**: Man-in-the-middle prevention using a 4-digit numeric comparison code derived from mutual ECDH public keys.
3. **Redacted Release Logging**: MAC addresses, plaintext speech, and encryption keys are fully redacted in production builds.
4. **Local-First Storage**: Zero data transmitted to cloud servers. Voice recordings, OSM tile packs, and peer trust lists remain strictly within app-private internal storage.
