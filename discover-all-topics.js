#!/usr/bin/env node
/**
 * MQTT Topic Discovery Script
 * Discovers and lists all MQTT topics from a broker
 * 
 * Usage:
 *   node discover-all-topics.js [mqtt-broker] [timeout-seconds]
 * 
 * Examples:
 *   node discover-all-topics.js
 *   node discover-all-topics.js mqtt://localhost:1883
 *   node discover-all-topics.js mqtt://10.0.0.202:1883 60
 */

const mqtt = require('mqtt');

const BROKER = process.argv[2] || 'mqtt://localhost:1883';
const TIMEOUT = parseInt(process.argv[3]) || 30;
console.log(`🔍 Discovering MQTT topics from ${BROKER}`);
console.log(`⏱️  Listening for ${TIMEOUT} seconds...\n`);

const client = mqtt.connect(BROKER);
const topics = new Map();

client.on('connect', () => {
    console.log('✅ Connected to MQTT broker');
    console.log('📥 Subscribing to all topics (#)...\n');
    
    client.subscribe('#', { qos: 0 }, (err) => {
        if (err) {
            console.error('❌ Error subscribing:', err);
            process.exit(1);
        } else {
            console.log('✅ Subscribed to all topics');
            console.log('📊 Listening for messages...\n');
        }
    });
});

client.on('message', (topic, message) => {
    if (!topics.has(topic)) {
        topics.set(topic, {
            firstSeen: new Date(),
            messageCount: 0,
            sample: message.toString().substring(0, 150)
        });
        console.log(`📌 New topic discovered: ${topic}`);
    }
    
    const data = topics.get(topic);
    data.messageCount++;
    data.lastSeen = new Date();
});

client.on('error', (error) => {
    console.error('❌ MQTT Error:', error.message);
    process.exit(1);
});

// Display summary after timeout
setTimeout(() => {
    console.log('\n\n' + '='.repeat(60));
    console.log('DISCOVERY SUMMARY');
    console.log('='.repeat(60));
    console.log(`Total unique topics found: ${topics.size}\n`);
    
    if (topics.size === 0) {
        console.log('⚠️  No topics found. Make sure:');
        console.log('   1. MQTT broker is running');
        console.log('   2. Broker address is correct');
        console.log('   3. There are active publishers');
        console.log('   4. Wildcard subscriptions are allowed\n');
        client.end();
        process.exit(0);
    }
    
    // Group topics by prefix
    const grouped = {};
    Array.from(topics.keys()).forEach(topic => {
        const parts = topic.split('/');
        const prefix = parts.length > 1 ? parts[0] : 'root';
        if (!grouped[prefix]) {
            grouped[prefix] = [];
        }
        grouped[prefix].push(topic);
    });
    
    console.log('Topics by prefix:\n');
    Object.keys(grouped).sort().forEach(prefix => {
        console.log(`📁 ${prefix}/ (${grouped[prefix].length} topics)`);
    });
    
    console.log('\n' + '-'.repeat(60));
    console.log('All Topics (sorted):');
    console.log('-'.repeat(60) + '\n');
    
    const sortedTopics = Array.from(topics.entries())
        .sort((a, b) => a[0].localeCompare(b[0]));
    
    sortedTopics.forEach(([topic, data]) => {
        console.log(`📌 ${topic}`);
        console.log(`   Messages: ${data.messageCount}`);
        console.log(`   First seen: ${data.firstSeen.toLocaleTimeString()}`);
        if (data.messageCount === 1) {
            console.log(`   Sample: ${data.sample}`);
        }
        console.log('');
    });
    
    client.end();
    process.exit(0);
}, TIMEOUT * 1000);

// Handle graceful shutdown
process.on('SIGINT', () => {
    console.log('\n\n🛑 Interrupted. Generating summary...\n');
    setTimeout(() => process.exit(0), 100);
});


