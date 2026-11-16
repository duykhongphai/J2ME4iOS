#!/usr/bin/env bash
# Builds build/tests/mascottest.jar (Mascot Capsule v3 visual test + generated assets).
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if command -v cygpath >/dev/null 2>&1; then ROOT="$(cygpath -m "$ROOT")"; fi
B="$ROOT/build"
OUT="$B/tests/mascot"
rm -rf "$OUT" && mkdir -p "$OUT/classes" "$OUT/gen"
javac -d "$OUT/gen" "$ROOT/tests/mascot/GenMascot.java"
java -cp "$OUT/gen" GenMascot "$OUT/classes"
javac -nowarn -Xlint:-options -source 8 -target 8 -encoding UTF-8 -bootclasspath "$B/j4i-runtime.jar" \
  -d "$OUT/classes" "$ROOT/tests/mascot/MascotTest.java"
printf 'MIDlet-Name: Mascot Test\nMIDlet-Vendor: J2ME4iOS\nMIDlet-Version: 1.0\nMIDlet-1: Mascot, , MascotTest\nMicroEdition-Configuration: CLDC-1.1\nMicroEdition-Profile: MIDP-2.0\n' > "$OUT/MANIFEST.MF"
(cd "$OUT/classes" && jar cfm "$B/tests/mascottest.jar" "$OUT/MANIFEST.MF" .)
echo "built $B/tests/mascottest.jar"
