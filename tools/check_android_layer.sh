#!/bin/sh
# Type-checks android/src against HAND-WRITTEN stubs of the Android API (android-stubs/) and a generated
# R.java. This catches typos and wrong types in OUR code only. It is NOT a build against the real SDK:
# a stub can differ from the real API. The real check is tools/build_apk_termux.sh with a real android.jar.
set -u
ROOT=$(cd "$(dirname "$0")/.." && pwd); cd "$ROOT" || exit 3
OUT="$ROOT/build/android_check"; mkdir -p "$OUT/stubs" "$OUT/app" "$OUT/gen/com/nexora/desktop" || exit 3
[ -d build/main ] || sh tools/build_core.sh >/dev/null || exit 1
if command -v javac >/dev/null 2>&1; then JAVAC="javac"; else JAVAC="java --module jdk.compiler/com.sun.tools.javac.Main"; fi
python3 - "$ROOT" <<'PY' || exit 1
import re,sys,os
root=sys.argv[1]; res=os.path.join(root,'android/res')
ids={'string':[], 'color':[]}
for f in ('values/strings.xml','values/colors.xml'):
    t=open(os.path.join(res,f),encoding='utf-8').read()
    for kind,name in re.findall(r'<(string|color) name="([A-Za-z0-9_]+)"',t): ids[kind].append(name)
# every locale must define exactly the same string names as the default
base=set(ids['string'])
for loc in ('values-fa','values-fa-rAF'):
    t=open(os.path.join(res,loc,'strings.xml'),encoding='utf-8').read()
    names=set(re.findall(r'<string name="([A-Za-z0-9_]+)"',t))
    if names!=base: print("STRING MISMATCH in",loc,"missing:",sorted(base-names),"extra:",sorted(names-base)); sys.exit(1)
out=['package com.nexora.desktop;','public final class R {']
n=0x7f010000
for kind in ('string','color'):
    out.append('  public static final class %s {'%kind)
    for name in ids[kind]:
        n+=1; out.append('    public static final int %s = 0x%x;'%(name,n))
    out.append('  }')
out.append('}')
open(os.path.join(root,'build/android_check/gen/com/nexora/desktop/R.java'),'w').write('\n'.join(out)+'\n')
print("locales have identical string names:", len(base))
PY
find android-stubs -name '*.java' > "$OUT/stubs.list"
$JAVAC -source 8 -target 8 -Xlint:-options -d "$OUT/stubs" @"$OUT/stubs.list" || { echo "STUBS DO NOT COMPILE"; exit 1; }
find android/src "$OUT/gen" -name '*.java' > "$OUT/app.list"
$JAVAC -source 8 -target 8 -Xlint:all,-options -Werror -cp "$OUT/stubs:build/main" -d "$OUT/app" @"$OUT/app.list" || { echo "ANDROID LAYER DOES NOT TYPE-CHECK"; exit 1; }
if grep -rlE 'LambdaMetafactory|StringConcatFactory' "$OUT/app" >/dev/null 2>&1; then echo "lambda/indy found in android layer"; exit 1; fi
echo "android layer type-checks against hand-written stubs (NOT the real SDK)"
