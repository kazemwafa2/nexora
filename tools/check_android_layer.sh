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
import re,sys,os,xml.etree.ElementTree as ET
root=sys.argv[1]; res=os.path.join(root,'android/res'); problems=[]
# 1) every XML must be well-formed (aapt2 stops on the first broken file)
xmls=[]
for dp,dn,fn in os.walk(res):
    for f in fn:
        if f.endswith('.xml'): xmls.append(os.path.join(dp,f))
for x in xmls:
    try: ET.parse(x)
    except ET.ParseError as e: problems.append("not well-formed XML: %s (%s)"%(os.path.relpath(x,root),e))
tpl=open(os.path.join(root,'android/AndroidManifest.xml.template'),encoding='utf-8').read()
for k,v in (('@VERSION@','0.0.0'),('@TARGET_SDK@','34')): tpl=tpl.replace(k,v)
left=re.findall(r'@[A-Z_]+@',tpl)
if left: problems.append("manifest template has placeholders the build script does not replace: %s"%sorted(set(left)))
try: ET.fromstring(tpl.encode('utf-8'))
except ET.ParseError as e: problems.append("manifest template is not well-formed: %s"%e)
# 2) vector path data must only contain legal path characters
for x in xmls:
    if os.sep+'drawable'+os.sep in x:
        for pd in re.findall(r'pathData="([^"]*)"',open(x,encoding='utf-8').read()):
            if not re.fullmatch(r'[MmLlHhVvCcSsQqTtAaZz0-9eE+\-., \n\t]+',pd): problems.append("bad pathData in "+os.path.relpath(x,root))
# 3) string rules that aapt2 enforces
def strings(f):
    t=ET.parse(f).getroot(); return [(e.get('name'),''.join(e.itertext())) for e in t.findall('string')]
ids={'string':[], 'color':[]}
base=None
for loc in ('values','values-fa','values-fa-rAF'):
    f=os.path.join(res,loc,'strings.xml')
    try: items=strings(f)
    except ET.ParseError: continue
    for name,val in items:
        if re.search(r"(?<!\\)'",val): problems.append("%s/%s: unescaped apostrophe (aapt2 error); write \\' or use a typographic ’"%(loc,name))
        if val[:1] in ('@','?'): problems.append("%s/%s: value starts with %s (aapt2 reads it as a reference)"%(loc,name,val[:1]))
        specs=re.findall(r'%(?!%)(?:(\d+)\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z]',val)
        if len(specs)>1 and any(not sp for sp in specs): problems.append("%s/%s: several non-positional format specifiers (aapt2 error)"%(loc,name))
    names=[n for n,_ in items]
    if len(names)!=len(set(names)): problems.append(loc+": duplicate string names")
    if base is None: base=set(names); ids['string']=names
    elif set(names)!=base: problems.append("STRING MISMATCH in %s missing: %s extra: %s"%(loc,sorted(base-set(names)),sorted(set(names)-base)))
try:
    t=ET.parse(os.path.join(res,'values/colors.xml')).getroot()
    ids['color']=[e.get('name') for e in t.findall('color')]
except ET.ParseError:
    pass   # already reported above as not well-formed
if problems:
    print("RESOURCE PROBLEMS:"); [print(" -",p) for p in problems]; sys.exit(1)
out=['package com.nexora.desktop;','public final class R {']
n=0x7f010000
for kind in ('string','color'):
    out.append('  public static final class %s {'%kind)
    for name in ids[kind]:
        n+=1; out.append('    public static final int %s = 0x%x;'%(name,n))
    out.append('  }')
out.append('}')
open(os.path.join(root,'build/android_check/gen/com/nexora/desktop/R.java'),'w').write('\n'.join(out)+'\n')
print("resources OK: %d xml files well-formed, %d strings identical across 3 locales"%(len(xmls),len(base)))
PY
find android-stubs -name '*.java' > "$OUT/stubs.list"
$JAVAC -source 8 -target 8 -Xlint:-options -d "$OUT/stubs" @"$OUT/stubs.list" || { echo "STUBS DO NOT COMPILE"; exit 1; }
find android/src "$OUT/gen" -name '*.java' > "$OUT/app.list"
$JAVAC -source 8 -target 8 -Xlint:all,-options -Werror -cp "$OUT/stubs:build/main" -d "$OUT/app" @"$OUT/app.list" || { echo "ANDROID LAYER DOES NOT TYPE-CHECK"; exit 1; }
if grep -rlE 'LambdaMetafactory|StringConcatFactory' "$OUT/app" >/dev/null 2>&1; then echo "lambda/indy found in android layer"; exit 1; fi
echo "android layer type-checks against hand-written stubs (NOT the real SDK)"
