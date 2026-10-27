#!/bin/sh
# Builds a DEBUG-signed APK from android/ + core/ using Android build-tools style commands
# (aapt2, javac, d8 or dx, zipalign, apksigner, zip, unzip, keytool). Works in Termux and on CI.
#
# STATUS: logic tested with fake tool shims only; NEVER yet run against a real SDK.
#
# Required:  ANDROID_JAR=/path/to/android.jar    (platform jar, API 24 or newer)
# Optional:  APP_ID=com.nexora.desktop.dev  TARGET_SDK=34  VERSION=0.1.0-dev
# Output:    build/apk-<TARGET_SDK>/nexora-debug.apk  and  build/apk-<TARGET_SDK>/BUILD_STATUS
#            Trust the APK only if BUILD_STATUS starts with "OK". Every run uses a NEW work folder,
#            so leftovers of an earlier failed run can never leak into a new build. Nothing is deleted.
set -u
ROOT=$(cd "$(dirname "$0")/.." && pwd); cd "$ROOT" || exit 3
APP_ID="${APP_ID:-com.nexora.desktop.dev}"
TARGET_SDK="${TARGET_SDK:-34}"
VERSION="${VERSION:-0.1.0-dev}"
OUT="$ROOT/build/apk-$TARGET_SDK"
STAMP=$(date +%Y%m%d-%H%M%S)
WORK="$OUT/work-$STAMP-$$"
MIN_SDK=24

mkdir -p "$OUT" || { echo "cannot create $OUT" >&2; exit 3; }
STATUS="$OUT/BUILD_STATUS"
echo "RUNNING since $STAMP" > "$STATUS"
on_exit() { rc=$?; if [ "$rc" -ne 0 ]; then echo "FAILED (exit $rc) at $(date +%Y%m%d-%H%M%S); any nexora-debug.apk here is from an EARLIER run" > "$STATUS"; fi; }
trap on_exit EXIT

die() { echo "ERROR: $*" >&2; exit 2; }
need() { command -v "$1" >/dev/null 2>&1 || die "missing tool: $1 (nothing was installed or changed). $2"; }

# ---------------------------------------------------------------- preflight
[ -n "${ANDROID_JAR:-}" ] || die "ANDROID_JAR is not set. Point it at a platform android.jar (API $MIN_SDK+), e.g. \$ANDROID_HOME/platforms/android-34/android.jar. In CI the workflow installs it with sdkmanager."
[ -f "$ANDROID_JAR" ] || die "ANDROID_JAR does not exist: $ANDROID_JAR"
case "$APP_ID" in *[!a-zA-Z0-9._]*|"") die "bad APP_ID '$APP_ID'";; esac
case "$TARGET_SDK" in *[!0-9]*|"") die "bad TARGET_SDK '$TARGET_SDK'";; esac
[ "$TARGET_SDK" -ge "$MIN_SDK" ] || die "TARGET_SDK must be >= $MIN_SDK"
need aapt2 "On CI: build-tools are installed by the workflow. In Termux: pkg install aapt2."
need javac ""; need zip ""; need unzip ""; need keytool ""; need apksigner ""
if command -v d8 >/dev/null 2>&1; then DEXER=d8; elif command -v dx >/dev/null 2>&1; then DEXER=dx; else die "missing tool: d8 or dx"; fi

JAR_LIST=$(unzip -l "$ANDROID_JAR" 2>/dev/null) || die "ANDROID_JAR is not a readable zip: $ANDROID_JAR"
echo "$JAR_LIST" | grep -q 'android/app/Activity.class' || die "ANDROID_JAR does not look like a platform android.jar (no android/app/Activity.class): $ANDROID_JAR"
JAR_SDK=$(unzip -p "$ANDROID_JAR" build.prop 2>/dev/null | sed -n 's/^ro\.build\.version\.sdk=//p' | head -n 1)
if [ -n "$JAR_SDK" ]; then
  [ "$JAR_SDK" -ge "$MIN_SDK" ] 2>/dev/null || die "android.jar is API $JAR_SDK, need $MIN_SDK or newer"
  [ "$JAR_SDK" -ge "$TARGET_SDK" ] 2>/dev/null || echo "WARNING: android.jar is API $JAR_SDK but TARGET_SDK=$TARGET_SDK (newer APIs cannot be checked)"
else
  echo "WARNING: could not read the API level from android.jar"
fi
if ! command -v zipalign >/dev/null 2>&1; then
  [ "$TARGET_SDK" -ge 30 ] && echo "WARNING: zipalign not found; with TARGET_SDK>=30 Android may reject the APK (resources.arsc alignment)."
fi
echo "tools: $(javac -version 2>&1 | head -n 1) | $(aapt2 version 2>&1 | head -n 1) | dexer=$DEXER"

# ---------------------------------------------------------------- build
echo "== 1/8 core (forbidden-API scan, compile, tests)"
sh tools/build_core.sh > "$OUT/core-build-$STAMP.log" 2>&1 || { tail -n 30 "$OUT/core-build-$STAMP.log" >&2; die "core build/tests failed (full log: $OUT/core-build-$STAMP.log)"; }

mkdir -p "$WORK/gen" "$WORK/classes" "$WORK/dex" || exit 3

echo "== 2/8 manifest from template"
sed -e "s|@VERSION@|$VERSION|g" -e "s|@TARGET_SDK@|$TARGET_SDK|g" android/AndroidManifest.xml.template > "$WORK/AndroidManifest.xml" || exit 3
if grep -q '@[A-Z_][A-Z_]*@' "$WORK/AndroidManifest.xml"; then
  grep -n '@[A-Z_][A-Z_]*@' "$WORK/AndroidManifest.xml" >&2; die "unreplaced placeholder left in the generated manifest"
fi

echo "== 3/8 resources (aapt2 compile + link)"
aapt2 compile --dir android/res -o "$WORK/res.zip" || die "aapt2 compile failed (see its message above; usually a bad resource file)"
aapt2 link -o "$WORK/base.apk" -I "$ANDROID_JAR" --manifest "$WORK/AndroidManifest.xml" \
  --rename-manifest-package "$APP_ID" --min-sdk-version "$MIN_SDK" --target-sdk-version "$TARGET_SDK" \
  --java "$WORK/gen" "$WORK/res.zip" || die "aapt2 link failed (see its message above)"
[ -s "$WORK/base.apk" ] || die "aapt2 produced no base.apk"
find "$WORK/gen" -name R.java | grep -q . || die "aapt2 produced no R.java"

echo "== 4/8 compile Java (Java 8 bytecode)"
find android/src "$WORK/gen" -name '*.java' > "$WORK/src.list"
javac -source 8 -target 8 -encoding UTF-8 -Xlint:-options -cp "$ANDROID_JAR:build/main" -d "$WORK/classes" @"$WORK/src.list" || die "javac failed on the Android sources (see errors above)"

echo "== 5/8 dex (app + core)"
(cd "$WORK/classes" && zip -qr ../app.jar .) || die "zip app.jar failed"
(cd build/main && zip -qr "$WORK/core.jar" .) || die "zip core.jar failed"
if [ "$DEXER" = d8 ]; then
  d8 --release --min-api "$MIN_SDK" --lib "$ANDROID_JAR" --output "$WORK/dex" "$WORK/app.jar" "$WORK/core.jar" || die "d8 failed"
else
  dx --dex --min-sdk-version="$MIN_SDK" --output="$WORK/dex/classes.dex" "$WORK/app.jar" "$WORK/core.jar" || die "dx failed (lambdas are forbidden in this project)"
fi
[ -s "$WORK/dex/classes.dex" ] || die "no classes.dex produced"

echo "== 6/8 package (+align)"
(cd "$WORK/dex" && zip -q "$WORK/base.apk" classes.dex) || die "adding classes.dex to the apk failed"
if command -v zipalign >/dev/null 2>&1; then
  zipalign -f -p 4 "$WORK/base.apk" "$WORK/aligned.apk" || die "zipalign failed"
else
  cp "$WORK/base.apk" "$WORK/aligned.apk" || exit 3
fi

echo "== 7/8 sign (DEBUG key, not for release) and verify"
KS="$HOME/.nexora/debug.keystore"
if [ ! -f "$KS" ]; then
  mkdir -p "$HOME/.nexora" && keytool -genkeypair -keystore "$KS" -storepass android -keypass android -alias nexoradebug \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Nexora Debug" >/dev/null 2>&1 || die "keytool could not create the debug keystore"
fi
apksigner sign --ks "$KS" --ks-pass pass:android --key-pass pass:android --ks-key-alias nexoradebug \
  --out "$WORK/nexora-debug.apk" "$WORK/aligned.apk" || die "apksigner sign failed"
apksigner verify "$WORK/nexora-debug.apk" || die "apksigner verify failed: the signature is NOT valid"

echo "== 8/8 inspect the final APK"
APK_LIST=$(unzip -l "$WORK/nexora-debug.apk") || die "cannot list the final APK"
for f in classes.dex AndroidManifest.xml resources.arsc; do
  echo "$APK_LIST" | grep -q " $f\$" || die "final APK has no $f"
done
for cls in Lcom/nexora/desktop/MainActivity\; Lcom/nexora/core/engine/IEngine\; Lcom/nexora/core/window/WindowManagerModel\; Lcom/nexora/core/capability/TierClassifier\; ; do
  unzip -p "$WORK/nexora-debug.apk" classes.dex | grep -a -q -F "$cls" || die "class $cls is missing from classes.dex"
done
BADGING=$(aapt2 dump badging "$WORK/nexora-debug.apk" 2>/dev/null || true)
if [ -n "$BADGING" ]; then
  echo "$BADGING" | grep -q "package: name='$APP_ID'" || die "badging: applicationId is not $APP_ID"
  echo "$BADGING" | grep -q "targetSdkVersion:'$TARGET_SDK'" || die "badging: targetSdkVersion is not $TARGET_SDK"
  echo "$BADGING" | grep -q "sdkVersion:'$MIN_SDK'" || { echo "Actual APK badging output:" >&2; echo "$BADGING" >&2; die "badging: minSdkVersion is not $MIN_SDK"; }
else
  echo "WARNING: 'aapt2 dump badging' gave no output; package/sdk values were not cross-checked"
fi

cp -f "$WORK/nexora-debug.apk" "$OUT/nexora-debug.apk" || exit 3
SHA=$(command -v sha256sum >/dev/null 2>&1 && sha256sum "$OUT/nexora-debug.apk" | cut -d' ' -f1 || echo n/a)
echo "OK applicationId=$APP_ID targetSdk=$TARGET_SDK sha256=$SHA work=$WORK" > "$STATUS"
echo "APK: $OUT/nexora-debug.apk"
echo "sha256: $SHA"
echo "applicationId=$APP_ID (temporary; final id is undecided) targetSdk=$TARGET_SDK"
