#!/usr/bin/env node
/**
 * Zigbee2MQTT Heartbeat Monitor
 * Sends periodic heartbeat messages and monitors zigbee2mqtt status
 * 
 * Usage:
 *   node zigbee2mqtt-heartbeat-monitor.js [interval-seconds]
 * 
 * Example:
 *   node zigbee2mqtt-heartbeat-monitor.js 30
 */

const mqtt = require('mqtt');
const os = require('os');

// Configuration
const CONFIG = {
    // MQTT broker configuration
    mqttBroker: process.env.MQTT_BROKER || 'mqtt://localhost:1883',
    mqttUsername: process.env.MQTT_USERNAME || null,
    mqttPassword: process.env.MQTT_PASSWORD || null,
    
    // Heartbeat settings
    heartbeatInterval: parseInt(process.argv[2]) || 30, // seconds (default: 30)
    heartbeatTopic: 'zigbee2mqtt/heartbeat/monitor',
    
    // zigbee2mqtt topics to monitor
    bridgeStateTopic: 'zigbee2mqtt/bridge/state',
    bridgeInfoTopic: 'zigbee2mqtt/bridge/info',
    
    // Client settings
    clientId: `zigbee2mqtt-monitor-${os.hostname()}-${Date.now()}`,
};

// State tracking
let isConnected = false;
let bridgeState = 'unknown';
let lastBridgeStateUpdate = null;
let heartbeatCount = 0;
let lastHeartbeatTime = null;

// Create MQTT client
const mqttOptions = {
    clientId: CONFIG.clientId,
    keepalive: 60,
    reconnectPeriod: 5000,
};

if (CONFIG.mqttUsername && CONFIG.mqttPassword) {
    mqttOptions.username = CONFIG.mqttUsername;
    mqttOptions.password = CONFIG.mqttPassword;
}

console.log('🔌 Connecting to MQTT broker:', CONFIG.mqttBroker);
const client = mqtt.connect(CONFIG.mqttBroker, mqttOptions);

// Connection event handlers
client.on('connect', () => {
    isConnected = true;
    console.log('✅ Connected to MQTT broker');
    console.log(`📊 Heartbeat interval: ${CONFIG.heartbeatInterval} seconds`);
    console.log(`📤 Heartbeat topic: ${CONFIG.heartbeatTopic}`);
    console.log('');
    
    // Subscribe to zigbee2mqtt bridge state
    client.subscribe(CONFIG.bridgeStateTopic, (err) => {
        if (err) {
            console.error('❌ Error subscribing to bridge state:', err);
        } else {
            console.log(`📥 Subscribed to ${CONFIG.bridgeStateTopic}`);
        }
    });
    
    // Subscribe to bridge info
    client.subscribe(CONFIG.bridgeInfoTopic, (err) => {
        if (!err) {
            console.log(`📥 Subscribed to ${CONFIG.bridgeInfoTopic}`);
        }
    });
    
    // Start sending heartbeats
    sendHeartbeat();
    setInterval(sendHeartbeat, CONFIG.heartbeatInterval * 1000);
    
    // Check bridge state periodically
    checkBridgeState();
    setInterval(checkBridgeState, CONFIG.heartbeatInterval * 1000);
});

client.on('message', (topic, message) => {
    try {
        if (topic === CONFIG.bridgeStateTopic) {
            const state = message.toString();
            bridgeState = state;
            lastBridgeStateUpdate = new Date();
            
            if (state === 'online') {
                console.log('✅ zigbee2mqtt bridge is ONLINE');
            } else if (state === 'offline') {
                console.log('⚠️  zigbee2mqtt bridge is OFFLINE');
            }
        } else if (topic === CONFIG.bridgeInfoTopic) {
            const info = JSON.parse(message.toString());
            console.log('📋 Bridge Info:', {
                version: info.version,
                commit: info.commit?.substring(0, 7),
                coordinator: info.coordinator
            });
        }
    } catch (error) {
        console.error('❌ Error processing message:', error.message);
    }
});

client.on('error', (error) => {
    console.error('❌ MQTT Error:', error.message);
    isConnected = false;
});

client.on('offline', () => {
    console.log('⚠️  MQTT client offline');
    isConnected = false;
});

client.on('reconnect', () => {
    console.log('🔄 Reconnecting to MQTT broker...');
});

client.on('close', () => {
    console.log('🔌 MQTT connection closed');
    isConnected = false;
});

// Send heartbeat message
function sendHeartbeat() {
    if (!isConnected) {
        console.log('⚠️  Skipping heartbeat - not connected to MQTT');
        return;
    }
    
    const heartbeat = {
        timestamp: new Date().toISOString(),
        hostname: os.hostname(),
        count: ++heartbeatCount,
        bridgeState: bridgeState,
        interval: CONFIG.heartbeatInterval,
        lastBridgeStateUpdate: lastBridgeStateUpdate ? lastBridgeStateUpdate.toISOString() : null
    };
    
    client.publish(CONFIG.heartbeatTopic, JSON.stringify(heartbeat, null, 2), (err) => {
        if (err) {
            console.error('❌ Error publishing heartbeat:', err);
        } else {
            lastHeartbeatTime = new Date();
            const statusIcon = bridgeState === 'online' ? '✅' : '⚠️';
            console.log(`${statusIcon} Heartbeat #${heartbeatCount} sent at ${lastHeartbeatTime.toLocaleTimeString()}`);
        }
    });
}

// Check bridge state and warn if offline
function checkBridgeState() {
    if (lastBridgeStateUpdate) {
        const timeSinceUpdate = Date.now() - lastBridgeStateUpdate.getTime();
        const maxAge = CONFIG.heartbeatInterval * 3000; // 3 intervals
        
        if (timeSinceUpdate > maxAge && bridgeState !== 'online') {
            console.log(`⚠️  WARNING: Bridge state hasn't updated in ${Math.round(timeSinceUpdate / 1000)}s`);
        }
    } else {
        console.log('⚠️  No bridge state received yet');
    }
}

// Request bridge state
function requestBridgeState() {
    if (isConnected) {
        client.publish('zigbee2mqtt/bridge/request/state', '', (err) => {
            if (err) {
                console.error('❌ Error requesting bridge state:', err);
            }
        });
    }
}

// Request bridge state periodically
setInterval(requestBridgeState, CONFIG.heartbeatInterval * 2000);

// Graceful shutdown
process.on('SIGINT', () => {
    console.log('\n🛑 Shutting down...');
    console.log(`📊 Total heartbeats sent: ${heartbeatCount}`);
    client.end();
    process.exit(0);
});

process.on('SIGTERM', () => {
    console.log('\n🛑 Shutting down...');
    client.end();
    process.exit(0);
});

// Display status on startup
console.log('🚀 Zigbee2MQTT Heartbeat Monitor Starting...');
console.log(`⚙️  Configuration:`);
console.log(`   - MQTT Broker: ${CONFIG.mqttBroker}`);
console.log(`   - Heartbeat Interval: ${CONFIG.heartbeatInterval} seconds`);
console.log(`   - Heartbeat Topic: ${CONFIG.heartbeatTopic}`);
console.log(`   - Client ID: ${CONFIG.clientId}`);
console.log('');


