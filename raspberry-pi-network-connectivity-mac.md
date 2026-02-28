# Make Raspberry Pi (10.0.0.202) Visible from Mac on Same Network

## Step 1: Verify Both Devices Are on Same Network

### On Raspberry Pi:
```bash
# Check IP and network info
hostname -I
# Should show: 10.0.0.202

# Check network interface
ip addr show

# Check gateway
ip route | grep default
# Should show: default via 10.0.0.1
```

### On Mac:
```bash
# Check Mac's IP address
ifconfig | grep "inet " | grep -v 127.0.0.1

# Or use
ipconfig getifaddr en0  # For WiFi
ipconfig getifaddr en1  # For Ethernet

# Should show an IP like: 10.0.0.XXX (same subnet as 10.0.0.202)
```

**Verify:** Both should be in the `10.0.0.x` range (same subnet)

## Step 2: Test Basic Connectivity

### From Mac, ping Raspberry Pi:
```bash
# Ping Raspberry Pi
ping 10.0.0.202

# Should see:
# 64 bytes from 10.0.0.202: icmp_seq=0 ttl=64 time=XX ms
```

### From Raspberry Pi, ping Mac:
```bash
# First find Mac's IP (from Mac)
ipconfig getifaddr en0

# Then from Raspberry Pi
ping <mac-ip>
# Example: ping 10.0.0.100
```

## Step 3: Check Firewall Settings

### On Raspberry Pi:

```bash
# Check if firewall is blocking connections
sudo ufw status

# If firewall is active, allow incoming connections
sudo ufw allow 22/tcp    # SSH
sudo ufw allow 1880/tcp  # Node-RED
sudo ufw allow 8080/tcp  # zigbee2mqtt web UI
sudo ufw allow 1883/tcp  # MQTT

# Or disable firewall temporarily for testing
sudo ufw disable

# Check iptables
sudo iptables -L
```

### On Mac:
```bash
# Check firewall status
sudo /usr/libexec/ApplicationFirewall/socketfilterfw --getglobalstate

# If firewall is enabled, allow connections
# Go to System Settings → Network → Firewall
# Or disable temporarily for testing
```

## Step 4: Enable SSH on Raspberry Pi (For Remote Access)

```bash
# On Raspberry Pi, enable SSH
sudo systemctl enable ssh
sudo systemctl start ssh

# Check SSH status
sudo systemctl status ssh

# Verify SSH port is listening
sudo netstat -tlnp | grep 22
# Or
sudo ss -tlnp | grep 22
```

## Step 5: Test Specific Services

### Test SSH from Mac:
```bash
# Try SSH connection
ssh pi@10.0.0.202

# Or if different username
ssh <username>@10.0.0.202
```

### Test HTTP Services from Mac:
```bash
# Test Node-RED (if running)
curl http://10.0.0.202:1880

# Test zigbee2mqtt (if running)
curl http://10.0.0.202:8080

# Test any other web service
curl http://10.0.0.202:<port>
```

### Test MQTT from Mac:
```bash
# If you have mosquitto client installed
mosquitto_pub -h 10.0.0.202 -t test -m "hello"
mosquitto_sub -h 10.0.0.202 -t test
```

## Step 6: Network Configuration

### Check Network Subnet Mask:
```bash
# On Raspberry Pi
ip addr show | grep inet
# Should show: 10.0.0.202/24 (means subnet mask 255.255.255.0)

# On Mac
ifconfig | grep "inet " | grep "10.0.0"
# Should show similar subnet
```

### Verify Same Router/Gateway:
```bash
# Both should have same gateway IP
# On Raspberry Pi:
ip route | grep default
# Example: default via 10.0.0.1

# On Mac:
netstat -nr | grep default
# Should show: 10.0.0.1
```

## Step 7: Set Static IP (Optional but Recommended)

If IP keeps changing, set static IP on Raspberry Pi:

```bash
# Edit network configuration
sudo nano /etc/dhcpcd.conf

# Add at the end:
interface wlan0  # or eth0 for ethernet
static ip_address=10.0.0.202/24
static routers=10.0.0.1
static domain_name_servers=10.0.0.1 8.8.8.8
```

Then restart:
```bash
sudo systemctl restart dhcpcd
# Or reboot
sudo reboot
```

## Step 8: Use Hostname Instead of IP (Bonjour/mDNS)

### On Raspberry Pi, install Avahi:
```bash
# Install Avahi (for .local hostname)
sudo apt-get update
sudo apt-get install avahi-daemon

# Start service
sudo systemctl start avahi-daemon
sudo systemctl enable avahi-daemon

# Check hostname
hostname
# Example: raspberrypi

# Now you can access as: raspberrypi.local
```

### From Mac, access by hostname:
```bash
# Ping by hostname
ping raspberrypi.local

# SSH by hostname
ssh pi@raspberrypi.local

# Access web services
http://raspberrypi.local:1880  # Node-RED
http://raspberrypi.local:8080  # zigbee2mqtt
```

## Quick Diagnostic Commands

### From Mac:
```bash
# 1. Check if on same network
ifconfig | grep "inet " | grep "10.0.0"

# 2. Ping Raspberry Pi
ping -c 4 10.0.0.202

# 3. Test SSH
ssh -v pi@10.0.0.202

# 4. Test port connectivity
nc -zv 10.0.0.202 22    # SSH
nc -zv 10.0.0.202 1880  # Node-RED
nc -zv 10.0.0.202 8080  # zigbee2mqtt

# 5. Scan open ports
nmap -p 22,80,1880,1883,8080 10.0.0.202
```

### From Raspberry Pi:
```bash
# 1. Check IP
hostname -I

# 2. Check network interface
ip addr show

# 3. Check listening ports
sudo netstat -tlnp

# 4. Check firewall
sudo ufw status

# 5. Test connectivity to Mac
ping <mac-ip>
```

## Common Issues and Fixes

### Issue 1: Cannot Ping Raspberry Pi

**Symptoms:** `ping 10.0.0.202` fails with "Request timeout" or "Host unreachable"

**Solutions:**
```bash
# On Raspberry Pi:
# Check if network interface is up
ip link show

# Restart network
sudo systemctl restart networking
# Or
sudo ifdown wlan0 && sudo ifup wlan0

# Check if IP is actually assigned
ip addr show wlan0  # or eth0
```

### Issue 2: Firewall Blocking

**Solutions:**
```bash
# On Raspberry Pi, check and configure firewall
sudo ufw status verbose

# Allow specific ports
sudo ufw allow from 10.0.0.0/24 to any port 22
sudo ufw allow from 10.0.0.0/24 to any port 1880
sudo ufw allow from 10.0.0.0/24 to any port 8080

# Reload firewall
sudo ufw reload
```

### Issue 3: Different Network Subnets

**Symptoms:** Mac is on `192.168.1.x` but Pi is on `10.0.0.x`

**Solutions:**
- Connect both to same WiFi network
- Check router configuration
- Verify DHCP settings

### Issue 4: Router Isolation (AP Isolation)

**Symptoms:** Devices can't see each other even on same network

**Solutions:**
- Check router settings for "AP Isolation" or "Client Isolation"
- Disable it if enabled
- Some guest networks have this enabled by default

## Quick Setup Script

Create `check-network-connectivity.sh` on Mac:

```bash
#!/bin/bash
PI_IP="10.0.0.202"

echo "=== Network Connectivity Check ==="
echo ""

echo "1. Mac IP Address:"
ifconfig | grep "inet " | grep -v 127.0.0.1
echo ""

echo "2. Pinging Raspberry Pi ($PI_IP)..."
if ping -c 2 $PI_IP > /dev/null 2>&1; then
    echo "   ✅ Raspberry Pi is reachable"
else
    echo "   ❌ Cannot reach Raspberry Pi"
fi
echo ""

echo "3. Testing SSH (port 22)..."
if nc -zv $PI_IP 22 > /dev/null 2>&1; then
    echo "   ✅ SSH port is open"
else
    echo "   ❌ SSH port is closed or blocked"
fi
echo ""

echo "4. Testing Node-RED (port 1880)..."
if nc -zv $PI_IP 1880 > /dev/null 2>&1; then
    echo "   ✅ Node-RED is accessible"
else
    echo "   ⚠️  Node-RED port is closed (may not be running)"
fi
echo ""

echo "5. Testing zigbee2mqtt (port 8080)..."
if nc -zv $PI_IP 8080 > /dev/null 2>&1; then
    echo "   ✅ zigbee2mqtt is accessible"
else
    echo "   ⚠️  zigbee2mqtt port is closed (may not be running)"
fi
echo ""

echo "6. Testing MQTT (port 1883)..."
if nc -zv $PI_IP 1883 > /dev/null 2>&1; then
    echo "   ✅ MQTT is accessible"
else
    echo "   ⚠️  MQTT port is closed (may not be running)"
fi
echo ""

echo "=== Check Complete ==="
```

Make executable and run:
```bash
chmod +x check-network-connectivity.sh
./check-network-connectivity.sh
```

## Verify Connectivity

### Quick Test from Mac:
```bash
# Test ping
ping -c 4 10.0.0.202

# Test SSH
ssh pi@10.0.0.202

# Test web services in browser
# http://10.0.0.202:1880  (Node-RED)
# http://10.0.0.202:8080  (zigbee2mqtt)
```

## Summary

**Quick checklist:**
1. ✅ Both devices on same network (10.0.0.x)
2. ✅ Can ping: `ping 10.0.0.202`
3. ✅ Firewall allows connections
4. ✅ Services are running on Raspberry Pi
5. ✅ Ports are open and accessible

**Most common fix:**
```bash
# On Raspberry Pi:
sudo ufw allow from 10.0.0.0/24
sudo systemctl restart ssh
```

Your Raspberry Pi should now be visible and accessible from your Mac!


