#!/usr/bin/env node
/**
 * Door Contacts Status Monitor
 * Periodically displays the status of all door contacts from zigbee2mqtt
 * 
 * Usage:
 *   node monitor-contacts-status.js [mqtt-broker] [check-interval]
 * 
 * Examples:
 *   node monitor-contacts-status.js
 *   node monitor-contacts-status.js mqtt://10.0.0.202:1883
 *   node monitor-contacts-status.js mqtt://localhost:1883 30
 */

const mqtt = require('mqtt');
const os = require('os');

// Configuration
const CONFIG = {
    // MQTT broker
    mqttBroker: process.argv[2] || process.env.MQTT_BROKER || 'mqtt://localhost:1883',
    mqttUsername: process.env.MQTT_USERNAME || null,
    mqttPassword: process.env.MQTT_PASSWORD || null,
    
    // Contact devices to monitor (UPDATE THESE WITH YOUR DEVICE NAMES)
    contacts: [
        'door_front',
        'door_back',
        'door_garage',
        'window_living_room'
        // Add more contact device names here
    ],
    
    // Check interval in seconds
    checkInterval: parseInt(process.argv[3]) || 30,
    
    // Display settings
    clearScreen: true, // Clear screen before each update
    showTimestamp: true,
    showUnknown: true, // Show devices with unknown status
};

// State storage
const contactStatus = new Map();
let lastUpdate = null;

// Create MQTT client
const mqttOptions = {
    clientId: `contacts-monitor-${os.hostname()}-${Date.now()}`,
    keepalive: 60,
    reconnectPeriod: 5000,
};

if (CONFIG.mqttUsername && CONFIG.mqttPassword) {
    mqttOptions.username = CONFIG.mqttUsername;
    mqttOptions.password = CONFIG.mqttPassword;
}

console.log('🚪 Door Contacts Status Monitor');
console.log('=' .repeat(60));
console.log(`MQTT Broker: ${CONFIG.mqttBroker}`);
console.log(`Monitoring ${CONFIG.contacts.length} contacts`);
console.log(`Check interval: ${CONFIG.checkInterval} seconds`);
console.log('=' .repeat(60));
console.log('');

const client = mqtt.connect(CONFIG.mqttBroker, mqttOptions);

// MQTT event handlers
client.on('connect', () => {
    console.log('✅ Connected to MQTT broker\n');
    
    // Subscribe to all zigbee2mqtt topics
    client.subscribe('zigbee2mqtt/#', (err) => {
        if (err) {
            console.error('❌ Error subscribing:', err);
            process.exit(1);
        } else {
            console.log('📥 Subscribed to zigbee2mqtt/#');
            console.log('📊 Monitoring contacts...\n');
        }
    });
    
    // Start periodic status display
    displayStatus();
    setInterval(displayStatus, CONFIG.checkInterval * 1000);
});

client.on('message', (topic, message) => {
    try {
        // Extract device name from topic: zigbee2mqtt/<device-name>
        const parts = topic.split('/');
        if (parts.length < 2 || parts[0] !== 'zigbee2mqtt') {
            return; // Not a zigbee2mqtt device topic
        }
        
        const deviceName = parts[1];
        
        // Only process contact devices
        if (!CONFIG.contacts.includes(deviceName)) {
            return; // Not a contact device we're monitoring
        }
        
        // Parse payload
        let data;
        try {
            data = typeof message === 'string' ? JSON.parse(message) : message;
        } catch(e) {
            data = { contact: message.toString() };
        }
        
        // Extract contact state
        // Common formats:
        // - contact: true/false (false = open, true = closed)
        // - state.contact: true/false
        // - contact_sensor: true/false
        const contactState = data.contact !== undefined ? data.contact :
                            data.state?.contact !== undefined ? data.state.contact :
                            data.contact_sensor !== undefined ? data.contact_sensor :
                            data.state || message.toString();
        
        // Determine if open or closed
        // false or 'open' or 0 = OPEN
        // true or 'closed' or 1 = CLOSED
        const isOpen = contactState === false || 
                      contactState === 'open' || 
                      contactState === 'OPEN' ||
                      contactState === 0;
                      
        const isClosed = contactState === true || 
                        contactState === 'closed' || 
                        contactState === 'CLOSED' ||
                        contactState === 1;
        
        // Store status
        contactStatus.set(deviceName, {
            device: deviceName,
            state: contactState,
            isOpen: isOpen,
            isClosed: isClosed,
            status: isOpen ? 'OPEN' : (isClosed ? 'CLOSED' : 'UNKNOWN'),
            timestamp: new Date(),
            lastUpdate: Date.now()
        });
        
        lastUpdate = new Date();
        
    } catch (error) {
        console.error('❌ Error processing message:', error.message);
    }
});

client.on('error', (error) => {
    console.error('❌ MQTT Error:', error.message);
});

client.on('offline', () => {
    console.log('⚠️  MQTT client offline');
});

client.on('reconnect', () => {
    console.log('🔄 Reconnecting to MQTT broker...');
});

// Display status function
function displayStatus() {
    if (CONFIG.clearScreen) {
        // Clear screen (works on most terminals)
        process.stdout.write('\x1B[2J\x1B[0f');
    }
    
    console.log('🚪 Door Contacts Status Monitor');
    console.log('=' .repeat(60));
    
    if (CONFIG.showTimestamp) {
        console.log(`📅 Last Update: ${new Date().toLocaleString()}`);
    }
    
    if (lastUpdate) {
        const timeSinceUpdate = Math.round((Date.now() - lastUpdate.getTime()) / 1000);
        console.log(`⏱️  Last message received: ${timeSinceUpdate}s ago`);
    } else {
        console.log('⏱️  Waiting for messages...');
    }
    
    console.log('=' .repeat(60));
    console.log('');
    
    // Get status for all contacts
    const statuses = [];
    let openCount = 0;
    let closedCount = 0;
    let unknownCount = 0;
    
    CONFIG.contacts.forEach(device => {
        const status = contactStatus.get(device);
        
        if (status) {
            statuses.push(status);
            if (status.isOpen) openCount++;
            else if (status.isClosed) closedCount++;
            else unknownCount++;
        } else {
            if (CONFIG.showUnknown) {
                statuses.push({
                    device: device,
                    status: 'UNKNOWN',
                    timestamp: null,
                    isOpen: null,
                    isClosed: null
                });
            }
            unknownCount++;
        }
    });
    
    // Display status table
    console.log('📋 Contact Status:');
    console.log('-'.repeat(60));
    console.log(`${'Device'.padEnd(25)} ${'Status'.padEnd(10)} ${'Last Update'}`);
    console.log('-'.repeat(60));
    
    statuses.forEach(status => {
        const device = status.device.padEnd(25);
        let statusText = status.status.padEnd(10);
        let timestamp = status.timestamp ? 
            status.timestamp.toLocaleTimeString() : 
            'Never';
        
        // Add color indicators (if terminal supports)
        let icon = '  ';
        if (status.isOpen === true) {
            icon = '🔴';
            statusText = 'OPEN'.padEnd(10);
        } else if (status.isClosed === true) {
            icon = '🟢';
            statusText = 'CLOSED'.padEnd(10);
        } else {
            icon = '⚪';
            statusText = 'UNKNOWN'.padEnd(10);
        }
        
        console.log(`${icon} ${device} ${statusText} ${timestamp}`);
    });
    
    console.log('-'.repeat(60));
    console.log('');
    
    // Summary
    console.log('📊 Summary:');
    console.log(`   Total: ${CONFIG.contacts.length}`);
    console.log(`   🟢 Closed: ${closedCount}`);
    console.log(`   🔴 Open: ${openCount}`);
    if (CONFIG.showUnknown) {
        console.log(`   ⚪ Unknown: ${unknownCount}`);
    }
    
    // Alert if any are open
    if (openCount > 0) {
        const openDevices = statuses
            .filter(s => s.isOpen === true)
            .map(s => s.device);
        console.log('');
        console.log(`⚠️  WARNING: ${openCount} contact(s) are OPEN:`);
        openDevices.forEach(device => {
            console.log(`   - ${device}`);
        });
    }
    
    console.log('');
    console.log(`⏳ Next update in ${CONFIG.checkInterval} seconds...`);
    console.log('   (Press Ctrl+C to exit)');
    console.log('');
}

// Request status for all contacts periodically
function requestStatus() {
    CONFIG.contacts.forEach(device => {
        client.publish(`zigbee2mqtt/${device}/get`, JSON.stringify({}), (err) => {
            if (err) {
                console.error(`❌ Error requesting status for ${device}:`, err);
            }
        });
    });
}

// Request status every check interval
setInterval(requestStatus, CONFIG.checkInterval * 1000);

// Request status on startup
setTimeout(requestStatus, 2000);

// Graceful shutdown
process.on('SIGINT', () => {
    console.log('\n\n🛑 Shutting down...');
    console.log('📊 Final Status:');
    displayStatus();
    client.end();
    process.exit(0);
});

process.on('SIGTERM', () => {
    client.end();
    process.exit(0);
});
