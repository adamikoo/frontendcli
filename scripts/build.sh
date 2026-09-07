#!/bin/bash
set -e

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_DIR="$PROJECT_ROOT/app"
BUILD_DIR="$APP_DIR/build"
OUT_DIR="$PROJECT_ROOT/build/outputs/apk/release"

export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
export PATH="/opt/kotlinc/bin:/usr/bin:$PATH"
D8_BIN="/opt/android-sdk/build-tools/34.0.0/d8"

echo "=== 1. Generating R.java with aapt ==="
mkdir -p "$BUILD_DIR/gen" "$BUILD_DIR/classes" "$BUILD_DIR/dex" "$OUT_DIR"
/usr/bin/aapt package -f -m -J "$BUILD_DIR/gen" -M "$APP_DIR/src/main/AndroidManifest.xml" -S "$APP_DIR/src/main/res" -I "$ANDROID_HOME/platforms/android-34/android.jar"

echo "=== 2. Compiling Kotlin and Java sources ==="
kotlinc -jvm-target 17 \
    -cp "$ANDROID_HOME/platforms/android-34/android.jar:/opt/kotlinc/lib/kotlin-stdlib.jar" \
    -d "$BUILD_DIR/classes" \
    "$BUILD_DIR/gen/com/antigravity/clifrontend/R.java" \
    $(find "$APP_DIR/src/main/kotlin" -name "*.kt")

echo "=== 3. Compiling bytecode with D8 ==="
$D8_BIN --min-api 26 \
   --lib "$ANDROID_HOME/platforms/android-34/android.jar" \
   --output "$BUILD_DIR/dex" \
   /opt/kotlinc/lib/kotlin-stdlib.jar \
   $(find "$BUILD_DIR/classes" -name "*.class")

echo "=== 4. Packaging unaligned APK ==="
/usr/bin/aapt package -f -M "$APP_DIR/src/main/AndroidManifest.xml" -S "$APP_DIR/src/main/res" -I "$ANDROID_HOME/platforms/android-34/android.jar" -F "$BUILD_DIR/unaligned.apk"
(cd "$BUILD_DIR/dex" && /usr/bin/aapt add "$BUILD_DIR/unaligned.apk" classes.dex)

echo "=== 5. Aligning APK with zipalign ==="
/usr/bin/zipalign -v -p -f 4 "$BUILD_DIR/unaligned.apk" "$OUT_DIR/CLIFrontend-release.apk"

echo "=== 6. Signing APK with apksigner ==="
/usr/bin/apksigner sign --ks "$PROJECT_ROOT/release.keystore" --ks-pass pass:clifrontend --key-pass pass:clifrontend "$OUT_DIR/CLIFrontend-release.apk"
/usr/bin/apksigner verify --verbose "$OUT_DIR/CLIFrontend-release.apk"

cp -f "$OUT_DIR/CLIFrontend-release.apk" "$PROJECT_ROOT/CLIFrontend.apk"
if [ -d "/sdcard/Download" ]; then
    cp -f "$PROJECT_ROOT/CLIFrontend.apk" "/sdcard/Download/CLIFrontend.apk" || true
fi

echo "=== BUILD SUCCESSFUL ==="
echo "Release APK created at: $PROJECT_ROOT/CLIFrontend.apk"
