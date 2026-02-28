#!/usr/bin/env python3
"""
Test script to verify ttyUSB0 port is working correctly
for SMLIGHT SLZB-07p7 Zigbee device on Raspberry Pi 4
"""

import serial
import sys
import time
import os

PORT = '/dev/ttyUSB0'
BAUDRATE = 115200  # Common baudrate for SLZB-07p7

def check_port_exists():
    """Check if the port file exists"""
    if not os.path.exists(PORT):
        print(f"❌ ERROR: {PORT} does not exist!")
        print("\nTroubleshooting:")
        print("1. Check USB connection: lsusb")
        print("2. Check if device is recognized: dmesg | tail -20")
        print("3. Try: ls -l /dev/ttyUSB*")
        return False
    print(f"✅ Port exists: {PORT}")
    return True

def check_permissions():
    """Check if we have read/write permissions"""
    if not os.access(PORT, os.R_OK):
        print(f"❌ ERROR: No read permission for {PORT}")
        print("Fix: sudo chmod 666 /dev/ttyUSB0")
        print("Or: sudo usermod -a -G dialout $USER (then logout/login)")
        return False
    if not os.access(PORT, os.W_OK):
        print(f"❌ ERROR: No write permission for {PORT}")
        print("Fix: sudo chmod 666 /dev/ttyUSB0")
        print("Or: sudo usermod -a -G dialout $USER (then logout/login)")
        return False
    print(f"✅ Port permissions OK (readable and writable)")
    return True

def check_port_in_use():
    """Check if port is already in use"""
    try:
        import subprocess
        result = subprocess.run(['lsof', PORT], 
                              capture_output=True, 
                              text=True)
        if result.returncode == 0 and result.stdout:
            print(f"⚠️  WARNING: Port is in use by another process:")
            print(result.stdout)
            return True
        print(f"✅ Port is available (not in use)")
        return False
    except FileNotFoundError:
        # lsof not installed, skip this check
        print("⚠️  'lsof' not installed, skipping port usage check")
        return False

def test_serial_connection():
    """Test opening and communicating with the serial port"""
    try:
        print(f"\nAttempting to open {PORT} at {BAUDRATE} baud...")
        ser = serial.Serial(
            port=PORT,
            baudrate=BAUDRATE,
            bytesize=serial.EIGHTBITS,
            parity=serial.PARITY_NONE,
            stopbits=serial.STOPBITS_ONE,
            timeout=2
        )
        
        print(f"✅ Successfully opened serial port!")
        print(f"   Port name: {ser.name}")
        print(f"   Baudrate: {ser.baudrate}")
        print(f"   Bytesize: {ser.bytesize}")
        print(f"   Parity: {ser.parity}")
        print(f"   Stopbits: {ser.stopbits}")
        print(f"   Timeout: {ser.timeout}")
        print(f"   Is open: {ser.is_open}")
        
        # Try to read any available data (Zigbee coordinators may send data)
        print(f"\nReading from port (5 seconds)...")
        start_time = time.time()
        data_received = False
        
        while time.time() - start_time < 5:
            if ser.in_waiting > 0:
                data = ser.read(ser.in_waiting)
                print(f"   Received {len(data)} bytes: {data.hex()}")
                data_received = True
            time.sleep(0.1)
        
        if not data_received:
            print("   No data received (this is OK - device may be idle)")
        
        # Test writing (some devices echo back)
        print(f"\nTesting write capability...")
        test_data = b'\x00'  # Null byte test
        try:
            ser.write(test_data)
            print(f"   ✅ Write successful")
        except Exception as e:
            print(f"   ⚠️  Write warning: {e}")
        
        ser.close()
        print(f"\n✅ Port closed successfully")
        return True
        
    except serial.SerialException as e:
        print(f"❌ ERROR opening serial port: {e}")
        print("\nCommon issues:")
        print("1. Port is already in use by another application")
        print("2. Wrong baudrate (try 38400 or 9600)")
        print("3. Device not properly connected")
        print("4. Driver issues")
        return False
    except PermissionError:
        print(f"❌ Permission denied: {PORT}")
        print("Fix: sudo usermod -a -G dialout $USER (then logout/login)")
        return False
    except Exception as e:
        print(f"❌ Unexpected error: {e}")
        return False

def main():
    """Main test function"""
    print("=" * 60)
    print("ttyUSB0 Port Verification Test")
    print("Device: SMLIGHT SLZB-07p7")
    print("=" * 60)
    print()
    
    # Check if pyserial is installed
    try:
        import serial
    except ImportError:
        print("❌ ERROR: pyserial not installed")
        print("Install with: pip3 install pyserial")
        print("Or: sudo apt-get install python3-serial")
        sys.exit(1)
    
    all_checks_passed = True
    
    # Step 1: Check if port exists
    if not check_port_exists():
        sys.exit(1)
    print()
    
    # Step 2: Check permissions
    if not check_permissions():
        sys.exit(1)
    print()
    
    # Step 3: Check if port is in use
    port_in_use = check_port_in_use()
    if port_in_use:
        print("⚠️  You may need to stop the process using the port first")
        response = input("Continue anyway? (y/n): ")
        if response.lower() != 'y':
            sys.exit(1)
    print()
    
    # Step 4: Test serial connection
    if not test_serial_connection():
        all_checks_passed = False
    
    print()
    print("=" * 60)
    if all_checks_passed:
        print("✅ ALL TESTS PASSED - Port is working correctly!")
        print("\nYou can now configure zigbee2mqtt with:")
        print(f"  serial:")
        print(f"    port: {PORT}")
        print(f"    adapter: zstack")
    else:
        print("❌ SOME TESTS FAILED - Please review errors above")
    print("=" * 60)
    
    return 0 if all_checks_passed else 1

if __name__ == "__main__":
    sys.exit(main())
