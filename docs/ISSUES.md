# Open issues and unverified items

| ID | Issue | State |
|---|---|---|
| NX-001 | Box64 0.4.4 starts but the x86_64 test programs exit with 139 (SIGSEGV) on the test device (Termux). Cause unknown. Not a verdict on Box64 or on the project. | OPEN |
| NX-002 | Winlator rootfs binaries contain the fixed prefix `/data/data/com.winlator/files/rootfs` (174 files). Options A-D (rebuild / patch / redirect / separate app) untested. | OPEN |
| NX-003 | Binary execution from app-private storage vs targetSdk (E1 single APK vs E2 two APKs). Needs on-device test T5. | OPEN |
| NX-004 | Licenses: Winlator 11.2 (sources conflict), Wine, glibc, VKD3D, cnc-ddraw, D8VK, Vortek, Gladio not verified. Microsoft DLLs must not be shipped. | OPEN |
| NX-005 | 16 KB memory-page devices vs Box64/Wine: not researched. | OPEN |
| NX-006 | An APK built from an earlier version was installed and ran on the developer's phone (screenshots). The CI workflow and build script have still never been run by their author; v0.3 changes are stub-checked only. | PARTLY OBSERVED |
| NX-008 | Dari (fa-AF) strings are a copy of the Persian text and need review by a native Dari speaker; Persian text also needs review. | OPEN |
| NX-009 | DesktopView draws everything on one Canvas: no scrolling, no text input, no accessibility nodes yet. Real devices may show layout problems. | OPEN |
| NX-007 | Compatibility on any brand/model: nothing verified; one test device only (Samsung SM-A075F, Android 16). | OPEN |
| NX-010 | First on-device Diagnostics: ANDROID.EXEC_PRIVATE = FAIL on the developer's phone; targetSdk of that build unknown. Needs the targetSdk 34 vs 28 comparison (v0.3 prints targetSdk in the report). | OPEN |
| NX-011 | Files window now has a toolbar (Up, +Folder, Copy, Cut, Paste, Delete with confirm). The operations are unit-tested (FileOps), but the window itself was only type-checked against stubs and never run. It works in app-private storage only; shared storage (SAF) is not implemented. | OPEN |
| NX-012 | tools/build_apk_termux.sh was exercised with FAKE aapt2/d8/apksigner/zipalign/keytool shims (success + 8 failure scenarios). Real tools may behave differently. Two real bugs were found this way (placeholder inside a template comment; an XML comment containing two hyphens). | OPEN |
