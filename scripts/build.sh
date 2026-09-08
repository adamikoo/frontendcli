#!/bin/bash
set -e

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_DIR="$PROJECT_ROOT/app"
BUILD_DIR="$APP_DIR/build"
OUT_DIR="$PROJECT_ROOT/build/outputs/apk/release"

# Android SDK Detection
if [ -z "$ANDROID_HOME" ]; then
    if [ -d "/opt/android-sdk" ]; then
        export ANDROID_HOME="/opt/android-sdk"
    elif [ -n "$ANDROID_SDK_ROOT" ] && [ -d "$ANDROID_SDK_ROOT" ]; then
        export ANDROID_HOME="$ANDROID_SDK_ROOT"
    elif [ -d "$HOME/Android/Sdk" ]; then
        export ANDROID_HOME="$HOME/Android/Sdk"
    fi
fi

# Build Tools Detection
BUILD_TOOLS_DIR=""
for cand in "$ANDROID_HOME/build-tools/34.0.0" "$ANDROID_HOME/build-tools/"* ; do
    if [ -d "$cand" ]; then
        BUILD_TOOLS_DIR="$cand"
        break
    fi
done

find_bin() {
    local name="$1"
    if [ -n "$BUILD_TOOLS_DIR" ] && [ -x "$BUILD_TOOLS_DIR/$name" ]; then
        echo "$BUILD_TOOLS_DIR/$name"
    elif command -v "$name" >/dev/null 2>&1; then
        command -v "$name"
    elif [ -x "/usr/bin/$name" ]; then
        echo "/usr/bin/$name"
    else
        echo "$name"
    fi
}

AAPT_BIN=$(find_bin "aapt")
D8_BIN=$(find_bin "d8")
ZIPALIGN_BIN=$(find_bin "zipalign")
APKSIGNER_BIN=$(find_bin "apksigner")

# Kotlin Compiler Detection
if ! command -v kotlinc >/dev/null 2>&1; then
    if [ -x "/opt/kotlinc/bin/kotlinc" ]; then
        export PATH="/opt/kotlinc/bin:$PATH"
    fi
fi

# Kotlin Stdlib Detection
KOTLIN_LIB=""
for cand in "/opt/kotlinc/lib/kotlin-stdlib.jar" \
            "/usr/share/kotlin/lib/kotlin-stdlib.jar" \
            "$(dirname "$(dirname "$(command -v kotlinc 2>/dev/null)")")/lib/kotlin-stdlib.jar"; do
    if [ -f "$cand" ]; then
        KOTLIN_LIB="$cand"
        break
    fi
done

# Android Jar Detection
ANDROID_JAR="$ANDROID_HOME/platforms/android-34/android.jar"
if [ ! -f "$ANDROID_JAR" ]; then
    # Fallback to any available platform
    ANDROID_JAR=$(find "$ANDROID_HOME/platforms" -name "android.jar" 2>/dev/null | head -n 1)
fi

echo "=== Environment Info ==="
echo "ANDROID_HOME: $ANDROID_HOME"
echo "ANDROID_JAR:  $ANDROID_JAR"
echo "AAPT_BIN:     $AAPT_BIN"
echo "D8_BIN:       $D8_BIN"
echo "ZIPALIGN_BIN: $ZIPALIGN_BIN"
echo "APKSIGNER:    $APKSIGNER_BIN"
echo "KOTLIN_LIB:   $KOTLIN_LIB"
echo "========================"

echo "=== 1. Generating R.java with aapt ==="
mkdir -p "$BUILD_DIR/gen" "$BUILD_DIR/classes" "$BUILD_DIR/dex" "$OUT_DIR"
"$AAPT_BIN" package -f -m --custom-package com.antigravity.pocketgravity -J "$BUILD_DIR/gen" -M "$APP_DIR/src/main/AndroidManifest.xml" -S "$APP_DIR/src/main/res" -I "$ANDROID_JAR"

echo "=== 2. Compiling Kotlin and Java sources ==="
kotlinc -jvm-target 17 \
    -cp "$ANDROID_JAR:$KOTLIN_LIB" \
    -d "$BUILD_DIR/classes" \
    "$BUILD_DIR/gen/com/antigravity/pocketgravity/R.java" \
    $(find "$APP_DIR/src/main/kotlin" -name "*.kt")

echo "=== 3. Compiling bytecode with D8 ==="
"$D8_BIN" --min-api 26 \
   --lib "$ANDROID_JAR" \
   --output "$BUILD_DIR/dex" \
   "$KOTLIN_LIB" \
   $(find "$BUILD_DIR/classes" -name "*.class")

echo "=== 4. Packaging unaligned APK with assets ==="
"$AAPT_BIN" package -f -M "$APP_DIR/src/main/AndroidManifest.xml" -S "$APP_DIR/src/main/res" -A "$APP_DIR/src/main/assets" -0 gz -0 tar.gz -0 so -0 crt -0 tar -I "$ANDROID_JAR" -F "$BUILD_DIR/unaligned.apk"
(cd "$BUILD_DIR/dex" && "$AAPT_BIN" add "$BUILD_DIR/unaligned.apk" classes.dex)

echo "=== 5. Aligning APK with zipalign ==="
"$ZIPALIGN_BIN" -v -p -f 4 "$BUILD_DIR/unaligned.apk" "$OUT_DIR/CLIFrontend-release.apk"

echo "=== 6. Signing APK with apksigner ==="
"$APKSIGNER_BIN" sign --ks "$PROJECT_ROOT/release.keystore" --ks-pass pass:clifrontend --key-pass pass:clifrontend "$OUT_DIR/CLIFrontend-release.apk"
"$APKSIGNER_BIN" verify --verbose "$OUT_DIR/CLIFrontend-release.apk"

cp -f "$OUT_DIR/CLIFrontend-release.apk" "$PROJECT_ROOT/CLIFrontend.apk"
if [ -d "/sdcard/Download" ]; then
    cp -f "$PROJECT_ROOT/CLIFrontend.apk" "/sdcard/Download/CLIFrontend.apk" || true
fi

echo "=== BUILD SUCCESSFUL ==="
echo "Release APK created at: $PROJECT_ROOT/CLIFrontend.apk"
