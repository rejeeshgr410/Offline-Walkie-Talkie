# Walkie-Talkie (2).apk — static-analysis readiness report

## Scope

This report concerns only the supplied artifact:

`C:\Users\reji2\Downloads\Walkie-Talkie (2).apk`

No copy of the APK was installed or launched.

## Artifact identity

| Field | Value |
| --- | --- |
| Size | 18,168,480 bytes |
| Last write | 2026-11-10 22:57:29 |
| SHA-256 | `259f3029cd2e72da466e6fe6cbd631bebf553da8016127b0a34215514c894680` |
| First bytes | `50 4B 03 04` (`PK..`, a ZIP local-file header) |

## Blocking integrity observation

The artifact is not a readable APK/ZIP container. Both the .NET ZIP reader and JADX 1.5.6 reject it with an end-of-central-directory / ZIP-opening error. Its final bytes do not contain a valid ZIP end-of-central-directory record.

Consequently, its Android manifest, DEX code, resources, signing information, native libraries, components, permissions, audio pipeline, and BLE protocol cannot be extracted reliably. Treating partial local headers as a complete application would create unsound conclusions.

## JADX MCP setup

The required `jadx-headless-mcp` 0.7.1 component has been installed at:

`C:\Reji\Android Apps\Offline Walkie Talkie\scratch\tools\jadx-headless-mcp-0.7.1-all.jar`

Its SHA-256 was verified against the upstream release digest:

`6e5eacf500b64292bfb73c49797c1958f6ee44646e43e868039ae7feb573ff75`

The user environment variable `REA_JADX_MCP_JAR` now points to that exact path. The currently running REA service predates this setting and cannot reload its process environment, so a Codex/REA restart is required before REA's Android commands can use it.

## Next required input

Provide a complete re-downloaded/exported copy of `Walkie-Talkie (2).apk`, preferably accompanied by its source SHA-256. Verify it before analysis with:

```powershell
Add-Type -AssemblyName System.IO.Compression.FileSystem
$apk = 'C:\path\to\Walkie-Talkie (2).apk'
$zip = [System.IO.Compression.ZipFile]::OpenRead($apk)
$zip.Entries.Count
$zip.Dispose()
```

Only when that opens successfully can a deep static analysis begin.

## Valid reference copy retrieved from the connected Vivo

On 2026-11-11, a separate, valid reference APK was pulled from the connected Vivo device (`V2514`) rather than replacing the invalid download:

| Field | Value |
| --- | --- |
| Package | `com.vivo.walkietalkie` |
| Version | `1.2.0.16.0` (`versionCode=120160`) |
| Device path | `/system/app/WalkieTalkie/WalkieTalkie.apk` |
| Local copy | `reference-apks/vivo-x300-pro/Vivo-WalkieTalkie-1.2.0.16.0.apk` |
| Size | 23,675,250 bytes |
| SHA-256 | `2ad3164ec83dacf9d3a0d1a2c6a91d8ec16a7f31fbac89b4bf82171083b8db2e` |
| Integrity | Valid ZIP; 3,029 entries, one DEX file, four native libraries |

The remote and pulled-copy SHA-256 values matched exactly. This is the suitable artifact for the requested deep static analysis after REA is restarted.
