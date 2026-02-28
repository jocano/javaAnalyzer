# Expected Output Examples for USB Device Detection

## 1. Expected `lsusb` Output

When your Zigbee USB adapter is connected, `lsusb` should show a device. Here are examples of what you might see:

### Example 1: Texas Instruments CC2531
```bash
$ lsusb
Bus 001 Device 003: ID 0451:16a8 Texas Instruments, Inc. CC2531
Bus 001 Device 002: ID 0424:9514 Standard Microsystems Corp.
Bus 001 Device 001: ID 1d6b:0002 Linux Foundation 2.0 root hub
```

### Example 2: Silicon Labs (EZSP adapter)
```bash
$ lsusb
Bus 001 Device 004: ID 10c4:ea60 Silicon Labs CP210x UART Bridge
Bus 001 Device 002: ID 0424:9514 Standard Microsystems Corp.
Bus 001 Device 001: ID 1d6b:0002 Linux Foundation 2.0 root hub
```

### Example 3: CC2652 (Sonoff, etc.)
```bash
$ lsusb
Bus 001 Device 005: ID 10c4:ea60 Silicon Labs CP210x UART Bridge
```

### Example 4: Generic USB Serial
```bash
$ lsusb
Bus 001 Device 004: ID 1a86:7523 QinHeng Electronics CH340 serial converter
```

**What to look for:**
- Any device with "Texas Instruments", "Silicon Labs", "CP210x", "CH340", or similar
- Device IDs like `0451:16a8`, `10c4:ea60`, `1a86:7523`
- The device should appear as a new entry when you plug it in

**If you DON'T see your device:**
```bash
$ lsusb
Bus 001 Device 002: ID 0424:9514 Standard Microsystems Corp.
Bus 001 Device 001: ID 1d6b:0002 Linux Foundation 2.0 root hub
# No Zigbee adapter shown - device not detected
```

## 2. Expected `/dev/ttyUSB0` Output

### When device exists and is working:
```bash
$ ls -l /dev/ttyUSB0
crw-rw---- 1 root dialout 188, 0 Jan 15 10:30 /dev/ttyUSB0
```

**What this means:**
- `c` = character device (serial port)
- `rw-rw----` = readable/writable by owner and group
- `root` = owned by root
- `dialout` = group (users in this group can access it)
- `188, 0` = major and minor device numbers
- Date/time = when device was created/connected

### When device doesn't exist:
```bash
$ ls -l /dev/ttyUSB0
ls: cannot access '/dev/ttyUSB0': No such file or directory
```

### Check all serial devices:
```bash
$ ls -l /dev/ttyUSB* /dev/ttyACM*
crw-rw---- 1 root dialout 188, 0 Jan 15 10:30 /dev/ttyUSB0
# OR
crw-rw---- 1 root dialout 166, 0 Jan 15 10:30 /dev/ttyACM0
```

**Note:** Some adapters appear as `/dev/ttyACM0` instead of `/dev/ttyUSB0`

## 3. Expected `dmesg` Output

When you plug in the USB adapter, you should see kernel messages:

### Good output (device recognized):
```bash
$ dmesg | tail -20
[12345.678901] usb 1-1.2: new full-speed USB device number 4 using dwc_otg
[12345.789012] usb 1-1.2: New USB device found, idVendor=0451, idProduct=16a8
[12345.789013] usb 1-1.2: New USB device strings: Mfr=1, Product=2, SerialNumber=3
[12345.789014] usb 1-1.2: Product: CC2531
[12345.789015] usb 1-1.2: Manufacturer: Texas Instruments
[12345.890123] cdc_acm 1-1.2:1.0: ttyACM0: USB ACM device
[12345.890124] usb 1-1.2: USB disconnect, device number 4
[12345.901234] usb 1-1.2: new full-speed USB device number 5 using dwc_otg
[12345.912345] usb 1-1.2: New USB device found, idVendor=0451, idProduct=16a8
[12345.923456] cdc_acm 1-1.2:1.0: ttyACM0: USB ACM device
```

### Or for ttyUSB:
```bash
$ dmesg | tail -20
[12345.678901] usb 1-1.2: new full-speed USB device number 4 using dwc_otg
[12345.789012] usb 1-1.2: New USB device found, idVendor=10c4, idProduct=ea60
[12345.890123] cp210x 1-1.2:1.0: cp210x converter now attached to ttyUSB0
```

**What to look for:**
- "New USB device found" with your device's vendor/product IDs
- "ttyUSB0" or "ttyACM0" being created
- No error messages

## 4. Complete Working Example

Here's what a complete successful check looks like:

```bash
# Step 1: Check USB device
$ lsusb
Bus 001 Device 005: ID 0451:16a8 Texas Instruments, Inc. CC2531
✅ Device detected!

# Step 2: Check serial port
$ ls -l /dev/ttyUSB0
crw-rw---- 1 root dialout 188, 0 Jan 15 10:30 /dev/ttyUSB0
✅ Port exists!

# Step 3: Check permissions
$ groups
pi dialout sudo
✅ User is in dialout group!

# Step 4: Test serial communication
$ python3 -c "import serial; s=serial.Serial('/dev/ttyUSB0', 115200, timeout=1); print('OK' if s.is_open else 'FAIL'); s.close()"
OK
✅ Port opens successfully!

# Step 5: Check zigbee2mqtt
$ docker-compose logs zigbee2mqtt | grep -i "started\|coordinator"
Zigbee: started successfully
Coordinator version: {'type': 'zStack12', 'meta': {'transportrev': 2, 'product': 2, 'majorrel': 6, 'minorrel': 7, 'maintrel': 3}}
✅ zigbee2mqtt can communicate with adapter!
```

## 5. Troubleshooting Based on Output

### If `lsusb` shows nothing:
- Device not physically connected
- USB port not working
- Adapter hardware failure
- Try different USB port/cable

### If `lsusb` shows device but no `/dev/ttyUSB0`:
- Driver not loaded
- Device appears as `/dev/ttyACM0` instead
- Check: `ls -l /dev/ttyACM*`
- May need to install driver: `sudo modprobe cdc_acm` or `sudo modprobe cp210x`

### If `/dev/ttyUSB0` exists but permission denied:
```bash
$ ls -l /dev/ttyUSB0
crw-rw---- 1 root dialout 188, 0 Jan 15 10:30 /dev/ttyUSB0
$ python3 -c "import serial; s=serial.Serial('/dev/ttyUSB0', 115200)"
PermissionError: [Errno 13] Permission denied: '/dev/ttyUSB0'
```
**Fix:** `sudo usermod -a -G dialout $USER` then logout/login

### If port exists but zigbee2mqtt can't use it:
- Check zigbee2mqtt configuration has correct port
- Check if another process is using it: `sudo lsof /dev/ttyUSB0`
- Restart zigbee2mqtt: `docker-compose restart zigbee2mqtt`

## 6. Quick Verification Commands

```bash
# All-in-one check
echo "=== USB Device ===" && \
lsusb | grep -iE "texas|silicon|zigbee|cc253" && \
echo "=== Serial Port ===" && \
ls -l /dev/ttyUSB0 2>/dev/null || ls -l /dev/ttyACM0 2>/dev/null && \
echo "=== Permissions ===" && \
groups | grep dialout && \
echo "=== Test Port ===" && \
python3 -c "import serial; s=serial.Serial('/dev/ttyUSB0', 115200, timeout=1); print('✅ OK'); s.close()" 2>/dev/null || \
python3 -c "import serial; s=serial.Serial('/dev/ttyACM0', 115200, timeout=1); print('✅ OK'); s.close()" 2>/dev/null
```

## Summary

**Expected when working:**
- ✅ `lsusb` shows your Zigbee adapter
- ✅ `/dev/ttyUSB0` (or `/dev/ttyACM0`) exists
- ✅ Permissions allow read/write access
- ✅ Serial port can be opened
- ✅ zigbee2mqtt logs show "started successfully"

**If any of these fail, use the troubleshooting steps in the main guide.**
