#!/bin/bash
# Enhanced Android APK Build Script
# Based on industry best practices

set -e  # Exit on error
set -u  # Exit on undefined variable

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Build configuration
readonly SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
readonly BUILD_DIR="${SCRIPT_DIR}/build"
readonly OUT_DIR="${BUILD_DIR}/out"
readonly DEX_DIR="${BUILD_DIR}/dex"
readonly APK_UNSIGNED="${BUILD_DIR}/app-unsigned.apk"
readonly APK_ALIGNED="${BUILD_DIR}/app-aligned.apk"
readonly APK_SIGNED="${SCRIPT_DIR}/app.apk"

# Android SDK paths
readonly ANDROID_JAR="/usr/lib/android-sdk/platforms/android-23/android.jar"
readonly R8_JAR="/root/build/r8.jar"
readonly KEYSTORE="${SCRIPT_DIR}/qdl.keystore"
readonly KEYSTORE_PASS="qdlqdl123"

# Build info
readonly VERSION_CODE=$(grep 'android:versionCode' "${SCRIPT_DIR}/AndroidManifest.xml" | grep -oP '\d+')
readonly VERSION_NAME=$(grep 'android:versionName' "${SCRIPT_DIR}/AndroidManifest.xml" | grep -oP '[\d\.\-a-z]+' | head -1)

echo_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

echo_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

echo_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

check_prerequisites() {
    echo_info "Checking prerequisites..."

    local missing=()

    command -v javac >/dev/null 2>&1 || missing+=("javac")
    command -v java >/dev/null 2>&1 || missing+=("java")
    command -v aapt >/dev/null 2>&1 || missing+=("aapt")
    command -v zipalign >/dev/null 2>&1 || missing+=("zipalign")
    command -v apksigner >/dev/null 2>&1 || missing+=("apksigner")

    [[ ! -f "$ANDROID_JAR" ]] && missing+=("android.jar")
    [[ ! -f "$R8_JAR" ]] && missing+=("r8.jar")
    [[ ! -f "$KEYSTORE" ]] && missing+=("qdl.keystore")

    if [[ ${#missing[@]} -gt 0 ]]; then
        echo_error "Missing required tools/files:"
        printf '  - %s\n' "${missing[@]}"
        exit 1
    fi

    echo_info "✓ All prerequisites satisfied"
}

clean_build() {
    echo_info "Cleaning previous build..."
    rm -rf "${BUILD_DIR}"
    rm -f "${APK_SIGNED}"
    mkdir -p "${OUT_DIR}" "${DEX_DIR}"
}

compile_java() {
    echo_info "Compiling Java sources..."

    local java_files=()
    while IFS= read -r -d '' file; do
        java_files+=("$file")
    done < <(find "${SCRIPT_DIR}/src" -name "*.java" -print0)

    echo_info "Found ${#java_files[@]} Java files"

    javac \
        -nowarn \
        -source 8 \
        -target 8 \
        -encoding UTF-8 \
        -bootclasspath "$ANDROID_JAR" \
        -d "$OUT_DIR" \
        "${java_files[@]}"

    echo_info "✓ Java compilation successful"
}

dex_classes() {
    echo_info "Converting bytecode to DEX..."

    java -cp "$R8_JAR" com.android.tools.r8.D8 \
        --lib "$ANDROID_JAR" \
        --min-api 21 \
        --output "$DEX_DIR" \
        $(find "$OUT_DIR" -name '*.class')

    echo_info "✓ DEX conversion successful"
}

package_apk() {
    echo_info "Packaging APK resources..."

    aapt package \
        -f \
        -M "${SCRIPT_DIR}/AndroidManifest.xml" \
        -S "${SCRIPT_DIR}/res" \
        -I "$ANDROID_JAR" \
        -A "${SCRIPT_DIR}/assets" \
        -F "$APK_UNSIGNED"

    echo_info "Adding DEX to APK..."
    (cd "$DEX_DIR" && aapt add "$APK_UNSIGNED" classes.dex)

    echo_info "✓ APK packaging successful"
}

align_apk() {
    echo_info "Aligning APK..."

    zipalign -f 4 "$APK_UNSIGNED" "$APK_ALIGNED"

    echo_info "✓ APK alignment successful"
}

sign_apk() {
    echo_info "Signing APK..."

    apksigner sign \
        --ks "$KEYSTORE" \
        --ks-pass "pass:${KEYSTORE_PASS}" \
        --key-pass "pass:${KEYSTORE_PASS}" \
        --out "$APK_SIGNED" \
        "$APK_ALIGNED"

    echo_info "✓ APK signing successful"
}

verify_apk() {
    echo_info "Verifying APK signature..."

    if apksigner verify "$APK_SIGNED" 2>/dev/null; then
        echo_info "✓ APK signature valid"
    else
        echo_error "APK signature verification failed!"
        exit 1
    fi
}

show_build_info() {
    local apk_size=$(stat -c%s "$APK_SIGNED" 2>/dev/null || stat -f%z "$APK_SIGNED" 2>/dev/null)
    local apk_size_mb=$(awk "BEGIN {printf \"%.2f\", $apk_size/1024/1024}")

    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo_info "Build completed successfully!"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "  📦 APK Path:    ${APK_SIGNED}"
    echo "  📏 Size:        ${apk_size_mb} MB"
    echo "  🏷️  Version:     ${VERSION_NAME} (${VERSION_CODE})"
    echo "  📱 Min SDK:     21 (Android 5.0)"
    echo "  🎯 Target SDK:  28 (Android 9.0)"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
}

main() {
    echo ""
    echo "🔨 Building Android APK..."
    echo "Version: ${VERSION_NAME} (${VERSION_CODE})"
    echo ""

    local start_time=$(date +%s)

    check_prerequisites
    clean_build
    compile_java
    dex_classes
    package_apk
    align_apk
    sign_apk
    verify_apk

    local end_time=$(date +%s)
    local elapsed=$((end_time - start_time))

    show_build_info
    echo_info "Build time: ${elapsed}s"
    echo ""
}

# Run build
main "$@"
