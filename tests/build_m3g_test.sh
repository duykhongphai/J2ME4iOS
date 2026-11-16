#!/usr/bin/env bash
# Builds build/tests/m3gtest.jar (M3G visual test + generated cube.m3g).
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if command -v cygpath >/dev/null 2>&1; then ROOT="$(cygpath -m "$ROOT")"; fi
B="$ROOT/build"
OUT="$B/tests/m3g"
rm -rf "$OUT" && mkdir -p "$OUT/classes" "$OUT/gen"
javac -d "$OUT/gen" "$ROOT/tests/m3g/GenM3G.java"
java -cp "$OUT/gen" GenM3G "$OUT/classes/cube.m3g"
javac -nowarn -Xlint:-options -source 8 -target 8 -encoding UTF-8 -bootclasspath "$B/j4i-runtime.jar" \
  -d "$OUT/classes" "$ROOT/tests/m3g/M3GTest.java"
printf 'MIDlet-Name: M3G Test\nMIDlet-Vendor: J2ME4iOS\nMIDlet-Version: 1.0\nMIDlet-1: M3G, , M3GTest\nMicroEdition-Configuration: CLDC-1.1\nMicroEdition-Profile: MIDP-2.0\n' > "$OUT/MANIFEST.MF"
(cd "$OUT/classes" && jar cfm "$B/tests/m3gtest.jar" "$OUT/MANIFEST.MF" .)
echo "built $B/tests/m3gtest.jar"
