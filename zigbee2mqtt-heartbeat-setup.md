# Zigbee2MQTT Heartbeat Monitor Setup

## Quick Setup

### Step 1: Install Dependencies

```bash
# Install mqtt library
npm install mqtt

# Or if you have a package.json
npm init -y
npm install mqtt
```

### Step 2: Configure MQTT Connection

Edit the script or set environment variables:

```bash
# Set MQTT broker address (if not localhost)
export MQTT_BROKER="mqtt://10.0.0.202:1883"

# Set credentials (if needed)
export MQTT_USERNAME="your_username"
export MQTT_PASSWORD="your_password"
```

Or edit the script directly:
```javascript
mqttBroker: 'mqtt://10.0.0.202:1883',  // Your Raspberry Pi IP
```

### Step 3: Make Script Executable

```bash
chmod +x zigbee2mqtt-heartbeat-monitor.js
```

### Step 4: Run the Script

```bash
# Run with default 30-second interval
node zigbee2mqtt-heartbeat-monitor.js

# Run with custom interval (60 seconds)
node zigbee2mqtt-heartbeat-monitor.js 60

# Run in background
nohup node zigbee2mqtt-heartbeat-monitor.js > heartbeat.log 2>&1 &
```

## Usage Examples

### Example 1: Basic Usage (30 seconds)

```bash
node zigbee2mqtt-heartbeat-monitor.js
```

Output:
```
✅ Connected to MQTT broker
📊 Heartbeat interval: 30 seconds
✅ Heartbeat #1 sent at 10:30:45
✅ zigbee2mqtt bridge is ONLINE
✅ Heartbeat #2 sent at 10:31:15
```

### Example 2: Custom Interval (60 seconds)

```bash
node zigbee2mqtt-heartbeat-monitor.js 60
```

### Example 3: Connect to Remote MQTT

```bash
MQTT_BROKER="mqtt://10.0.0.202:1883" node zigbee2mqtt-heartbeat-monitor.js
```

## What the Script Does

1. **Connects to MQTT broker** - Connects to your MQTT broker
2. **Subscribes to bridge state** - Monitors `zigbee2mqtt/bridge/state`
3. **Sends heartbeats** - Publishes heartbeat messages every X seconds
4. **Monitors zigbee2mqtt** - Checks if bridge is online/offline
5. **Logs status** - Shows connection and bridge status

## Heartbeat Message Format

The script publishes messages to: `zigbee2mqtt/heartbeat/monitor`

Message format:
```json
{
  "timestamp": "2026-01-04T10:30:45.123Z",
  "hostname": "raspberrypi",
  "count": 1,
  "bridgeState": "online",
  "interval": 30,
  "lastBridgeStateUpdate": "2026-01-04T10:30:40.000Z"
}
```

## Monitor Heartbeats

### Subscribe to Heartbeat Topic

```bash
# Using mosquitto_sub
mosquitto_sub -h localhost -t zigbee2mqtt/heartbeat/monitor

# Or from Node.js
node -e "const mqtt=require('mqtt');const c=mqtt.connect('mqtt://localhost:1883');c.on('connect',()=>{c.subscribe('zigbee2mqtt/heartbeat/monitor');});c.on('message',(t,m)=>{console.log(JSON.parse(m));});"
```

### Check Bridge State

```bash
# Subscribe to bridge state
mosquitto_sub -h localhost -t zigbee2mqtt/bridge/state
```

## Run as a Service (systemd)

Create a systemd service to run continuously:

### Step 1: Create Service File

```bash
sudo nano /etc/systemd/system/zigbee2mqtt-monitor.service
```

### Step 2: Add Service Configuration

```ini
[Unit]
Description=Zigbee2MQTT Heartbeat Monitor
After=network.target

[Service]
Type=simple
User=pi
WorkingDirectory=/home/pi
ExecStart=/usr/bin/node /home/pi/zigbee2mqtt-heartbeat-monitor.js 30
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
```

**Note:** Adjust paths and username as needed.

### Step 3: Enable and Start Service

```bash
# Reload systemd
sudo systemctl daemon-reload

# Enable service (start on boot)
sudo systemctl enable zigbee2mqtt-monitor.service

# Start service
sudo systemctl start zigbee2mqtt-monitor.service

# Check status
sudo systemctl status zigbee2mqtt-monitor.service

# View logs
sudo journalctl -u zigbee2mqtt-monitor.service -f
```

## Run with PM2 (Process Manager)

### Install PM2

```bash
npm install -g pm2
```

### Start Script

```bash
# Start with PM2
pm2 start zigbee2mqtt-heartbeat-monitor.js --name zigbee2mqtt-monitor -- 30

# Save PM2 configuration
pm2 save

# Setup PM2 to start on boot
pm2 startup
# Follow the instructions it gives you

# View logs
pm2 logs zigbee2mqtt-monitor

# View status
pm2 status

# Stop
pm2 stop zigbee2mqtt-monitor

# Restart
pm2 restart zigbee2mqtt-monitor
```

## Simple Version (Minimal Script)

If you want a simpler version:

```javascript
const mqtt = require('mqtt');

const INTERVAL = 30; // seconds
const client = mqtt.connect('mqtt://localhost:1883');

client.on('connect', () => {
    console.log('✅ Connected');
    
    setInterval(() => {
        const heartbeat = {
            timestamp: new Date().toISOString(),
            status: 'alive'
        };
        client.publish('zigbee2mqtt/heartbeat', JSON.stringify(heartbeat));
        console.log('💓 Heartbeat sent');
    }, INTERVAL * 1000);
});
```

## Troubleshooting

### Issue: Cannot connect to MQTT broker

**Solution:**
```bash
# Check if MQTT broker is running
docker-compose ps mosquitto
# Or
systemctl status mosquitto

# Test connection
mosquitto_pub -h localhost -t test -m "test"
```

### Issue: Script stops after some time

**Solution:**
- Use systemd service or PM2 for auto-restart
- Check logs for errors
- Ensure MQTT broker is stable

### Issue: No bridge state updates

**Solution:**
```bash
# Check if zigbee2mqtt is running
docker-compose ps zigbee2mqtt

# Check zigbee2mqtt logs
docker-compose logs zigbee2mqtt | grep -i "bridge/state"
```

## Summary

**Quick Start:**
```bash
# 1. Install mqtt
npm install mqtt

# 2. Edit script to set correct MQTT broker IP
nano zigbee2mqtt-heartbeat-monitor.js

# 3. Run
node zigbee2mqtt-heartbeat-monitor.js 30

# 4. (Optional) Run as service
sudo systemctl enable zigbee2mqtt-monitor.service
sudo systemctl start zigbee2mqtt-monitor.service
```

The script will verify zigbee2mqtt is running by monitoring its bridge state and sending periodic heartbeats!


