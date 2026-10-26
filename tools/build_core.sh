#!/bin/sh
# Compile + test the platform-independent core. Works with a real javac, or with a JRE that has the
# jdk.compiler module (java --module jdk.compiler/com.sun.tools.javac.Main). No downloads, no installs.
# Usage: sh tools/build_core.sh      Output: build/ (created; only this folder is written)
set -u
ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT" || exit 3
OUT="$ROOT/build"
mkdir -p "$OUT/main" "$OUT/test" || exit 3

if command -v javac >/dev/null 2>&1; then
  JAVAC="javac"
else
  JAVAC="java --module jdk.compiler/com.sun.tools.javac.Main"
fi
OPTS="-source 8 -target 8 -encoding UTF-8 -Xlint:all,-options -Werror"

echo "== 1/5 forbidden API scan"
sh tools/check_java8_api.sh core/src/main/java || exit 1

echo "== 2/5 compile main (Java 8 bytecode)"
find core/src/main/java -name '*.java' > "$OUT/main.list"
$JAVAC $OPTS -d "$OUT/main" @"$OUT/main.list" || { echo "COMPILE MAIN FAILED"; exit 1; }

echo "== 3/5 bytecode must not use invokedynamic helpers (breaks Termux dx on lambdas)"
if grep -rlE 'LambdaMetafactory|StringConcatFactory' "$OUT/main" >/dev/null 2>&1; then
  echo "FOUND lambda/indy usage in:"; grep -rlE 'LambdaMetafactory|StringConcatFactory' "$OUT/main"; exit 1
fi
echo "OK"

echo "== 4/5 compile tests"
find core/src/test/java -name '*.java' > "$OUT/test.list"
$JAVAC -source 8 -target 8 -encoding UTF-8 -Xlint:-options -cp "$OUT/main" -d "$OUT/test" @"$OUT/test.list" || { echo "COMPILE TESTS FAILED"; exit 1; }

echo "== 5/5 run tests"
CLASSES=$(find core/src/test/java -name '*Test.java' | sed -e 's|core/src/test/java/||' -e 's|\.java$||' -e 's|/|.|g' | sort | tr '\n' ' ')
java -cp "$OUT/main:$OUT/test" com.nexora.testing.TestRunner $CLASSES
