#!/usr/bin/env bash
# Self-checking tests for JSR-75 files, tone synthesis, inflate and vendor APIs.
#   usage: tests/run_io_test.sh [path/to/j4i(.exe)|j4i-headless]
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if command -v cygpath >/dev/null 2>&1; then ROOT="$(cygpath -m "$ROOT")"; fi
B="$ROOT/build"
J4I="${1:-$B/j4i.exe}"
OUT="$B/tests/io"
rm -rf "$OUT" "$B/tests/iodata" && mkdir -p "$OUT"
javac -nowarn -Xlint:-options -source 8 -target 8 -encoding UTF-8 \
  -bootclasspath "$B/j4i-runtime.jar" -d "$OUT" "$ROOT"/tests/io/*.java
(cd "$OUT" && jar cf ../iotest.jar *.class)
fail=0
for cls in IoTest VendorTest; do
  out="$("$J4I" --headless --data "$B/tests/iodata" --main "$cls" "$B/tests/iotest.jar" 2>&1 || true)"
  if echo "$out" | grep -q "ALL OK"; then echo "PASS $cls"; else echo "FAIL $cls"; echo "$out" | grep -v "^ok" | tail -20; fail=1; fi
done
exit $fail
