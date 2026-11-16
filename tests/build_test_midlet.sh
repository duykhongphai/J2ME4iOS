#!/usr/bin/env bash
# Builds build/tests/testmidlet.jar from tests/midp (needs JDK + built runtime jar).
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
B="$ROOT/build"
OUT="$B/tests/midp"
rm -rf "$OUT" && mkdir -p "$OUT/classes" "$OUT/res" "$OUT/gen"
javac -d "$OUT/gen" "$ROOT/tests/midp/GenAssets.java"
java -cp "$OUT/gen" GenAssets "$OUT/res"
javac -nowarn -Xlint:-options -source 8 -target 8 -encoding UTF-8 -bootclasspath "$B/j4i-runtime.jar" \
  -d "$OUT/classes" "$ROOT/tests/midp/TestMidlet.java"
cat > "$OUT/MANIFEST.MF" <<EOF
MIDlet-Name: J2ME4iOS Test
MIDlet-Vendor: J2ME4iOS
MIDlet-Version: 1.0
MIDlet-1: Test, /rgba.png, TestMidlet
MicroEdition-Configuration: CLDC-1.1
MicroEdition-Profile: MIDP-2.0
EOF
cp "$OUT/res/"*.png "$OUT/classes/"
(cd "$OUT/classes" && jar cfm "$B/tests/testmidlet.jar" "$OUT/MANIFEST.MF" .)
echo "built $B/tests/testmidlet.jar"
