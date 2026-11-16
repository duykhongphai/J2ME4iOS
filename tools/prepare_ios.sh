#!/usr/bin/env bash
# Prepares platform/ios for XcodeGen/Xcode: builds the runtime jar and copies the
# portable VM sources and the jar inside the iOS project folder (XcodeGen resolves
# paths outside the project folder unreliably). Then run: cd platform/ios && xcodegen generate
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
bash "$ROOT/tools/build_runtime.sh"
rm -rf "$ROOT/platform/ios/Core" "$ROOT/platform/ios/Bundle"
mkdir -p "$ROOT/platform/ios/Core" "$ROOT/platform/ios/Bundle"
cp "$ROOT"/core/*.c "$ROOT"/core/*.h "$ROOT/platform/ios/Core/"
cp "$ROOT/build/j4i-runtime.jar" "$ROOT/platform/ios/Bundle/"
echo "platform/ios is ready: cd platform/ios && xcodegen generate"
