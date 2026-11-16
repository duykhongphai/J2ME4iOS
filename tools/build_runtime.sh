#!/usr/bin/env bash
# Builds build/j4i-runtime.jar (CLDC/MIDP classes) with any JDK >= 9, without CMake.
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/build/runtime-classes"
rm -rf "$OUT" && mkdir -p "$OUT"
find "$ROOT/runtime/src" -name '*.java' > "$ROOT/build/runtime-sources.txt"
javac -nowarn -Xlint:-options -source 8 -target 8 -encoding UTF-8 \
  -bootclasspath "$OUT" -d "$OUT" @"$ROOT/build/runtime-sources.txt"
(cd "$OUT" && jar cf0 "$ROOT/build/j4i-runtime.jar" .)
echo "built $ROOT/build/j4i-runtime.jar"
