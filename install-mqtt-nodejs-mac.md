# Install MQTT Library for Node.js on Mac

## Quick Installation

### Install mqtt Package (Most Popular)

```bash
# Using npm (Node Package Manager)
npm install mqtt

# Or install globally (for CLI tools)
npm install -g mqtt

# Or install as dev dependency
npm install --save-dev mqtt

# Using yarn
yarn add mqtt

# Using pnpm
pnpm add mqtt
```

## Verify Installation

```bash
# Check if installed
npm list mqtt

# Or check in your project
node -e "console.log(require('mqtt'))"
```

## Basic Usage Example

Create a test file `mqtt-test.js`:

```javascript
const mqtt = require('mqtt');

// Connect to MQTT broker
const client = mqtt.connect('mqtt://localhost:1883');

// When connected
client.on('connect', () => {
    console.log('Connected to MQTT broker');
    
    // Subscribe to a topic
    client.subscribe('test/topic', (err) => {
        if (!err) {
            console.log('Subscribed to test/topic');
            
            // Publish a message
            client.publish('test/topic', 'Hello from Node.js!');
        }
    });
});

// Receive messages
client.on('message', (topic, message) => {
    console.log(`Received message on ${topic}: ${message.toString()}`);
});

// Handle errors
client.on('error', (error) => {
    console.error('MQTT Error:', error);
});

// Handle disconnect
client.on('close', () => {
    console.log('MQTT connection closed');
});
```

Run it:
```bash
node mqtt-test.js
```

## Installation Options

### Option 1: For a Specific Project

```bash
# Navigate to your project directory
cd /path/to/your/project

# Initialize npm (if not already done)
npm init -y

# Install mqtt
npm install mqtt

# This adds mqtt to your package.json dependencies
```

### Option 2: Global Installation (CLI Tools)

```bash
# Install globally for CLI access
npm install -g mqtt

# Now you can use mqtt_pub and mqtt_sub commands
mqtt_pub -h localhost -t test -m "hello"
mqtt_sub -h localhost -t test
```

### Option 3: Using TypeScript

```bash
# Install mqtt
npm install mqtt

# Install TypeScript types (if using TypeScript)
npm install --save-dev @types/mqtt
```

## Alternative MQTT Libraries

### 1. mqtt (Most Popular)
```bash
npm install mqtt
```
- Most widely used
- Good documentation
- Supports MQTT 3.1.1 and 5.0

### 2. aedes (MQTT Broker)
```bash
npm install aedes
```
- For creating MQTT brokers (not clients)

### 3. paho-mqtt (Eclipse Paho)
```bash
npm install paho-mqtt
```
- Official Eclipse Paho client

## Common Use Cases

### 1. Connect to Local MQTT Broker

```javascript
const mqtt = require('mqtt');
const client = mqtt.connect('mqtt://localhost:1883');
```

### 2. Connect to Remote MQTT Broker

```javascript
const mqtt = require('mqtt');
const client = mqtt.connect('mqtt://192.168.1.100:1883');
```

### 3. Connect with Authentication

```javascript
const mqtt = require('mqtt');
const client = mqtt.connect('mqtt://localhost:1883', {
    username: 'your_username',
    password: 'your_password'
});
```

### 4. Connect with SSL/TLS

```javascript
const mqtt = require('mqtt');
const fs = require('fs');

const client = mqtt.connect('mqtts://localhost:8883', {
    key: fs.readFileSync('client-key.pem'),
    cert: fs.readFileSync('client-cert.pem'),
    ca: fs.readFileSync('ca-cert.pem')
});
```

### 5. Connect to Raspberry Pi MQTT Broker

```javascript
const mqtt = require('mqtt');

// If MQTT broker is on Raspberry Pi
const client = mqtt.connect('mqtt://<raspberry-pi-ip>:1883');

client.on('connect', () => {
    console.log('Connected to Raspberry Pi MQTT broker');
    
    // Subscribe to zigbee2mqtt topics
    client.subscribe('zigbee2mqtt/#');
});

client.on('message', (topic, message) => {
    console.log(`Topic: ${topic}`);
    console.log(`Message: ${JSON.parse(message.toString())}`);
});
```

## Complete Example: MQTT Client

```javascript
const mqtt = require('mqtt');

// Connection options
const options = {
    host: 'localhost',
    port: 1883,
    protocol: 'mqtt',
    // Optional authentication
    // username: 'user',
    // password: 'pass',
    // Optional client ID
    clientId: 'nodejs-client-' + Math.random().toString(16).substr(2, 8)
};

// Create client
const client = mqtt.connect('mqtt://localhost:1883', options);

// Event handlers
client.on('connect', () => {
    console.log('✅ Connected to MQTT broker');
    
    // Subscribe to topics
    client.subscribe('zigbee2mqtt/#', (err) => {
        if (err) {
            console.error('❌ Subscription error:', err);
        } else {
            console.log('✅ Subscribed to zigbee2mqtt/#');
        }
    });
});

client.on('message', (topic, message) => {
    try {
        const data = JSON.parse(message.toString());
        console.log(`📨 Topic: ${topic}`);
        console.log(`📦 Data:`, data);
    } catch (e) {
        console.log(`📨 Topic: ${topic}`);
        console.log(`📦 Message: ${message.toString()}`);
    }
});

client.on('error', (error) => {
    console.error('❌ MQTT Error:', error);
});

client.on('close', () => {
    console.log('🔌 Connection closed');
});

client.on('offline', () => {
    console.log('⚠️  Client offline');
});

// Publish example
function publishMessage(topic, message) {
    client.publish(topic, JSON.stringify(message), (err) => {
        if (err) {
            console.error('❌ Publish error:', err);
        } else {
            console.log(`✅ Published to ${topic}`);
        }
    });
}

// Example: Publish after 5 seconds
setTimeout(() => {
    publishMessage('test/topic', { 
        message: 'Hello from Node.js',
        timestamp: new Date().toISOString()
    });
}, 5000);

// Graceful shutdown
process.on('SIGINT', () => {
    console.log('Disconnecting...');
    client.end();
    process.exit();
});
```

## Install MQTT Broker (Mosquitto) on Mac

If you need to run an MQTT broker on your Mac:

```bash
# Using Homebrew
brew install mosquitto

# Start Mosquitto
brew services start mosquitto

# Or run manually
mosquitto -c /usr/local/etc/mosquitto/mosquitto.conf
```

## Test MQTT Connection

After installing, test the connection:

```bash
# If you have Mosquitto installed
# Terminal 1: Subscribe
mosquitto_sub -h localhost -t test/topic

# Terminal 2: Publish
mosquitto_pub -h localhost -t test/topic -m "Hello MQTT"

# Or using Node.js
node mqtt-test.js
```

## Troubleshooting

### Issue: Cannot find module 'mqtt'

```bash
# Make sure you're in the project directory
cd /path/to/your/project

# Reinstall
npm install mqtt

# Or clear cache and reinstall
npm cache clean --force
npm install mqtt
```

### Issue: Connection refused

```bash
# Check if MQTT broker is running
# For Mosquitto:
brew services list | grep mosquitto

# Test connection
mosquitto_pub -h localhost -t test -m "test"
```

### Issue: Permission denied

```bash
# If installing globally, may need sudo
sudo npm install -g mqtt

# Or better: Fix npm permissions
# See: https://docs.npmjs.com/resolving-eacces-permissions-errors-when-installing-packages-globally
```

## Quick Reference

```bash
# Install
npm install mqtt

# Import in your code
const mqtt = require('mqtt');

# Connect
const client = mqtt.connect('mqtt://localhost:1883');

# Subscribe
client.subscribe('topic/name');

# Publish
client.publish('topic/name', 'message');

# Listen for messages
client.on('message', (topic, message) => {
    console.log(message.toString());
});
```

## Package.json Example

After installation, your `package.json` will have:

```json
{
  "name": "your-project",
  "version": "1.0.0",
  "dependencies": {
    "mqtt": "^5.x.x"
  }
}
```

## Summary

**Quick Install:**
```bash
npm install mqtt
```

**Basic Usage:**
```javascript
const mqtt = require('mqtt');
const client = mqtt.connect('mqtt://localhost:1883');
client.on('connect', () => {
    client.subscribe('topic');
    client.publish('topic', 'message');
});
```

That's it! You can now use MQTT in your Node.js applications on Mac.


