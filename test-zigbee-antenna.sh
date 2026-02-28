#!/bin/bash
# test-zigbee-antenna.sh
# Comprehensive test for Zigbee antenna on Raspberry Pi

set -e

echo "=========================================="
echo "Zigbee Antenna Diagnostic Test"
echo "=========================================="
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Test results
TESTS_PASSED=0
TESTS_FAILED=0

# Function to print test result
print_result() {
    if [ $1 -eq 0 ]; then
        echo -e "${GREEN}   ✅ $2${NC}"
        ((TESTS_PASSED++))
    else
        echo -e "${RED}   ❌ $2${NC}"
        ((TESTS_FAILED++))
    fi
}

# Test 1: USB Device Detection
echo "1. USB Device Detection:"
if lsusb 2>/dev/null | grep -qiE "texas|silicon|zigbee|cc253|cc2652|ezsp"; then
    echo -e "${GREEN}   ✅ USB device detected${NC}"
    echo "   Device details:"
    lsusb | grep -iE "texas|silicon|zigbee|cc253|cc2652|ezsp" | sed 's/^/      /'
    ((TESTS_PASSED++))
else
    echo -e "${RED}   ❌ USB device NOT detected${NC}"
    echo "   All USB devices:"
    lsusb | sed 's/^/      /'
    echo ""
    echo "   Troubleshooting:"
    echo "   - Try unplugging and replugging the USB adapter"
    echo "   - Try a different USB port"
    echo "   - Check if adapter LED is on (if it has one)"
    ((TESTS_FAILED++))
fi
echo ""

# Test 2: Serial Port Existence
echo "2. Serial Port Check:"
if [ -e "/dev/ttyUSB0" ]; then
    echo -e "${GREEN}   ✅ /dev/ttyUSB0 exists${NC}"
    ls -l /dev/ttyUSB0 | sed 's/^/      /'
    ((TESTS_PASSED++))
else
    echo -e "${RED}   ❌ /dev/ttyUSB0 NOT found${NC}"
    echo "   Available serial ports:"
    ls -l /dev/ttyUSB* /dev/ttyACM* 2>/dev/null | sed 's/^/      /' || echo "      (none found)"
    ((TESTS_FAILED++))
fi
echo ""

# Test 3: Port Permissions
echo "3. Port Permissions:"
if [ -e "/dev/ttyUSB0" ]; then
    if [ -r "/dev/ttyUSB0" ] && [ -w "/dev/ttyUSB0" ]; then
        echo -e "${GREEN}   ✅ Port is readable and writable${NC}"
        ((TESTS_PASSED++))
    else
        echo -e "${RED}   ❌ Permission denied${NC}"
        echo "   Fix with: sudo usermod -a -G dialout \$USER"
        echo "   Then logout and login again"
        ((TESTS_FAILED++))
    fi
else
    echo -e "${YELLOW}   ⚠️  Skipped (port doesn't exist)${NC}"
fi
echo ""

# Test 4: Port Usage
echo "4. Port Usage Check:"
if [ -e "/dev/ttyUSB0" ]; then
    if command -v lsof &> /dev/null; then
        if lsof /dev/ttyUSB0 2>/dev/null | grep -q .; then
            echo -e "${YELLOW}   ⚠️  Port is in use by:${NC}"
            lsof /dev/ttyUSB0 | sed 's/^/      /'
            echo "   (This is OK if zigbee2mqtt is using it)"
        else
            echo -e "${GREEN}   ✅ Port is available${NC}"
            ((TESTS_PASSED++))
        fi
    else
        echo -e "${YELLOW}   ⚠️  'lsof' not installed, skipping${NC}"
    fi
else
    echo -e "${YELLOW}   ⚠️  Skipped (port doesn't exist)${NC}"
fi
echo ""

# Test 5: Serial Communication
echo "5. Serial Communication Test:"
if [ -e "/dev/ttyUSB0" ] && command -v python3 &> /dev/null; then
    # Check if pyserial is installed
    if python3 -c "import serial" 2>/dev/null; then
        python3 << 'PYEOF'
import serial
import sys
try:
    ser = serial.Serial('/dev/ttyUSB0', 115200, timeout=1)
    print("   ✅ Serial port opens successfully")
    print(f"      Port: {ser.name}")
    print(f"      Baudrate: {ser.baudrate}")
    print(f"      Is open: {ser.is_open}")
    ser.close()
    sys.exit(0)
except serial.SerialException as e:
    print(f"   ❌ Cannot open serial port: {e}")
    sys.exit(1)
except Exception as e:
    print(f"   ❌ Error: {e}")
    sys.exit(1)
PYEOF
        if [ $? -eq 0 ]; then
            ((TESTS_PASSED++))
        else
            ((TESTS_FAILED++))
        fi
    else
        echo -e "${YELLOW}   ⚠️  pyserial not installed${NC}"
        echo "   Install with: pip3 install pyserial"
        echo "   Or: sudo apt-get install python3-serial"
    fi
else
    if [ ! -e "/dev/ttyUSB0" ]; then
        echo -e "${YELLOW}   ⚠️  Skipped (port doesn't exist)${NC}"
    else
        echo -e "${YELLOW}   ⚠️  Python3 not available${NC}"
    fi
fi
echo ""

# Test 6: Zigbee2MQTT Status
echo "6. Zigbee2MQTT Status:"
if command -v docker-compose &> /dev/null || docker compose version &> /dev/null; then
    if [ -f "docker-compose.yml" ] || [ -f "docker-compose.yaml" ]; then
        if docker-compose ps zigbee2mqtt 2>/dev/null | grep -q "Up" || \
           docker compose ps zigbee2mqtt 2>/dev/null | grep -q "Up"; then
            echo -e "${GREEN}   ✅ zigbee2mqtt container is running${NC}"
            echo ""
            echo "   Recent log entries (looking for errors):"
            docker-compose logs --tail=20 zigbee2mqtt 2>/dev/null | \
                grep -iE "error|started|coordinator|serial" | tail -5 | sed 's/^/      /' || \
                echo "      (no relevant log entries found)"
            ((TESTS_PASSED++))
        else
            echo -e "${YELLOW}   ⚠️  zigbee2mqtt container is not running${NC}"
            echo "   Start with: docker-compose up -d"
        fi
    else
        echo -e "${YELLOW}   ⚠️  docker-compose.yml not found in current directory${NC}"
    fi
else
    # Check if running as systemd service
    if systemctl is-active --quiet zigbee2mqtt 2>/dev/null; then
        echo -e "${GREEN}   ✅ zigbee2mqtt service is running${NC}"
        ((TESTS_PASSED++))
    else
        echo -e "${YELLOW}   ⚠️  zigbee2mqtt service status unknown${NC}"
    fi
fi
echo ""

# Test 7: Kernel Messages
echo "7. Recent Kernel Messages:"
echo "   Checking for USB connection/disconnection events..."
RECENT_USB=$(dmesg | tail -30 | grep -iE "usb|tty" | tail -5)
if [ -n "$RECENT_USB" ]; then
    echo "$RECENT_USB" | sed 's/^/      /'
else
    echo "      (no recent USB/tty messages)"
fi
echo ""

# Summary
echo "=========================================="
echo "Test Summary:"
echo -e "   ${GREEN}Passed: $TESTS_PASSED${NC}"
if [ $TESTS_FAILED -gt 0 ]; then
    echo -e "   ${RED}Failed: $TESTS_FAILED${NC}"
fi
echo "=========================================="
echo ""

# Recommendations
if [ $TESTS_FAILED -gt 0 ]; then
    echo "Recommendations:"
    echo ""
    
    if ! lsusb 2>/dev/null | grep -qiE "texas|silicon|zigbee|cc253"; then
        echo "1. USB device not detected:"
        echo "   - Unplug and replug the USB adapter"
        echo "   - Try a different USB port"
        echo "   - Use a powered USB hub"
        echo "   - Check USB cable"
        echo ""
    fi
    
    if [ ! -e "/dev/ttyUSB0" ]; then
        echo "2. Serial port not found:"
        echo "   - Check if device appears as /dev/ttyACM0 instead"
        echo "   - Run: ls -l /dev/ttyUSB* /dev/ttyACM*"
        echo "   - Check dmesg: dmesg | tail -20"
        echo ""
    fi
    
    echo "3. If antenna was working before:"
    echo "   - Try restarting zigbee2mqtt: docker-compose restart zigbee2mqtt"
    echo "   - Check zigbee2mqtt logs: docker-compose logs zigbee2mqtt"
    echo "   - Verify configuration: cat docker-compose.yml | grep ttyUSB0"
    echo ""
fi

echo "For more detailed troubleshooting, see: troubleshoot-zigbee-antenna.md"
echo ""
