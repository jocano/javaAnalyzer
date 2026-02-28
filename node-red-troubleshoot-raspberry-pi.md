# Node-RED Troubleshooting on Raspberry Pi

## Quick Diagnostic Commands

### 1. Check if Node-RED is Running

```bash
# Check service status
sudo systemctl status nodered

# Check if process is running
ps aux | grep node-red

# Check if port is listening
sudo netstat -tlnp | grep 1880
# Or
sudo ss -tlnp | grep 1880
```

### 2. Check Node-RED Logs

```bash
# View service logs
sudo journalctl -u nodered -f

# Or check log file
tail -f ~/.node-red/node-red.log
```

### 3. Check Port and Network

```bash
# Check if port 1880 is open
sudo lsof -i :1880

# Test local connection
curl http://localhost:1880

# Check firewall
sudo ufw status
```

## Common Issues and Solutions

### Issue 1: Node-RED Service Not Running

**Symptoms:**
- Can't access web interface
- Service shows as inactive/failed

**Solutions:**

```bash
# Start Node-RED service
sudo systemctl start nodered

# Enable to start on boot
sudo systemctl enable nodered

# Check status
sudo systemctl status nodered

# Restart if needed
sudo systemctl restart nodered
```

### Issue 2: Port 1880 Not Accessible

**Symptoms:**
- Can't connect to http://raspberry-pi-ip:1880
- Connection refused or timeout

**Solutions:**

```bash
# Check if Node-RED is listening on all interfaces
# Edit settings file
nano ~/.node-red/settings.js

# Look for:
# uiHost: "0.0.0.0",  // Should be 0.0.0.0 to accept connections from network

# Or check if it's only listening on localhost
sudo netstat -tlnp | grep 1880
# Should show: 0.0.0.0:1880 (not 127.0.0.1:1880)

# Restart after changes
sudo systemctl restart nodered
```

### Issue 3: Node-RED Running in Docker

**If Node-RED is in Docker:**

```bash
# Check container status
docker ps | grep node-red
# Or
docker-compose ps node-red

# Check logs
docker logs node-red
# Or
docker-compose logs node-red

# Check if port is mapped correctly
docker ps | grep node-red
# Should show: 0.0.0.0:1880->1880/tcp

# Restart container
docker restart node-red
# Or
docker-compose restart node-red
```

### Issue 4: Permission Issues

**Symptoms:**
- Service fails to start
- Permission denied errors in logs

**Solutions:**

```bash
# Check Node-RED user
sudo systemctl show nodered | grep User

# Fix permissions
sudo chown -R pi:pi ~/.node-red

# Check if user has correct permissions
sudo usermod -a -G dialout,gpio,i2c,spi node-red
```

### Issue 5: Port Already in Use

**Symptoms:**
- Error: "Port 1880 already in use"
- Service fails to start

**Solutions:**

```bash
# Find what's using port 1880
sudo lsof -i :1880
# Or
sudo fuser 1880/tcp

# Kill the process
sudo kill -9 <PID>

# Or change Node-RED port
nano ~/.node-red/settings.js
# Change: uiPort: 1880, to: uiPort: 1881,
```

### Issue 6: Node-RED Can't Connect to MQTT

**Symptoms:**
- MQTT nodes show "disconnected"
- Error: "Connection refused"

**Solutions:**

```bash
# Test MQTT connection from Node-RED container/host
# If MQTT is on same machine:
# Use: mqtt://localhost:1883
# Or: mqtt://127.0.0.1:1883

# If MQTT is in Docker:
# Use service name: mqtt://mosquitto:1883

# Test MQTT from command line
mosquitto_pub -h localhost -t test -m "hello"
mosquitto_sub -h localhost -t test
```

### Issue 7: Node-RED Not Starting After Reboot

**Solutions:**

```bash
# Enable service to start on boot
sudo systemctl enable nodered

# Check if enabled
systemctl is-enabled nodered
# Should show: enabled
```

## Complete Diagnostic Script

Create `check-node-red.sh`:

```bash
#!/bin/bash
echo "=== Node-RED Diagnostic ==="
echo ""

echo "1. Service Status:"
sudo systemctl status nodered --no-pager | head -10
echo ""

echo "2. Process Check:"
ps aux | grep node-red | grep -v grep || echo "   ❌ Node-RED not running"
echo ""

echo "3. Port Check:"
if sudo netstat -tlnp 2>/dev/null | grep -q ":1880"; then
    echo "   ✅ Port 1880 is listening"
    sudo netstat -tlnp | grep 1880
else
    echo "   ❌ Port 1880 not listening"
fi
echo ""

echo "4. Local Connection Test:"
if curl -s http://localhost:1880 > /dev/null; then
    echo "   ✅ Can connect to localhost:1880"
else
    echo "   ❌ Cannot connect to localhost:1880"
fi
echo ""

echo "5. Recent Logs:"
sudo journalctl -u nodered --no-pager -n 10
echo ""

echo "6. Node-RED Directory:"
ls -la ~/.node-red/ 2>/dev/null || echo "   ⚠️  .node-red directory not found"
echo ""

echo "=== Diagnostic Complete ==="
```

Make executable and run:
```bash
chmod +x check-node-red.sh
./check-node-red.sh
```

## Installation Check

If Node-RED is not installed:

```bash
# Install Node-RED (Raspberry Pi OS)
bash <(curl -sL https://raw.githubusercontent.com/node-red/linux-installers/master/deb/update-nodejs-and-nodered)

# Or using npm
sudo npm install -g --unsafe-perm node-red

# Start service
sudo systemctl start nodered
sudo systemctl enable nodered
```

## Docker Installation

If using Docker:

```bash
# Check docker-compose.yml
cat docker-compose.yml | grep -A 10 node-red

# Should have something like:
# node-red:
#   image: nodered/node-red
#   ports:
#     - "1880:1880"
#   volumes:
#     - node-red-data:/data

# Start container
docker-compose up -d node-red

# Check logs
docker-compose logs -f node-red
```

## Network Configuration

### Allow External Access

```bash
# Check settings.js
nano ~/.node-red/settings.js

# Ensure:
uiHost: "0.0.0.0",  // Listen on all interfaces

# Or if using Docker, check port mapping:
# ports:
#   - "0.0.0.0:1880:1880"
```

### Firewall Configuration

```bash
# Allow port 1880 through firewall
sudo ufw allow 1880/tcp

# Or if using iptables
sudo iptables -A INPUT -p tcp --dport 1880 -j ACCEPT
```

## Reset Node-RED

If nothing works, reset Node-RED:

```bash
# Stop service
sudo systemctl stop nodered

# Backup current configuration
cp -r ~/.node-red ~/.node-red.backup

# Remove flows (keeps settings)
rm ~/.node-red/flows_*.json

# Or complete reset (removes everything)
# rm -rf ~/.node-red

# Start service
sudo systemctl start nodered
```

## Common MQTT Connection Issues

### If Node-RED can't connect to MQTT:

```bash
# 1. Check MQTT broker is running
docker-compose ps mosquitto
# Or
systemctl status mosquitto

# 2. Test MQTT connection
mosquitto_pub -h localhost -t test -m "test"
mosquitto_sub -h localhost -t test

# 3. In Node-RED, use correct MQTT server:
# - Same machine: mqtt://localhost:1883
# - Docker service: mqtt://mosquitto:1883
# - Remote: mqtt://10.0.0.83:1883
```

## Quick Fixes

### Fix 1: Restart Everything
```bash
sudo systemctl restart nodered
```

### Fix 2: Check and Fix Port
```bash
# Check what's using port 1880
sudo lsof -i :1880

# Restart Node-RED
sudo systemctl restart nodered
```

### Fix 3: Check Logs for Errors
```bash
sudo journalctl -u nodered -n 50 | grep -i error
```

### Fix 4: Reinstall Node-RED
```bash
# Backup
cp -r ~/.node-red ~/.node-red.backup

# Reinstall
bash <(curl -sL https://raw.githubusercontent.com/node-red/linux-installers/master/deb/update-nodejs-and-nodered)
```

## Access Node-RED

Once running, access at:
- Local: http://localhost:1880
- Network: http://<raspberry-pi-ip>:1880

## Summary

**Most common fixes:**
1. `sudo systemctl restart nodered` - Restart service
2. Check port 1880 is not in use
3. Verify service is enabled: `sudo systemctl enable nodered`
4. Check logs: `sudo journalctl -u nodered -f`
5. For Docker: `docker-compose restart node-red`
