#!/bin/sh
# Builds a DEBUG-signed APK from android/ + core/ with the tools available in Termux
# (aapt2, javac, d8 or dx, apksigner, zip, keytool; zipalign recommended).
#
# STATUS: written but NEVER RUN against a real SDK (the authoring environment had no android.jar/aapt2).
#         Expect to fix small problems on the first real run. Nothing here installs packages or edits
#         existing files; output goes to build/apk/ and a debug keystore under ~/.nexora/.
#
# Required:  ANDROID_JAR=/path/to/android.jar   (API 24 or newer platform jar)
# Optional:  APP_ID=com.nexora.desktop.dev  TARGET_SDK=34  VERSION=0.1.0-dev
# Usage:     ANDROID_JAR=... sh tools/build_apk_termux.sh
#            TARGET_SDK=28 ... builds the variant used to compare exec-from-private-dir (decision E1/E2).
set -u
ROOT=$(cd "$(dirname "$0")/.." && pwd); cd "$ROOT" || exit 3
APP_ID="${APP_ID:-com.nexora.desktop.dev}"
TARGET_SDK="${TARGET_SDK:-34}"
VERSION="${VERSION:-0.1.0-dev}"
OUT="$ROOT/build/apk-$TARGET_SDK"

die() { echo "ERROR: $*" >&2; exit 2; }
need() { command -v "$1" >/dev/null 2>&1 || die "missing tool: $1 (nothing was installed or changed)"; }

[ -n "${ANDROID_JAR:-}" ] || die "set ANDROID_JAR to an android.jar file (API 24+). Example: ANDROID_JAR=\$HOME/android.jar sh tools/build_apk_termux.sh"
[ -f "$ANDROID_JAR" ] || die "ANDROID_JAR does not exist: $ANDROID_JAR"
case "$APP_ID" in *[!a-zA-Z0-9._]*|"") die "bad APP_ID";; esac
case "$TARGET_SDK" in *[!0-9]*|"") die "bad TARGET_SDK";; esac
need aapt2; need zip; need keytool; need apksigner
if command -v javac >/dev/null 2>&1; then JAVAC=javac; else die "missing tool: javac"; fi
if command -v d8 >/dev/null 2>&1; then DEXER=d8; elif command -v dx >/dev/null 2>&1; then DEXER=dx; else die "missing tool: d8 or dx"; fi
if ! command -v zipalign >/dev/null 2>&1; then
  [ "$TARGET_SDK" -ge 30 ] && echo "WARNING: zipalign not found; with TARGET_SDK>=30 the APK may be rejected at install (resources.arsc alignment)."
fi

echo "== 1/7 core (compile + tests)"; sh tools/build_core.sh >/dev/null 2>&1 || die "core build/tests failed; run: sh tools/build_core.sh"

mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex" || exit 3
echo "== 2/7 manifest"
sed -e "s|@VERSION@|$VERSION|g" -e "s|@TARGET_SDK@|$TARGET_SDK|g" android/AndroidManifest.xml.template > "$OUT/AndroidManifest.xml" || exit 3

echo "== 3/7 resources (aapt2 compile + link)"
aapt2 compile --dir android/res -o "$OUT/res.zip" || die "aapt2 compile failed"
aapt2 link -o "$OUT/base.apk" -I "$ANDROID_JAR" --manifest "$OUT/AndroidManifest.xml" \
  --rename-manifest-package "$APP_ID" --min-sdk-version 24 --target-sdk-version "$TARGET_SDK" \
  --java "$OUT/gen" "$OUT/res.zip" || die "aapt2 link failed"

echo "== 4/7 compile Java (Java 8 bytecode)"
find android/src "$OUT/gen" -name '*.java' > "$OUT/src.list"
$JAVAC -source 8 -target 8 -encoding UTF-8 -Xlint:-options -cp "$ANDROID_JAR:build/main" -d "$OUT/classes" @"$OUT/src.list" || die "javac failed"

echo "== 5/7 dex"
(cd "$OUT/classes" && zip -qr ../app.jar .) || die "zip failed"
(cd build/main && zip -qr "$OUT/core.jar" .) || die "zip failed"
if [ "$DEXER" = d8 ]; then
  d8 --release --min-api 24 --lib "$ANDROID_JAR" --output "$OUT/dex" "$OUT/app.jar" "$OUT/core.jar" || die "d8 failed"
else
  dx --dex --min-sdk-version=24 --output="$OUT/dex/classes.dex" "$OUT/app.jar" "$OUT/core.jar" || die "dx failed (lambdas are not allowed in this project)"
fi
[ -f "$OUT/dex/classes.dex" ] || die "no classes.dex produced"

echo "== 6/7 package"
(cd "$OUT/dex" && zip -q "$OUT/base.apk" classes.dex) || die "adding classes.dex failed"
if command -v zipalign >/dev/null 2>&1; then
  zipalign -f -p 4 "$OUT/base.apk" "$OUT/aligned.apk" || die "zipalign failed"
else
  cp "$OUT/base.apk" "$OUT/aligned.apk"
fi

echo "== 7/7 sign (DEBUG key, not for release)"
KS="$HOME/.nexora/debug.keystore"
if [ ! -f "$KS" ]; then
  mkdir -p "$HOME/.nexora" && keytool -genkeypair -keystore "$KS" -storepass android -keypass android -alias nexoradebug \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Nexora Debug" >/dev/null 2>&1 || die "keytool failed"
fi
apksigner sign --ks "$KS" --ks-pass pass:android --key-pass pass:android --ks-key-alias nexoradebug \
  --out "$OUT/nexora-debug.apk" "$OUT/aligned.apk" || die "apksigner failed"
apksigner verify "$OUT/nexora-debug.apk" >/dev/null 2>&1 && echo "signature verifies"
echo "APK: $OUT/nexora-debug.apk"
command -v sha256sum >/dev/null 2>&1 && sha256sum "$OUT/nexora-debug.apk"
echo "applicationId=$APP_ID targetSdk=$TARGET_SDK (temporary id; final id is still undecided)"
