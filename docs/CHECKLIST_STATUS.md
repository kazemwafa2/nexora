# Status of the technical checklist (what is verified, by whom, and how)

Legend: DONE-SANDBOX = verified in the author's Linux sandbox; DEVICE = seen on the developer's phone
(screenshots); UNVERIFIED = written but never run against the real thing.

| Checklist item | Status |
|---|---|
| Core compiles (Java 8, -Werror) | DONE-SANDBOX |
| Java 8 / Android API restrictions scan | DONE-SANDBOX |
| Core unit tests (93) | DONE-SANDBOX |
| Android layer type-check with hand-written stubs | DONE-SANDBOX (not the real SDK) |
| Resource lint (XML, aapt2 string rules, placeholders, 3 locales) | DONE-SANDBOX, 6 negative cases proven |
| Build script refuses to run without a valid android.jar (exists, is a platform jar, API >= 24) | DONE-SANDBOX with fakes |
| Clear error plus remedy when SDK/tools are missing | DONE-SANDBOX with fakes |
| Manifest generated from the template, leftover placeholder stops the build | DONE-SANDBOX with fakes |
| Rebuild without leftovers (fresh work folder per run, BUILD_STATUS file) | DONE-SANDBOX with fakes |
| classes.dex / manifest / resources.arsc / key classes checked inside the APK | DONE-SANDBOX with fakes (dex content was faked) |
| Signature verification fails the build | DONE-SANDBOX with fakes |
| badging cross-check (applicationId, minSdk, targetSdk) | DONE-SANDBOX with fakes |
| CI: SDK install, ANDROID_HOME/ANDROID_JAR/PATH, tool check, two APKs, verify, artifact only on success | UNVERIFIED (YAML syntax checked only) |
| Real aapt2 / d8 / zipalign / apksigner behaviour | UNVERIFIED |
| Compile against the real SDK | UNVERIFIED |
| Install and open an APK on a real phone without crash | DEVICE (earlier build, screenshots) |
| Android-specific tests (install, start, stop, recover) | NOT DONE |
