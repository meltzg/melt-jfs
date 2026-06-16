#!/usr/bin/env bash
# record-fixture.sh — capture a umockdev fixture from a real MTP device.
#
# Prerequisites:
#   sudo apt install umockdev
#
# Usage:
#   1. Connect the device (vendor 4102 / product 1191, e.g. AK100 II).
#   2. Run this script — it starts umockdev-record in the background.
#   3. In a separate terminal, run:  ./gradlew test --tests '*MTPDeviceBridgeTest*'
#   4. Return here and press Enter to stop recording.
#
# Output files written to src/test/resources/umockdev/:
#   ak100ii.umockdev  — sysfs device attributes (text, checked into git)
#   ak100ii.ioctl     — USB ioctl traffic replay (binary, checked into git)

set -euo pipefail

VENDOR_ID=4102
PRODUCT_ID=1191
FIXTURE_DIR="src/test/resources/umockdev"

for cmd in umockdev-record udevadm lsusb; do
    command -v "$cmd" &>/dev/null || { echo "Error: $cmd not found. Install the umockdev package."; exit 1; }
done

lsusb_line=$(lsusb -d "${VENDOR_ID}:${PRODUCT_ID}" 2>/dev/null | head -1)
if [[ -z "$lsusb_line" ]]; then
    echo "Device ${VENDOR_ID}:${PRODUCT_ID} not found. Connect the device and retry."
    exit 1
fi

BUS=$(echo "$lsusb_line" | awk '{printf "%03d", $2}')
DEV=$(echo "$lsusb_line" | awk '{printf "%03d", $4}' | tr -d ':')
DEV_NODE="/dev/bus/usb/${BUS}/${DEV}"
SYSFS_PATH=$(udevadm info --name="$DEV_NODE" --query=path)

echo "Found device at $DEV_NODE  (sysfs: $SYSFS_PATH)"
echo ""
mkdir -p "$FIXTURE_DIR"

umockdev-record \
    --ioctl "${DEV_NODE}=${FIXTURE_DIR}/ak100ii.ioctl" \
    "$SYSFS_PATH" \
    > "${FIXTURE_DIR}/ak100ii.umockdev" &
RECORD_PID=$!

echo "Recording started (PID ${RECORD_PID})."
echo ""
echo "  In another terminal run:"
echo "    ./gradlew test --tests '*MTPDeviceBridgeTest*'"
echo ""
echo "Press Enter here when the test has finished to stop the recording."
read -r _
kill "$RECORD_PID" 2>/dev/null || true
wait "$RECORD_PID" 2>/dev/null || true

echo ""
echo "Saved:"
echo "  ${FIXTURE_DIR}/ak100ii.umockdev  ($(wc -l < "${FIXTURE_DIR}/ak100ii.umockdev") lines)"
echo "  ${FIXTURE_DIR}/ak100ii.ioctl     ($(wc -c < "${FIXTURE_DIR}/ak100ii.ioctl") bytes)"
echo ""
echo "Commit both files. Tests will now run in CI without the real device."
