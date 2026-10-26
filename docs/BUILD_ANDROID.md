# Building the APK (not yet done by anyone)

1. Core tests (no SDK needed): `sh tools/build_core.sh`
2. Type-check of the Android layer against hand-written stubs: `sh tools/check_android_layer.sh`
   (catches our own mistakes only; real API differences can still exist)
3. Real APK in Termux: needs an `android.jar` (API 24+). Then
   `ANDROID_JAR=/path/android.jar sh tools/build_apk_termux.sh`
   and for the exec-from-private-storage comparison (decision E1/E2):
   `TARGET_SDK=28 ANDROID_JAR=... sh tools/build_apk_termux.sh` (install both, run Diagnostics in each).

The applicationId is temporary (`com.nexora.desktop.dev`) and must not be treated as final.
How to obtain android.jar inside Termux is UNVERIFIED; it can be copied from an Android SDK platform folder.
