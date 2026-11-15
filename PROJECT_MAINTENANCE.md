# Project maintenance

## Folder layout

- `app/src`: application source and automated tests.
- `gradle`, wrapper scripts and build configuration: reproducible build setup.
- `.github`: repository automation.
- `reference-apks`: local reference inputs; excluded from Git.
- `.test-artifacts`: local device captures and diagnostic evidence; excluded from Git.
- `.codex-remote-attachments`: original user attachments; excluded from Git.
- `app/build/outputs`: generated installable artifacts. Use these instead of stale APK copies at the project root.

Signing files and `local.properties` remain local and must stay out of Git.

## Recoverable cleanup: 2026-11-15

Moved 91 untracked root entries (old screenshots, exported APKs and the scratch
investigation directory) to:

`C:\Reji\Android Apps\Offline Walkie Talkie Archive\cleanup-2026-11-15`

No source, Git history, signing files, original attachments or current build
outputs were deleted. The archive retains each original filename. A file can be
restored by moving it back to the project root; restore scratch tools before
running historical scripts that refer to `scratch`.

## Validation

Run the following from PowerShell in the project root:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug -PqaInstall=true
```

`-PqaInstall=true` produces the separate `.qa` application ID so device tests
preserve production app data. Release builds must omit this flag.

Physical acceptance evidence and outstanding field tests are tracked in
`PHYSICAL_TEST_PLAN.md`. A successful build does not replace radio, acoustic,
or multi-phone field acceptance.

### Cleanup regression evidence (2026-11-15)

- 155 unit tests passed, with zero failures or errors.
- Fresh QA APK installed and launched on Pixel 6 and Samsung J8; both logs
  reported one authenticated peer after trusted reconnect.
- Pixel delayed foreground-service creation while locked, then promoted the
  service after unlock and return to the activity (`isForeground=true`,
  `startForegroundCount=1`, types `0x90`).
- Failed foreground promotion now stops the service before taking a wake lock.
  The service no longer requests a sticky restart with no restored session.
