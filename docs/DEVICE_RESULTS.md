# Real-device results (only what was actually observed)

Source: screenshots sent by the developer (not produced by an automated run of mine).

## 2026-10-26, Nexora debug APK on the developer's phone (Samsung SM-A075F; Android version from the earlier probe: 16)

| Observation | Evidence | Note |
|---|---|---|
| APK installs and opens without crashing | screenshots 1-4 | build origin/targetSdk of THAT APK unknown at the time; v0.3 now prints targetSdk in Diagnostics |
| Desktop, icons, Files / Settings / Diagnostics windows render and respond | screenshots 1-4 | |
| Diagnostics: PASS 2, FAIL 1, UNKNOWN 5; ANDROID.EXEC_PRIVATE = FAIL; tier STANDARD, Advanced blocked (6) | screenshot 3 | the failure reason text was not visible; v0.3 shows reasons |
| Taskbar overlapped by the system navigation bar; window buttons drawn under it | screenshots 1-4 | fixed in 0.3 by applying window insets (needs re-check on device) |
| Icon letters off-centre | screenshot 1 | fixed in 0.3 (centred) |
| Diagnostics list clipped ("Share report" half visible), no scrolling | screenshot 3 | fixed in 0.3 (drag to scroll, taller default window) |

## Reference screenshots of two other programs (design inspiration only, no code or assets are used)

* Windows-7 style simulator: boot screen, login screen (user tile with the developer's name), desktop with tray
  (flag, signal, battery, volume, clock and date), "Computer" window with drives C:, A: (Android) and Z:.
* A Windows-style file manager: drive tree (C:, D:, E:, X:, Z:), toolbar (Up, Copy, Cut, Paste, Delete,
  New Folder, New File), columns Name / Type / Size / Date, item counter, and a floating media-player window
  that appeared after opening an .mp4. Whether that player is a real Windows program or an Android component
  cannot be told from the screenshots.
