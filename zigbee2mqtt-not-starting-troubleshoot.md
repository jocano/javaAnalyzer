# Troubleshooting: Zigbee2MQTT Not Starting Successfully

## Problem: Adapter Detected But "Zigbee: started successfully" Never Appears

If you see the adapter discovery message but never see "Zigbee: started successfully", the controller is failing to initialize.

## Step 1: Check Full Error Logs

```bash
# Get complete startup logs
docker-compose logs zigbee2mqtt | tail -100

# Look for errors after the controller starting message
docker-compose logs zigbee2mqtt | grep -A 20 "controller: Starting"

# Check for specific error patterns
docker-compose logs zigbee2mqtt | grep -iE "error|failed|timeout|cannot|unable"
```

## Step 2: Common Issues and Solutions

### Issue 1: Coordinator Not Responding

**Symptoms:**
```
zh:controller: Starting with options...
[timestamp] error: Coordinator version: undefined
[timestamp] error: Failed to start
```

**Solutions:**

```bash
# 1. Reset the adapter (unplug/replug USB)
docker-compose stop zigbee2mqtt
# Unplug USB adapter
sleep 5
# Plug back in
sleep 5
docker-compose start zigbee2mqtt

# 2. Check if port is accessible
ls -l /dev/ttyUSB0
python3 -c "import serial; s=serial.Serial('/dev/ttyUSB0', 115200, timeout=1); print('OK'); s.close()"

# 3. Try different baudrate in configuration
# Edit configuration.yaml and try:
# serial:
#   port: /dev/ttyUSB0
#   adapter: zstack
#   baudrate: 38400  # or 9600, 57600
```

### Issue 2: Permission Denied

**Symptoms:**
```
[timestamp] error: Error opening serialport: Error: Permission denied
```

**Solutions:**

```bash
# Fix permissions
sudo usermod -a -G dialout $USER
# Logout and login again

# Or temporarily fix:
sudo chmod 666 /dev/ttyUSB0

# Check current permissions
ls -l /dev/ttyUSB0
groups | grep dialout
```

### Issue 3: Port Busy or Already in Use

**Symptoms:**
```
[timestamp] error: Error opening serialport: Error: Resource busy
```

**Solutions:**

```bash
# Check what's using the port
sudo lsof /dev/ttyUSB0

# Kill the process if needed
sudo kill -9 <PID>

# Or restart Docker
sudo systemctl restart docker
docker-compose restart zigbee2mqtt
```

### Issue 4: Wrong Adapter Type

**Symptoms:**
```
[timestamp] error: Adapter type mismatch
[timestamp] error: Unsupported adapter
```

**Solutions:**

For SMLIGHT SLZB-07p7, verify configuration:

```yaml
# configuration.yaml
serial:
  port: /dev/ttyUSB0
  adapter: zstack  # ✅ Correct for SLZB-07p7
```

### Issue 5: Network Key or Coordinator Backup Issues

**Symptoms:**
```
[timestamp] error: Failed to restore coordinator
[timestamp] error: Network key mismatch
```

**Solutions:**

```bash
# Check if coordinator backup exists
docker-compose exec zigbee2mqtt ls -la /app/data/coordinator_backup.json

# If backup is corrupted, you may need to reset
# WARNING: This will require re-pairing all devices
docker-compose exec zigbee2mqtt rm /app/data/coordinator_backup.json
docker-compose restart zigbee2mqtt
```

### Issue 6: Database Corruption

**Symptoms:**
```
[timestamp] error: Database error
[timestamp] error: Failed to open database
```

**Solutions:**

```bash
# Backup current database
docker-compose exec zigbee2mqtt cp /app/data/database.db /app/data/database.db.backup

# Remove corrupted database (WARNING: Will lose device data)
docker-compose exec zigbee2mqtt rm /app/data/database.db
docker-compose restart zigbee2mqtt
```

## Step 3: Diagnostic Commands

Run these to gather information:

```bash
# 1. Check if port is accessible
echo "=== Port Check ==="
ls -l /dev/ttyUSB0
python3 -c "import serial; s=serial.Serial('/dev/ttyUSB0', 115200, timeout=1); print('Port OK'); s.close()" 2>&1

# 2. Check zigbee2mqtt container status
echo "=== Container Status ==="
docker-compose ps zigbee2mqtt

# 3. Check recent errors
echo "=== Recent Errors ==="
docker-compose logs zigbee2mqtt | grep -i error | tail -10

# 4. Check what happens after "controller: Starting"
echo "=== After Controller Start ==="
docker-compose logs zigbee2mqtt | grep -A 30 "controller: Starting" | tail -30

# 5. Check coordinator response
echo "=== Coordinator Status ==="
docker-compose logs zigbee2mqtt | grep -i "coordinator\|version" | tail -5
```

## Step 4: Reset and Restart Procedure

If nothing else works, try a complete reset:

```bash
# 1. Stop zigbee2mqtt
docker-compose stop zigbee2mqtt

# 2. Unplug USB adapter
# Wait 10 seconds

# 3. Plug USB adapter back in
# Wait 5 seconds

# 4. Verify device is detected
lsusb | grep -i "10c4:ea60"
ls -l /dev/ttyUSB0

# 5. Clear any locks
sudo fuser -k /dev/ttyUSB0 2>/dev/null || true

# 6. Start zigbee2mqtt
docker-compose start zigbee2mqtt

# 7. Watch logs
docker-compose logs -f zigbee2mqtt
```

## Step 5: Check Configuration

Verify your configuration is correct:

```bash
# View current configuration
docker-compose exec zigbee2mqtt cat /app/data/configuration.yaml | grep -A 10 serial

# Should show:
# serial:
#   port: /dev/ttyUSB0
#   adapter: zstack
```

## Step 6: Test Serial Communication Directly

Test if the adapter responds to serial commands:

```bash
# Install minicom for testing
sudo apt-get install minicom -y

# Test serial communication
sudo minicom -D /dev/ttyUSB0 -b 115200

# In minicom, try typing (some adapters respond to AT commands)
# Press Ctrl+A then X to exit
```

## Step 7: Check for Firmware Issues

Some adapters need specific firmware. For SLZB-07p7:

```bash
# Check if adapter responds at all
# The adapter should at least acknowledge serial connection

# If adapter doesn't respond, it may need firmware update
# Check SMLIGHT documentation for firmware update procedure
```

## Step 8: Enable Debug Logging

Get more detailed logs:

```yaml
# In configuration.yaml
advanced:
  log_level: debug  # Change from info to debug
```

Then restart and check logs:

```bash
docker-compose restart zigbee2mqtt
docker-compose logs -f zigbee2mqtt
```

## Step 9: Check System Resources

```bash
# Check if Raspberry Pi has enough resources
free -h
df -h

# Check if Docker has issues
docker system df
docker system prune -f  # Clean up if needed
```

## Step 10: Complete Diagnostic Script

Run this comprehensive check:

```bash
#!/bin/bash
echo "=== Zigbee2MQTT Startup Diagnostic ==="
echo ""

echo "1. USB Device:"
lsusb | grep -i "10c4:ea60" && echo "✅ Detected" || echo "❌ Not found"
echo ""

echo "2. Serial Port:"
[ -e /dev/ttyUSB0 ] && echo "✅ /dev/ttyUSB0 exists" || echo "❌ /dev/ttyUSB0 missing"
ls -l /dev/ttyUSB0 2>/dev/null
echo ""

echo "3. Port Permissions:"
[ -r /dev/ttyUSB0 ] && [ -w /dev/ttyUSB0 ] && echo "✅ Readable/writable" || echo "❌ Permission issue"
echo ""

echo "4. Port Usage:"
sudo lsof /dev/ttyUSB0 2>/dev/null && echo "⚠️  Port in use" || echo "✅ Port available"
echo ""

echo "5. Container Status:"
docker-compose ps zigbee2mqtt
echo ""

echo "6. Recent Logs (last 20 lines):"
docker-compose logs --tail=20 zigbee2mqtt
echo ""

echo "7. Errors in Logs:"
docker-compose logs zigbee2mqtt | grep -i error | tail -5
echo ""

echo "8. After Controller Start:"
docker-compose logs zigbee2mqtt | grep -A 20 "controller: Starting" | tail -20
echo ""

echo "=== Diagnostic Complete ==="
```

## Most Common Solution

For SMLIGHT SLZB-07p7, the most common fix is:

```bash
# 1. Stop zigbee2mqtt
docker-compose stop zigbee2mqtt

# 2. Reset USB adapter (unplug/replug)
# Wait 10 seconds between

# 3. Verify port
ls -l /dev/ttyUSB0

# 4. Restart with fresh connection
docker-compose start zigbee2mqtt

# 5. Watch for success message
docker-compose logs -f zigbee2mqtt | grep -E "started successfully|error|failed"
```

## What to Share for Further Help

If still not working, share these outputs:

```bash
# Complete startup logs
docker-compose logs zigbee2mqtt | tail -100

# Configuration
docker-compose exec zigbee2mqtt cat /app/data/configuration.yaml

# System info
lsusb
ls -l /dev/ttyUSB0
docker-compose ps
```
