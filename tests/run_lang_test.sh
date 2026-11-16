#!/usr/bin/env bash
# Compile tests/lang/*.java against the runtime, run on the host JVM and on
# J2ME4iOS, and diff the outputs.
#   usage: tests/run_lang_test.sh [path/to/j4i(.exe)]
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
B="$ROOT/build"
J4I="${1:-$B/j4i.exe}"
OUT="$B/tests/lang"
rm -rf "$OUT" && mkdir -p "$OUT"
javac -nowarn -Xlint:-options -source 8 -target 8 -encoding UTF-8 \
  -bootclasspath "$B/j4i-runtime.jar" -d "$OUT" "$ROOT"/tests/lang/*.java
(cd "$OUT" && jar cf ../langtest.jar *.class)
fail=0
for cls in $(cd "$ROOT/tests/lang" && ls *.java | sed 's/\.java$//'); do
  java -Dstdout.encoding=UTF-8 -cp "$OUT" "$cls" 2>&1 | tr -d '\r' > "$B/tests/$cls.expected"
  "$J4I" --headless --main "$cls" "$B/tests/langtest.jar" 2>&1 | tr -d '\r' \
    | sed -E 's/^\[ *[0-9.]+\] //' | grep -v '^J2ME4iOS:' > "$B/tests/$cls.actual" || true
  if diff "$B/tests/$cls.expected" "$B/tests/$cls.actual" > "$B/tests/$cls.diff"; then
    echo "PASS $cls"
  else
    echo "FAIL $cls"; head -20 "$B/tests/$cls.diff"; fail=1
  fi
done
exit $fail
