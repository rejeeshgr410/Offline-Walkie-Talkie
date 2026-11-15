# TalkLoop Hardware Compatibility & Field Benchmark Matrix

**Product**: TalkLoop (Zero-Cloud Offline Walkie-Talkie & Mesh Intercom)  
**Version**: Engineering compatibility target — not a production qualification record
**Tested APK Hash (SHA-256)**: `AA813DF7FCA15208D072749C18055166ABBC80C9772C761E76BE0EBF59F51BCC` (production-package signed release artifact, 2026-11-12)

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

These are target formats. Current build latency and loss require fresh two-device measurement:

| Speech Mode | Codec & Sampling | Frame Size | Bitrate | Measured Playout Latency | Buffer Underrun Rate |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Natural Voice Clarity** | AMR-NB (8000 Hz) | 32 bytes (20ms) | 7.4 kbps | Measure during release testing | Measure during release testing |
| **Survival Range Mode** | Codec 2 Mode 1600 (8000 Hz) | 8 bytes (40ms) | 1.6 kbps | Measure during release testing | Measure during release testing |

> [!NOTE]
> The jitter buffer has a software target policy, but latency and loss values in this matrix need release-candidate measurements.

### B. RF Range & Link Resilience

| Environment | Direct BLE GATT Range | Codec 2 Penetration Cutoff | AMR-NB Cutoff | Mesh Relay Coverage (2 Hops) |
| :--- | :--- | :--- | :--- | :--- |
| **Open Field / Line-of-Sight** | To be measured | To be measured | To be measured | No range claim before field testing |
| **Dense Indoor / Partition Walls** | To be measured | To be measured | To be measured | No range claim before field testing |
| **Deep Forest / Mountain Ridge** | To be measured | To be measured | To be measured | No range claim before field testing |

---

## 3. Battery Consumption Metrics

Battery metrics require a release-candidate background soak on each supported hardware class:

| Scenario | Handset | Screen State | Power Drain Rate | Note |
| :--- | :--- | :--- | :--- | :--- |
| **Background BLE Standby** | Representative Android device | Screen Off (`Dozing`) | Measure during release testing | Low-duty BLE scanner & advertiser |
| **Active Mesh Voice Receive** | Representative Android device | Screen Off (`PARTIAL_WAKE_LOCK`) | Measure during release testing | Real-time audio playback active |
| **Emergency SOS Active Beacon** | Representative Android device | Screen On | Measure during release testing | Distress beacon and UI active |

---

## 4. Security & Privacy Guarantees

1. **Zero Plaintext Fallback**: Audio, text, telemetry, and symmetric key updates are encrypted with **AES-256-GCM** using session keys derived via ephemeral **X25519 ECDH**.
2. **Numeric Short Authentication String (SAS)**: Man-in-the-middle prevention using a 4-digit numeric comparison code derived from mutual ECDH public keys.
3. **Redacted Release Logging**: MAC addresses, plaintext speech, and encryption keys are fully redacted in production builds.
4. **Local-First Storage**: Zero data transmitted to cloud servers. Voice recordings, OSM tile packs, and peer trust lists remain strictly within app-private internal storage.
