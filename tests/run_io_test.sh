#!/usr/bin/env bash
# Self-checking tests: JSR-75 files, tones, MIDI synth, inflate, vendor APIs, JAD and property overrides.
#   usage: tests/run_io_test.sh [path/to/j4i(.exe)|j4i-headless]
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if command -v cygpath >/dev/null 2>&1; then ROOT="$(cygpath -m "$ROOT")"; fi
B="$ROOT/build"
J4I="${1:-$B/j4i.exe}"
OUT="$B/tests/io"
rm -rf "$OUT" "$B/tests/iodata" && mkdir -p "$OUT/classes"
javac -nowarn -Xlint:-options -source 8 -target 8 -encoding UTF-8 \
  -bootclasspath "$B/j4i-runtime.jar" -d "$OUT/classes" "$ROOT"/tests/io/*.java
printf 'MIDlet-Name: PropTest\nMIDlet-Vendor: J2ME4iOS\nMIDlet-Version: 1.0\nMIDlet-1: PropTest, , PropTest\n' > "$OUT/MANIFEST.MF"
(cd "$OUT/classes" && jar cfm ../../iotest.jar "$OUT/MANIFEST.MF" *.class)
printf 'MIDlet-Name: PropTest\nMIDlet-Version: 2.0\nGame-Server: from-jad\nMIDlet-Jar-URL: iotest.jar\n' > "$OUT/iotest.jad"
fail=0
run() {
  local name="$1"; shift
  out="$("$J4I" --headless --data "$B/tests/iodata" "$@" "$B/tests/iotest.jar" 2>&1 || true)"
  if echo "$out" | grep -q "ALL OK"; then echo "PASS $name"; else echo "FAIL $name"; echo "$out" | grep -v "^ok" | tail -20; fail=1; fi
}
run IoTest --main IoTest
run VendorTest --main VendorTest
run PropTest-props --main PropMain --prop microedition.platform=SonyEricssonK800i --prop "my.prop: 42" --font 10,14,22
run PropTest-jad --jad "$OUT/iotest.jad"
exit $fail
