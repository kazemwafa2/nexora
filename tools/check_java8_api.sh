#!/bin/sh
# Fails if main sources use language/API features that break on old Android (minSdk 24) or Termux dx.
# Lambdas/method references are checked on the compiled classes by build_core.sh (invokedynamic).
SRC="${1:-core/src/main/java}"
bad=0
check() { # description, extended-regex
  hits=$(grep -rnE "$2" "$SRC" --include=*.java | grep -vE '^\S+:[0-9]+:\s*(//|\*|/\*)')
  if [ -n "$hits" ]; then echo "FORBIDDEN ($1):"; echo "$hits"; bad=1; fi
}
check "Java 9+ collection factories" '\b(List|Set|Map)\.(of|copyOf)\('
check "var keyword"                  '(^|[^A-Za-z_])var [A-Za-z_]+ *='
check "Java 11 String API"           '\.(isBlank|strip|stripLeading|stripTrailing|repeat|lines)\('
check "java.nio.file (API 26+)"      'java\.nio\.file'
check "records/sealed/text blocks"   '(^|[^A-Za-z_])(record|sealed) [A-Z]|"""'
check "Optional (API 24+ only)"      'java\.util\.Optional'
check "java.util.stream (API 24+)"   'java\.util\.stream'
check "java.time (API 26+)"          'java\.time\.'
[ "$bad" = 0 ] && echo "java8/android API check: OK"
exit $bad
