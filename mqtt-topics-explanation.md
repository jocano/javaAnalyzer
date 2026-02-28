# MQTT Topics Explanation: Why Topics Appear and Disappear

## How MQTT Topics Work

### Key Concept: Topics Are Not Persistent

**Important:** In MQTT, topics don't exist as stored entities. They are:
- **Routing paths** for messages
- **Created on-the-fly** when messages are published
- **Temporary** - they exist only when messages flow to them

### Why Topics Appear and Disappear

1. **Topics only appear when messages are published**
   - If no message is published to a topic, it doesn't exist
   - When you subscribe to `#`, you only see topics that have **active messages**

2. **zigbee2mqtt publishes to different topics based on events:**
   - Device updates → `zigbee2mqtt/<device-name>`
   - Bridge state changes → `zigbee2mqtt/bridge/state`
   - Device joins → `zigbee2mqtt/bridge/logging`
   - Only active devices publish messages

3. **Topics vary based on activity:**
   - Active devices publish frequently → their topics appear
   - Inactive devices don't publish → their topics disappear
   - Bridge publishes periodically → appears regularly

## Why zigbee2mqtt Topics Vary

### Topics That Always Appear (Regular Updates)

```bash
# Bridge state (published regularly)
zigbee2mqtt/bridge/state

# Bridge info (published on startup/changes)
zigbee2mqtt/bridge/info

# Bridge config (published on changes)
zigbee2mqtt/bridge/config
```

### Topics That Appear Only When Active

```bash
# Device topics (only when device reports)
zigbee2mqtt/<device-name>
zigbee2mqtt/<device-name>/set
zigbee2mqtt/<device-name>/get

# Logging (only when events occur)
zigbee2mqtt/bridge/logging

# Device events (only when devices join/leave)
zigbee2mqtt/bridge/event
```

### Example Scenarios

**Scenario 1: Device sends update**
- Topic appears: `zigbee2mqtt/sensor_living_room`
- Message published: `{"temperature": 22, "humidity": 50}`
- Topic exists while message is being processed

**Scenario 2: Device inactive**
- No messages published
- Topic doesn't appear in wildcard subscriptions
- Topic doesn't exist in broker

**Scenario 3: Device joins network**
- Topic appears: `zigbee2mqtt/bridge/logging`
- Message published: `{"type": "device_joined", ...}`
- Topic disappears after message is processed

## How to See All Configured Topics

### Method 1: Monitor Over Time

Since topics only appear when messages are published, you need to monitor over time to see all active topics:

```bash
# Monitor for extended period (capture all topics)
timeout 300 mosquitto_sub -h localhost -t 'zigbee2mqtt/#' -v | \
  tee all-topics.log | \
  awk '{print $1}' | \
  sort -u > unique-topics.txt

# View all unique topics found
cat unique-topics.txt
```

### Method 2: Check zigbee2mqtt Database

The actual device list is stored in zigbee2mqtt's database, not in MQTT topics:

```bash
# Check zigbee2mqtt database
docker-compose exec zigbee2mqtt cat /app/data/database.db

# Or via API
curl http://localhost:8080/api/devices

# Or via MQTT (request)
mosquitto_pub -h localhost -t zigbee2mqtt/bridge/request/device/options -m '{}'
```

### Method 3: Monitor Topic Activity Over Time

Create a script to collect all topics over time:

```javascript
// collect-topics-over-time.js
const mqtt = require('mqtt');
const fs = require('fs');

const client = mqtt.connect('mqtt://localhost:1883');
const allTopics = new Set();

client.on('connect', () => {
    client.subscribe('zigbee2mqtt/#');
    console.log('✅ Monitoring topics... (Press Ctrl+C to stop and see summary)');
});

client.on('message', (topic, message) => {
    allTopics.add(topic);
    console.log(`📌 Topic: ${topic} (Total: ${allTopics.size})`);
});

process.on('SIGINT', () => {
    console.log('\n\n=== All Topics Collected ===');
    const sorted = Array.from(allTopics).sort();
    sorted.forEach(topic => console.log(topic));
    
    // Save to file
    fs.writeFileSync('all-topics.txt', sorted.join('\n'));
    console.log(`\n✅ Saved ${sorted.length} topics to all-topics.txt`);
    
    client.end();
    process.exit(0);
});
```

Run for extended period:
```bash
node collect-topics-over-time.js
# Let it run for hours/days to capture all device activity
# Press Ctrl+C to see summary
```

## Understanding zigbee2mqtt Topic Patterns

### Always Present Topics (Regular Updates)

```bash
zigbee2mqtt/bridge/state          # Published every few seconds
zigbee2mqtt/bridge/info           # Published on startup
zigbee2mqtt/bridge/config         # Published when config changes
```

### Event-Based Topics (Only When Events Occur)

```bash
zigbee2mqtt/bridge/logging        # When events happen
zigbee2mqtt/bridge/event          # When devices join/leave
zigbee2mqtt/bridge/response       # When bridge responds
```

### Device Topics (Only When Devices Report)

```bash
zigbee2mqtt/<friendly_name>       # Device state (when device updates)
zigbee2mqtt/<friendly_name>/set   # Set device (when you publish)
zigbee2mqtt/<friendly_name>/get   # Get device (when you request)
```

## How to Get Complete Device List

### Method 1: Via zigbee2mqtt API

```bash
# Get all devices
curl http://localhost:8080/api/devices

# Or from Raspberry Pi
curl http://10.0.0.202:8080/api/devices
```

### Method 2: Via MQTT Request

```bash
# Request device list
mosquitto_pub -h localhost -t 'zigbee2mqtt/bridge/request/config/devices' -m '{}'

# Subscribe to response
mosquitto_sub -h localhost -t 'zigbee2mqtt/bridge/response/config/devices' -v
```

### Method 3: Check zigbee2mqtt Web UI

1. Open: `http://10.0.0.202:8080`
2. Go to "Devices" tab
3. See all configured devices (regardless of MQTT activity)

## Script to Monitor Topic Activity Over Time

Create `monitor-topics-activity.js`:

```javascript
const mqtt = require('mqtt');
const fs = require('fs');

const client = mqtt.connect('mqtt://localhost:1883');
const topicStats = new Map();
const startTime = Date.now();

client.on('connect', () => {
    console.log('✅ Connected');
    console.log('📊 Monitoring topic activity...\n');
    client.subscribe('zigbee2mqtt/#');
    
    // Show stats every 30 seconds
    setInterval(() => {
        const uptime = Math.round((Date.now() - startTime) / 1000);
        console.log(`\n📊 Stats (${uptime}s uptime):`);
        console.log(`   Total unique topics: ${topicStats.size}`);
        console.log(`   Most active topics:`);
        
        const sorted = Array.from(topicStats.entries())
            .sort((a, b) => b[1].count - a[1].count)
            .slice(0, 10);
        
        sorted.forEach(([topic, stats]) => {
            console.log(`   ${topic}: ${stats.count} messages`);
        });
    }, 30000);
});

client.on('message', (topic, message) => {
    if (!topicStats.has(topic)) {
        topicStats.set(topic, {
            firstSeen: new Date(),
            count: 0,
            lastSeen: null
        });
        console.log(`📌 New topic: ${topic}`);
    }
    
    const stats = topicStats.get(topic);
    stats.count++;
    stats.lastSeen = new Date();
});

process.on('SIGINT', () => {
    console.log('\n\n=== Final Statistics ===');
    console.log(`Total unique topics: ${topicStats.size}`);
    console.log(`Monitoring duration: ${Math.round((Date.now() - startTime) / 1000)}s\n`);
    
    const sorted = Array.from(topicStats.entries())
        .sort((a, b) => a[0].localeCompare(b[0]));
    
    sorted.forEach(([topic, stats]) => {
        console.log(`${topic}`);
        console.log(`  Messages: ${stats.count}`);
        console.log(`  First seen: ${stats.firstSeen.toLocaleString()}`);
        console.log(`  Last seen: ${stats.lastSeen.toLocaleString()}`);
        console.log('');
    });
    
    client.end();
    process.exit(0);
});
```

## Summary

### Key Points:

1. **MQTT topics are ephemeral** - they exist only when messages are published
2. **Topics don't persist** - no message = no topic in broker
3. **zigbee2mqtt topics vary** based on device activity
4. **To see all topics**, monitor over time or use zigbee2mqtt API/database
5. **Device list** is in zigbee2mqtt database, not MQTT topics

### Recommended Approach:

```bash
# 1. Monitor topics over time (hours/days)
timeout 3600 mosquitto_sub -h localhost -t 'zigbee2mqtt/#' -v | \
  awk '{print $1}' | sort -u > all-topics.txt

# 2. Or use zigbee2mqtt API for complete device list
curl http://10.0.0.202:8080/api/devices

# 3. Or check web UI
# http://10.0.0.202:8080 → Devices tab
```

This is normal MQTT behavior - topics only appear when messages are actively being published to them!


