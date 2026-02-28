# List All MQTT Topics from MQTT Server

## Method 1: Subscribe to All Topics (Wildcard)

### Using mosquitto_sub

```bash
# Subscribe to all topics (requires broker support)
mosquitto_sub -h localhost -t '#' -v

# Or for remote broker
mosquitto_sub -h 10.0.0.202 -t '#' -v

# With credentials (if needed)
mosquitto_sub -h localhost -u username -P password -t '#' -v

# Save to file
mosquitto_sub -h localhost -t '#' -v > mqtt-topics.log
```

**Note:** `#` is a wildcard that matches all topics.

### Using Node.js Script

Create `list-mqtt-topics.js`:

```javascript
const mqtt = require('mqtt');

const MQTT_BROKER = 'mqtt://localhost:1883';
// Or: 'mqtt://10.0.0.202:1883'

const client = mqtt.connect(MQTT_BROKER);

const topics = new Set();

client.on('connect', () => {
    console.log('✅ Connected to MQTT broker');
    console.log('📥 Subscribing to all topics (#)...\n');
    
    // Subscribe to all topics
    client.subscribe('#', (err) => {
        if (err) {
            console.error('❌ Error subscribing:', err);
        } else {
            console.log('✅ Subscribed to all topics');
            console.log('📊 Listening for messages...\n');
        }
    });
});

client.on('message', (topic, message) => {
    if (!topics.has(topic)) {
        topics.add(topic);
        console.log(`📌 New topic: ${topic}`);
        console.log(`   Message: ${message.toString().substring(0, 100)}...`);
        console.log('');
    }
});

// Display summary after 30 seconds
setTimeout(() => {
    console.log('\n=== Topic Summary ===');
    console.log(`Total unique topics found: ${topics.size}\n`);
    console.log('Topics:');
    Array.from(topics).sort().forEach(topic => {
        console.log(`  - ${topic}`);
    });
    client.end();
    process.exit(0);
}, 30000);
```

Run:
```bash
npm install mqtt
node list-mqtt-topics.js
```

## Method 2: Subscribe to zigbee2mqtt Topics Specifically

### List zigbee2mqtt Topics

```bash
# Subscribe to all zigbee2mqtt topics
mosquitto_sub -h localhost -t 'zigbee2mqtt/#' -v

# Or with timestamps
mosquitto_sub -h localhost -t 'zigbee2mqtt/#' -v -F '%t %p'
```

### Common zigbee2mqtt Topics

```bash
# Bridge state
mosquitto_sub -h localhost -t 'zigbee2mqtt/bridge/state' -v

# Bridge info
mosquitto_sub -h localhost -t 'zigbee2mqtt/bridge/info' -v

# All device topics
mosquitto_sub -h localhost -t 'zigbee2mqtt/#' -v

# Specific device
mosquitto_sub -h localhost -t 'zigbee2mqtt/device-name' -v

# Logs
mosquitto_sub -h localhost -t 'zigbee2mqtt/bridge/logging' -v
```

## Method 3: Using MQTT Explorer (GUI Tool)

### Install MQTT Explorer

**On Mac:**
```bash
brew install --cask mqtt-explorer
```

**Or download from:**
https://mqtt-explorer.com/

### Connect and Browse

1. Open MQTT Explorer
2. Add new connection:
   - Host: `10.0.0.202` (your Raspberry Pi IP)
   - Port: `1883`
   - (Optional) Username/Password if configured
3. Click "Connect"
4. Browse all topics in the left panel
5. See message history and current values

## Method 4: Python Script to Discover Topics

Create `discover-topics.py`:

```python
#!/usr/bin/env python3
import paho.mqtt.client as mqtt
import time
import json

MQTT_BROKER = "localhost"  # or "10.0.0.202"
MQTT_PORT = 1883
topics = set()

def on_connect(client, userdata, flags, rc):
    print(f"✅ Connected with result code {rc}")
    client.subscribe("#")  # Subscribe to all topics

def on_message(client, userdata, msg):
    topic = msg.topic
    if topic not in topics:
        topics.add(topic)
        try:
            payload = json.loads(msg.payload.decode())
            print(f"📌 Topic: {topic}")
            print(f"   Payload: {json.dumps(payload, indent=2)[:200]}")
        except:
            print(f"📌 Topic: {topic}")
            print(f"   Payload: {msg.payload.decode()[:100]}")
        print()

def on_disconnect(client, userdata, rc):
    print("\n=== Topic Summary ===")
    print(f"Total topics found: {len(topics)}\n")
    for topic in sorted(topics):
        print(f"  - {topic}")

client = mqtt.Client()
client.on_connect = on_connect
client.on_message = on_message
client.on_disconnect = on_disconnect

client.connect(MQTT_BROKER, MQTT_PORT, 60)
client.loop_start()

try:
    time.sleep(30)  # Listen for 30 seconds
except KeyboardInterrupt:
    pass

client.loop_stop()
client.disconnect()
```

Install and run:
```bash
pip3 install paho-mqtt
python3 discover-topics.py
```

## Method 5: Check zigbee2mqtt Configuration

### View Configuration File

```bash
# If running in Docker
docker-compose exec zigbee2mqtt cat /app/data/configuration.yaml

# Or if installed directly
cat /opt/zigbee2mqtt/data/configuration.yaml
```

Look for:
```yaml
mqtt:
  base_topic: zigbee2mqtt  # All topics start with this
```

### Check zigbee2mqtt Documentation

Default topics structure:
- `zigbee2mqtt/bridge/state` - Bridge state
- `zigbee2mqtt/bridge/info` - Bridge info
- `zigbee2mqtt/bridge/logging` - Logs
- `zigbee2mqtt/<device-name>` - Device state
- `zigbee2mqtt/<device-name>/set` - Set device state
- `zigbee2mqtt/<device-name>/get` - Get device state

## Method 6: Monitor MQTT Traffic

### Capture All Traffic

```bash
# Save all messages to file
mosquitto_sub -h localhost -t '#' -v > all-topics.log

# Then analyze
cat all-topics.log | grep -o '^[^ ]*' | sort -u
```

### Real-time Monitoring Script

Create `monitor-mqtt.js`:

```javascript
const mqtt = require('mqtt');

const client = mqtt.connect('mqtt://localhost:1883');
const topics = new Map();

client.on('connect', () => {
    console.log('✅ Connected');
    client.subscribe('#');
    
    // Show summary every 10 seconds
    setInterval(() => {
        console.log(`\n📊 Total topics: ${topics.size}`);
    }, 10000);
});

client.on('message', (topic, message) => {
    topics.set(topic, {
        lastUpdate: new Date(),
        message: message.toString(),
        count: (topics.get(topic)?.count || 0) + 1
    });
    
    console.log(`📨 ${topic}`);
    console.log(`   ${message.toString().substring(0, 80)}`);
});

// Display all topics on exit
process.on('SIGINT', () => {
    console.log('\n\n=== All Topics ===');
    Array.from(topics.keys()).sort().forEach(topic => {
        const data = topics.get(topic);
        console.log(`${topic} (${data.count} messages)`);
    });
    client.end();
    process.exit(0);
});
```

## Method 7: Using MQTT.fx (GUI Tool)

1. Download MQTT.fx: http://www.mqttfx.org/
2. Connect to broker: `10.0.0.202:1883`
3. Subscribe to `#` (all topics)
4. Browse messages in the interface

## Quick Command Reference

### List zigbee2mqtt Topics Only

```bash
# Subscribe to all zigbee2mqtt topics
mosquitto_sub -h localhost -t 'zigbee2mqtt/#' -v

# Extract unique topics
mosquitto_sub -h localhost -t 'zigbee2mqtt/#' -v | \
  awk '{print $1}' | sort -u > zigbee2mqtt-topics.txt
```

### List All Topics (One-liner)

```bash
# Connect, subscribe, collect for 30 seconds, show summary
timeout 30 mosquitto_sub -h localhost -t '#' -v | \
  awk '{print $1}' | sort -u
```

### Monitor Specific Topic Pattern

```bash
# All bridge topics
mosquitto_sub -h localhost -t 'zigbee2mqtt/bridge/+' -v

# All device topics
mosquitto_sub -h localhost -t 'zigbee2mqtt/+/set' -v
```

## Complete Topic Discovery Script

Create `discover-all-topics.js`:

```javascript
#!/usr/bin/env node
const mqtt = require('mqtt');

const BROKER = process.argv[2] || 'mqtt://localhost:1883';
const TIMEOUT = parseInt(process.argv[3]) || 30;

console.log(`🔍 Discovering MQTT topics from ${BROKER}`);
console.log(`⏱️  Listening for ${TIMEOUT} seconds...\n`);

const client = mqtt.connect(BROKER);
const topics = new Map();

client.on('connect', () => {
    console.log('✅ Connected to broker\n');
    client.subscribe('#', { qos: 0 });
});

client.on('message', (topic, message) => {
    if (!topics.has(topic)) {
        topics.set(topic, {
            firstSeen: new Date(),
            messageCount: 0,
            sample: message.toString().substring(0, 100)
        });
        console.log(`📌 ${topic}`);
    }
    
    const data = topics.get(topic);
    data.messageCount++;
    data.lastSeen = new Date();
});

setTimeout(() => {
    console.log('\n\n=== DISCOVERY SUMMARY ===');
    console.log(`Total unique topics: ${topics.size}\n`);
    
    const sortedTopics = Array.from(topics.entries())
        .sort((a, b) => a[0].localeCompare(b[0]));
    
    sortedTopics.forEach(([topic, data]) => {
        console.log(`📌 ${topic}`);
        console.log(`   Messages: ${data.messageCount}`);
        console.log(`   Sample: ${data.sample}`);
        console.log('');
    });
    
    client.end();
    process.exit(0);
}, TIMEOUT * 1000);
```

Usage:
```bash
chmod +x discover-all-topics.js
node discover-all-topics.js mqtt://10.0.0.202:1883 30
```

## Summary

**Quick Methods:**

1. **Wildcard subscription:**
   ```bash
   mosquitto_sub -h localhost -t '#' -v
   ```

2. **zigbee2mqtt topics only:**
   ```bash
   mosquitto_sub -h localhost -t 'zigbee2mqtt/#' -v
   ```

3. **Use MQTT Explorer** (GUI tool - easiest)

4. **Node.js script** (provided above)

The wildcard subscription (`#`) will show you all active topics on your MQTT broker!


