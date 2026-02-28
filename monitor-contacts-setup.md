# Setup: Monitor Door Contacts Status (JavaScript)

## Quick Setup

### Step 1: Install Dependencies

```bash
# Install mqtt library
npm install mqtt

# Or if you have package.json
npm init -y
npm install mqtt
```

### Step 2: Configure Contact Devices

Edit `monitor-contacts-status.js` and update the contacts array:

```javascript
contacts: [
    'door_front',      // Your actual device names
    'door_back',
    'door_garage',
    'window_living_room'
    // Add more here
],
```

### Step 3: Configure MQTT Broker

**Option A: Command line argument**
```bash
node monitor-contacts-status.js mqtt://10.0.0.202:1883
```

**Option B: Environment variable**
```bash
export MQTT_BROKER="mqtt://10.0.0.202:1883"
node monitor-contacts-status.js
```

**Option C: Edit script default**
```javascript
mqttBroker: 'mqtt://10.0.0.202:1883',  // Your Raspberry Pi IP
```

### Step 4: Run the Program

```bash
# Default (localhost, 30s interval)
node monitor-contacts-status.js

# Custom broker
node monitor-contacts-status.js mqtt://10.0.0.202:1883

# Custom broker and interval
node monitor-contacts-status.js mqtt://10.0.0.202:1883 60
```

## Output Example

```
🚪 Door Contacts Status Monitor
============================================================
📅 Last Update: 1/4/2026, 10:30:45 AM
⏱️  Last message received: 5s ago
============================================================

📋 Contact Status:
------------------------------------------------------------
Device                   Status     Last Update
------------------------------------------------------------
🟢 door_front            CLOSED     10:30:40 AM
🟢 door_back             CLOSED     10:30:42 AM
🔴 door_garage           OPEN       10:30:38 AM
🟢 window_living_room     CLOSED     10:30:44 AM
------------------------------------------------------------

📊 Summary:
   Total: 4
   🟢 Closed: 3
   🔴 Open: 1
   ⚪ Unknown: 0

⚠️  WARNING: 1 contact(s) are OPEN:
   - door_garage

⏳ Next update in 30 seconds...
   (Press Ctrl+C to exit)
```

## Configuration Options

### Update Contact Device Names

Edit the `contacts` array in the script:
```javascript
contacts: [
    'your_device_name_1',
    'your_device_name_2',
    // Add all your contact devices
],
```

### Adjust Check Interval

```bash
# Check every 60 seconds
node monitor-contacts-status.js mqtt://localhost:1883 60

# Check every 10 seconds
node monitor-contacts-status.js mqtt://localhost:1883 10
```

### MQTT Authentication

```bash
export MQTT_USERNAME="your_username"
export MQTT_PASSWORD="your_password"
node monitor-contacts-status.js
```

## Run Continuously

### Using PM2

```bash
# Install PM2
npm install -g pm2

# Start program
pm2 start monitor-contacts-status.js --name contacts-monitor -- mqtt://10.0.0.202:1883 30

# Save PM2 config
pm2 save

# Setup PM2 to start on boot
pm2 startup
# Follow instructions

# View logs
pm2 logs contacts-monitor

# View status
pm2 status
```

### Using systemd (Linux)

Create `/etc/systemd/system/contacts-monitor.service`:

```ini
[Unit]
Description=Door Contacts Status Monitor
After=network.target

[Service]
Type=simple
User=pi
WorkingDirectory=/home/pi
ExecStart=/usr/bin/node /home/pi/monitor-contacts-status.js mqtt://localhost:1883 30
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
```

Enable and start:
```bash
sudo systemctl daemon-reload
sudo systemctl enable contacts-monitor.service
sudo systemctl start contacts-monitor.service
sudo systemctl status contacts-monitor.service
```

### Using nohup

```bash
nohup node monitor-contacts-status.js mqtt://10.0.0.202:1883 30 > contacts-monitor.log 2>&1 &
```

## Troubleshooting

### Issue: No Status Updates

**Check:**
1. MQTT broker is running
2. Device names match exactly
3. zigbee2mqtt is publishing messages
4. Network connectivity

**Test:**
```bash
# Subscribe to see messages
mosquitto_sub -h localhost -t 'zigbee2mqtt/#' -v
```

### Issue: All Status Unknown

**Solution:**
- Verify device names match zigbee2mqtt friendly names
- Check message format in zigbee2mqtt
- Adjust contact state parsing in script

### Issue: Wrong Contact State

**Solution:**
Your devices might use different format. Check actual message:
```bash
mosquitto_sub -h localhost -t 'zigbee2mqtt/door_front' -v
```

Then adjust parsing in script:
```javascript
// Try different extraction methods:
const contactState = data.contact || 
                    data.state?.contact ||
                    data.contact_sensor ||
                    data.state;
```

## Customization

### Change Display Format

Edit `displayStatus()` function to customize output format.

### Add Email/SMS Alerts

Add alert function:
```javascript
function sendAlert(openContacts) {
    // Send email, SMS, or other notification
    console.log(`ALERT: ${openContacts.length} contacts open!`);
}
```

### Save Status to File

```javascript
const fs = require('fs');

function saveStatus() {
    const status = {
        timestamp: new Date().toISOString(),
        contacts: Array.from(contactStatus.values())
    };
    fs.writeFileSync('contacts-status.json', JSON.stringify(status, null, 2));
}
```

## Summary

**Quick Start:**
1. `npm install mqtt`
2. Update `contacts` array with your device names
3. `node monitor-contacts-status.js mqtt://10.0.0.202:1883`

**The program will:**
- Connect to MQTT broker
- Subscribe to zigbee2mqtt topics
- Monitor your contact devices
- Display status every 30 seconds (or custom interval)
- Show alerts if any contacts are open

Perfect for monitoring door contacts from zigbee2mqtt!
