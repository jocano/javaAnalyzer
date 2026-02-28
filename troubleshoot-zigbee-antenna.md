# Troubleshooting Zigbee Antenna on Raspberry Pi

## Step 1: Check if USB Device is Detected

```bash
# List all USB devices
lsusb

# Look for your Zigbee adapter (common brands: Texas Instruments, Silicon Labs, etc.)
# Example output might show:
# Bus 001 Device 003: ID 0451:16a8 Texas Instruments, Inc. CC2531

# Check if ttyUSB0 exists
ls -l /dev/ttyUSB0

# Check all serial devices
ls -l /dev/ttyUSB* /dev/ttyACM*

# Check device details
dmesg | grep -i usb | tail -20
dmesg | grep -i tty | tail -20
```

**Expected:** You should see your USB device in `lsusb` and `/dev/ttyUSB0` should exist.

## Step 2: Check USB Connection and Power

```bash
# Check USB device information
udevadm info /dev/ttyUSB0

# Check USB power/connection status
dmesg | grep -i "usb.*disconnect\|usb.*connect" | tail -10

# Check if device is getting power (LED on adapter should be on)
# Physical check: Look for LED indicator on your Zigbee adapter
```

**Troubleshooting:**
- Try different USB port on Raspberry Pi
- Use a powered USB hub (some adapters need more power)
- Check USB cable quality
- Try unplugging and replugging the adapter

## Step 3: Test Serial Port Communication

```bash
# Check port permissions
ls -l /dev/ttyUSB0
# Should show: crw-rw---- (readable/writable)

# Test if port can be opened
python3 << EOF
import serial
try:
    ser = serial.Serial('/dev/ttyUSB0', 115200, timeout=1)
    print(f"✅ Port opened successfully")
    print(f"   Port: {ser.name}")
    print(f"   Baudrate: {ser.baudrate}")
    print(f"   Is open: {ser.is_open}")
    ser.close()
except Exception as e:
    print(f"❌ Error: {e}")
EOF
```

## Step 4: Check zigbee2mqtt Logs

```bash
# If running in Docker
docker-compose logs zigbee2mqtt | tail -50

# Or if running directly
tail -50 /opt/zigbee2mqtt/data/log/*.log

# Look for these error messages:
# - "Error opening serialport"
# - "Cannot open /dev/ttyUSB0"
# - "Permission denied"
# - "Coordinator version: undefined" (bad sign)
# - "Zigbee: started successfully" (good sign)
```

## Step 5: Test Antenna with zigbee2mqtt

```bash
# Check zigbee2mqtt status
docker-compose exec zigbee2mqtt cat /app/data/configuration.yaml | grep -A 5 serial

# Or if running directly
cat /opt/zigbee2mqtt/data/configuration.yaml | grep -A 5 serial

# Verify configuration shows correct port:
# serial:
#   port: /dev/ttyUSB0
#   adapter: zstack
```

## Step 6: Check Coordinator Status

```bash
# Access zigbee2mqtt web UI
# Go to: http://<raspberry-pi-ip>:8080

# Or check via API
curl http://localhost:8080/api/config

# Look for coordinator information
```

## Step 7: Hardware Test - Reset the Adapter

```bash
# Method 1: Unplug and replug USB adapter
# 1. Stop zigbee2mqtt
docker-compose stop zigbee2mqtt

# 2. Unplug USB adapter
# 3. Wait 5 seconds
# 4. Plug back in
# 5. Wait for device to be recognized
ls -l /dev/ttyUSB0

# 6. Start zigbee2mqtt
docker-compose start zigbee2mqtt

# Method 2: Reset via software (if supported)
# Some adapters can be reset via serial commands
```

## Step 8: Check for Port Conflicts

```bash
# Check if another process is using the port
sudo lsof /dev/ttyUSB0

# Check if zigbee2mqtt is actually running
docker-compose ps

# Or if running as service
systemctl status zigbee2mqtt
```

## Step 9: Test Different Baudrates

Some Zigbee adapters use different baudrates. Test common ones:

```bash
# Test baudrate 115200 (most common)
python3 << EOF
import serial
for baud in [115200, 38400, 9600, 57600]:
    try:
        ser = serial.Serial('/dev/ttyUSB0', baud, timeout=1)
        print(f"✅ {baud} baud: OK")
        ser.close()
    except:
        print(f"❌ {baud} baud: Failed")
EOF
```

## Step 10: Check Kernel Messages

```bash
# Monitor kernel messages in real-time
sudo dmesg -w

# In another terminal, unplug and replug the USB adapter
# Look for connection/disconnection messages
```

## Step 11: Verify Adapter Firmware

```bash
# Check if adapter responds to AT commands (if supported)
# This depends on your adapter type

# For CC2531/CC2530 adapters, you might need to flash firmware
# Check zigbee2mqtt documentation for your specific adapter
```

## Step 12: Complete Diagnostic Script

Create and run this comprehensive test:

```bash
#!/bin/bash
# test-zigbee-antenna.sh

echo "=== Zigbee Antenna Diagnostic ==="
echo ""

# Check USB device
echo "1. USB Device Detection:"
if lsusb | grep -i "texas\|silicon\|zigbee\|cc253"; then
    echo "   ✅ USB device detected"
    lsusb | grep -i "texas\|silicon\|zigbee\|cc253"
else
    echo "   ❌ USB device NOT detected"
    echo "   Try: lsusb (to see all USB devices)"
fi
echo ""

# Check serial port
echo "2. Serial Port:"
if [ -e "/dev/ttyUSB0" ]; then
    echo "   ✅ /dev/ttyUSB0 exists"
    ls -l /dev/ttyUSB0
else
    echo "   ❌ /dev/ttyUSB0 NOT found"
    echo "   Check: ls -l /dev/ttyUSB*"
fi
echo ""

# Check permissions
echo "3. Permissions:"
if [ -r "/dev/ttyUSB0" ] && [ -w "/dev/ttyUSB0" ]; then
    echo "   ✅ Port is readable and writable"
else
    echo "   ❌ Permission issues"
    echo "   Fix: sudo usermod -a -G dialout \$USER"
fi
echo ""

# Check if port is in use
echo "4. Port Usage:"
if lsof /dev/ttyUSB0 2>/dev/null; then
    echo "   ⚠️  Port is in use"
else
    echo "   ✅ Port is available"
fi
echo ""

# Check zigbee2mqtt
echo "5. Zigbee2MQTT Status:"
if docker-compose ps zigbee2mqtt 2>/dev/null | grep -q "Up"; then
    echo "   ✅ zigbee2mqtt is running"
    echo ""
    echo "   Recent logs:"
    docker-compose logs --tail=10 zigbee2mqtt 2>/dev/null | grep -i "error\|started\|coordinator" || echo "   (no relevant logs)"
else
    echo "   ⚠️  zigbee2mqtt may not be running"
fi
echo ""

# Test serial communication
echo "6. Serial Communication Test:"
if command -v python3 &> /dev/null; then
    python3 << 'PYEOF'
import serial
import sys
try:
    ser = serial.Serial('/dev/ttyUSB0', 115200, timeout=1)
    print("   ✅ Serial port opens successfully")
    print(f"      Baudrate: {ser.baudrate}")
    ser.close()
    sys.exit(0)
except serial.SerialException as e:
    print(f"   ❌ Cannot open serial port: {e}")
    sys.exit(1)
except Exception as e:
    print(f"   ❌ Error: {e}")
    sys.exit(1)
PYEOF
else
    echo "   ⚠️  Python3 not available for serial test"
fi

echo ""
echo "=== Diagnostic Complete ==="
```

Make it executable and run:
```bash
chmod +x test-zigbee-antenna.sh
./test-zigbee-antenna.sh
```

## Common Issues and Solutions

### Issue 1: Device Not Detected
**Symptoms:** `lsusb` doesn't show the device
**Solutions:**
- Try different USB port
- Use powered USB hub
- Check USB cable
- Try on another computer to verify hardware

### Issue 2: Permission Denied
**Symptoms:** Cannot access `/dev/ttyUSB0`
**Solutions:**
```bash
sudo usermod -a -G dialout $USER
# Logout and login again
```

### Issue 3: Port Busy
**Symptoms:** "Device or resource busy"
**Solutions:**
```bash
# Stop zigbee2mqtt
docker-compose stop zigbee2mqtt

# Check what's using it
sudo lsof /dev/ttyUSB0

# Kill the process if needed
```

### Issue 4: Coordinator Not Responding
**Symptoms:** zigbee2mqtt can't communicate with adapter
**Solutions:**
- Reset the adapter (unplug/replug)
- Check baudrate in configuration
- Verify adapter firmware is correct
- Check adapter type in configuration (zstack, deconz, etc.)

### Issue 5: Devices Not Responding
**Symptoms:** Adapter works but devices don't
**Solutions:**
- Check Zigbee network channel
- Verify devices are still paired
- Check for interference (WiFi on same channel)
- Try permit_join: true to re-pair devices

## Quick Health Check Commands

```bash
# 1. Is USB device connected?
lsusb | grep -i "texas\|silicon"

# 2. Is serial port available?
ls -l /dev/ttyUSB0

# 3. Is zigbee2mqtt running?
docker-compose ps zigbee2mqtt

# 4. Are there errors in logs?
docker-compose logs zigbee2mqtt | grep -i error | tail -10

# 5. Can we open the port?
python3 -c "import serial; s=serial.Serial('/dev/ttyUSB0', 115200, timeout=1); print('OK' if s.is_open else 'FAIL'); s.close()"
```

## Next Steps if Antenna is Not Working

1. **Hardware Test:** Try the adapter on another computer
2. **Firmware Check:** Verify adapter firmware is correct for zigbee2mqtt
3. **Replacement:** If hardware is faulty, you may need a new adapter
4. **Alternative Port:** Try `/dev/ttyACM0` if your adapter appears there
5. **Adapter Type:** Verify correct adapter type in zigbee2mqtt config (zstack, deconz, etc.)
