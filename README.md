# Nexora Desktop (core) — work in progress

Designer & developer: Kazem Rafiei (Jaghori, Ghazni, Afghanistan).
Tagline: *A World of Possibilities, in Your Pocket.*

Nexora Desktop is an Android desktop environment (Windows-11-inspired) that can OPTIONALLY run real
Windows programs through a separate compatibility engine. The desktop must keep working when the
engine is missing, unsupported or crashes.

## What exists in this repository (verified by `sh tools/build_core.sh`)

`core/` is the platform-independent heart (plain Java 8, no dependencies, no lambdas so it also
builds with Termux `javac` + `dx`):

| package | purpose |
|---|---|
| `capability` | brand-independent device facts + tier classifier (Basic / Standard / Advanced) |
| `diag` | diagnostics runner (crash/timeout-safe), 3-category report (PASS / FAIL / UNKNOWN), redaction |
| `engine` | `IEngine` contract, state machine, error taxonomy, supervisor (contains failures, crash-loop breaker), fake engine for tests |
| `pack` | engine-pack manifest (strict), hash verifier, installer (staging, atomic rename, never overwrites) |
| `window` | pure window-manager model (z-order, move/resize/snap/maximize, rotation reflow) |
| `settings` | validated settings with versioned migrations (en / fa / fa-AF, RTL) |
| `license` | license policy and dependency inventory (`config/dependencies.csv`) |
| `files` | file-manager operations confined to a root: list, new folder, copy, move, delete (no overwrite, no symlink following, root protected) |
| `util` | strict JSON, SHA-256, clock |

## Android layer (`android/`) — written, type-checked only against hand-written stubs

Views-based desktop (wallpaper, icons, windows, Start menu, taskbar), internal apps (Files in app-private
storage, Settings, Diagnostics with a share button, About), English / Persian / Dari strings with RTL,
logo (SVG, VectorDrawables, launcher PNGs). `sh tools/check_android_layer.sh` type-checks it against
`android-stubs/`. It has NOT been compiled against the real SDK and NO APK has been built or installed.
`tools/build_apk_termux.sh` builds the APK once an `android.jar` is supplied (see docs/BUILD_ANDROID.md).

## NOT in this repository yet (honest status)

* A built, installed and tested APK (no Android SDK / network in the authoring environment).
* Real Wine / Box64 integration: open issue NX-001 (Box64 exits with 139 on the test device) and the
  fixed `/data/data/com.winlator` path problem. See docs/ISSUES.md.
* No device or brand has been verified as compatible.

## Build and test (no installs needed beyond a JDK or a JRE with `jdk.compiler`)

    sh tools/build_core.sh      # forbidden-API scan, compile, bytecode check, tests

Everything is written only into `build/`.

## Licenses

Nexora's own license is UNDECIDED. Third-party components and their verification state are in
`config/dependencies.csv` and are enforced by `LicensePolicy`: nothing unverified may ship.
