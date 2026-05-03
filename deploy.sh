#!/usr/bin/env bash
set -euo pipefail

APP_PACKAGE="com.example.blackout"
MAIN_ACTIVITY="${APP_PACKAGE}/.MainActivity"
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"

# Local file → device filename mapping (must match ModelVariant enum)
NPU_MODEL_LOCAL="models/gemma-4-E2B-it_qualcomm_sm8750.litertlm"
NPU_MODEL_DEVICE="/sdcard/Android/data/${APP_PACKAGE}/files/gemma4_npu.litertlm"

GENERIC_MODEL_LOCAL="models/gemma-4-E2B-it.litertlm"
GENERIC_MODEL_DEVICE="/sdcard/Android/data/${APP_PACKAGE}/files/gemma4.litertlm"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

info()  { echo -e "${GREEN}[deploy]${NC} $*"; }
warn()  { echo -e "${YELLOW}[deploy]${NC} $*"; }
error() { echo -e "${RED}[deploy]${NC} $*" >&2; exit 1; }

push_model_if_needed() {
    local local_path="$1"
    local device_path="$2"
    local label="$3"

    if [[ ! -f "$local_path" ]]; then
        warn "$label not found at $local_path — skipping push."
        warn "App will show 'Model missing' if $label variant is selected."
        return
    fi

    DEVICE_SIZE=$($ADB shell "stat -c %s '$device_path' 2>/dev/null" 2>/dev/null || echo "0")
    LOCAL_SIZE=$(stat --printf="%s" "$local_path" 2>/dev/null || stat -f%z "$local_path" 2>/dev/null || echo "0")

    if [[ "$DEVICE_SIZE" -ne "$LOCAL_SIZE" ]]; then
        info "Pushing $label to device ($(( LOCAL_SIZE / 1024 / 1024 )) MB, may take a few minutes)..."
        $ADB shell "mkdir -p /sdcard/Android/data/${APP_PACKAGE}/files/"
        $ADB push "$local_path" "$device_path"
        info "$label pushed."
    else
        info "$label already on device (size matches). Skipping."
    fi
}

# --- Locate the S25 device ---
info "Searching for connected Samsung S25..."

ADB_OUTPUT=$(adb devices 2>/dev/null | tail -n +2 | grep -v "^$" || true)

if [[ -z "$ADB_OUTPUT" ]]; then
    error "No devices found. Ensure USB debugging is enabled and the S25 is plugged in."
fi

DEVICE_COUNT=$(echo "$ADB_OUTPUT" | wc -l)
SERIAL=""

if [[ "$DEVICE_COUNT" -eq 1 ]]; then
    SERIAL=$(echo "$ADB_OUTPUT" | awk '{print $1}')
    STATE=$(echo "$ADB_OUTPUT" | awk '{print $2}')
    if [[ "$STATE" == "unauthorized" ]]; then
        error "Device $SERIAL is unauthorized. Unlock the phone and accept the USB debugging prompt."
    fi
    info "Found device: $SERIAL"
else
    warn "Multiple devices detected:"
    echo "$ADB_OUTPUT"
    echo ""
    read -rp "Enter the S25 serial (from list above): " SERIAL
    if [[ -z "$SERIAL" ]]; then
        error "No serial provided."
    fi
fi

ADB="adb -s $SERIAL"

# Verify device is reachable
$ADB get-state >/dev/null 2>&1 || error "Cannot communicate with device $SERIAL"
info "Device $SERIAL is online."

# --- Build ---
info "Building debug APK..."
./gradlew assembleDebug --quiet || error "Gradle build failed."
info "Build complete: $APK_PATH"

# --- Install ---
info "Installing APK on $SERIAL..."
$ADB install -r "$APK_PATH" || error "APK install failed."
info "Install complete."

# --- Push models ---
push_model_if_needed "$NPU_MODEL_LOCAL"     "$NPU_MODEL_DEVICE"     "NPU model (SM8750)"
push_model_if_needed "$GENERIC_MODEL_LOCAL" "$GENERIC_MODEL_DEVICE" "Generic model (CPU/GPU)"

# --- Launch ---
info "Launching Blackout..."
$ADB shell am force-stop "$APP_PACKAGE" 2>/dev/null || true
$ADB shell am start -n "$MAIN_ACTIVITY"

info "Done! Blackout is running on $SERIAL."
info "Watch backend:  adb -s $SERIAL logcat -s InferenceEngine:I"
info "Watch NPU init: adb -s $SERIAL logcat | grep -E 'InferenceEngine|LiteRt|QNN|Dispatch'"
