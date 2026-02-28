# Node-RED Workflow: Monitor Door Contacts from zigbee2mqtt

## Overview

This workflow periodically checks the status of multiple door contacts (open/closed) from zigbee2mqtt and monitors their state.

## Complete Flow Structure

```
[inject: periodic check] → [function: request status] → [mqtt out: request]
                                                              ↓
[mqtt in: zigbee2mqtt/#] → [function: parse contact] → [switch: filter contacts] → [function: check status] → [function: alert if open]
```

## Method 1: Subscribe and Monitor (Recommended)

### Flow 1: Monitor All Contacts Continuously

**Nodes:**

1. **MQTT In Node**
   - Topic: `zigbee2mqtt/#`
   - QoS: 0
   - Name: "zigbee2mqtt All Messages"

2. **Function Node: Parse Contact Status**
   ```javascript
   // Parse zigbee2mqtt messages and extract contact status
   const topic = msg.topic;
   const payload = msg.payload;
   
   // List of contact device names
   const contacts = [
       "door_front",
       "door_back", 
       "door_garage",
       "window_living_room"
   ];
   
   // Extract device name from topic
   const deviceName = topic.split('/').pop();
   
   // Check if this is a contact device
   if (contacts.includes(deviceName)) {
       // Parse payload (could be JSON or string)
       let data;
       try {
           data = typeof payload === 'string' ? JSON.parse(payload) : payload;
       } catch(e) {
           data = { contact: payload };
       }
       
       // Extract contact state
       const contactState = data.contact !== undefined ? data.contact : 
                           data.state?.contact !== undefined ? data.state.contact :
                           data.state || payload;
       
       msg.device = deviceName;
       msg.contactState = contactState;
       msg.isOpen = contactState === false || contactState === 'open' || contactState === 'OPEN';
       msg.isClosed = contactState === true || contactState === 'closed' || contactState === 'CLOSED';
       msg.timestamp = new Date().toISOString();
       
       return msg;
   }
   
   // Not a contact device, drop message
   return null;
   ```

3. **Switch Node: Filter Contacts**
   - Property: `msg.device`
   - Rules: Add rules for each contact device name
   - Or use: Property: `msg.device`, Type: `is not null`

4. **Function Node: Store and Check Status**
   ```javascript
   // Store contact status in flow context
   const device = msg.device;
   const status = {
       state: msg.contactState,
       isOpen: msg.isOpen,
       isClosed: msg.isClosed,
       timestamp: msg.timestamp,
       lastUpdate: Date.now()
   };
   
   // Store in flow context
   flow.set(`contact_${device}`, status);
   
   // Get all contacts status
   const contacts = [
       "door_front",
       "door_back",
       "door_garage",
       "window_living_room"
   ];
   
   const allStatus = {};
   contacts.forEach(contact => {
       const stored = flow.get(`contact_${contact}`);
       allStatus[contact] = stored || { state: 'unknown', isOpen: null };
   });
   
   msg.allContacts = allStatus;
   msg.currentDevice = device;
   msg.currentStatus = status;
   
   return msg;
   ```

5. **Function Node: Check for Open Contacts**
   ```javascript
   // Check if any contacts are open
   const allContacts = msg.allContacts || {};
   const openContacts = [];
   
   Object.keys(allContacts).forEach(device => {
       const status = allContacts[device];
       if (status.isOpen === true) {
           openContacts.push({
               device: device,
               state: status.state,
               timestamp: status.timestamp
           });
       }
   });
   
   msg.openContacts = openContacts;
   msg.hasOpenContacts = openContacts.length > 0;
   msg.totalContacts = Object.keys(allContacts).length;
   msg.closedContacts = msg.totalContacts - openContacts.length;
   
   return msg;
   ```

6. **Switch Node: Alert if Open**
   - Property: `msg.hasOpenContacts`
   - Rule 1: `true` → Send alert
   - Rule 2: `false` → Log status (optional)

7. **Debug Node** (for monitoring)
   - Show: `msg.payload`
   - Or show: `{{msg.allContacts}}`

## Method 2: Periodic Status Check

### Flow 2: Request Status Periodically

**Nodes:**

1. **Inject Node: Periodic Check**
   - Repeat: `interval`
   - Interval: `30 seconds` (or your preferred interval)
   - Name: "Check Contacts Every 30s"

2. **Function Node: Request All Contact Status**
   ```javascript
   // List of contact devices
   const contacts = [
       "door_front",
       "door_back",
       "door_garage",
       "window_living_room"
   ];
   
   // Create messages to request status for each contact
   const messages = contacts.map(device => {
       return {
           topic: `zigbee2mqtt/${device}/get`,
           payload: JSON.stringify({}),
           device: device
       };
   });
   
   return messages; // Return array to send multiple messages
   ```

3. **MQTT Out Node**
   - Topic: `zigbee2mqtt/{{msg.device}}/get`
   - QoS: 0
   - Name: "Request Contact Status"

4. **MQTT In Node: Receive Status**
   - Topic: `zigbee2mqtt/+/get`
   - QoS: 0
   - Name: "Receive Contact Status"

5. **Function Node: Parse Response**
   ```javascript
   // Parse response from zigbee2mqtt
   const topic = msg.topic;
   const device = topic.split('/')[1];
   const payload = typeof msg.payload === 'string' ? 
                   JSON.parse(msg.payload) : msg.payload;
   
   // Extract contact state
   const contactState = payload.contact !== undefined ? payload.contact :
                       payload.state?.contact !== undefined ? payload.state.contact :
                       payload.state;
   
   msg.device = device;
   msg.contactState = contactState;
   msg.isOpen = contactState === false || contactState === 'open';
   msg.isClosed = contactState === true || contactState === 'closed';
   msg.timestamp = new Date().toISOString();
   
   return msg;
   ```

6. **Continue with Flow 1 steps 4-7** (Store, Check, Alert)

## Method 3: Complete Monitoring Flow with Dashboard

### Complete Flow JSON

```json
[
    {
        "id": "inject1",
        "type": "inject",
        "name": "Check Contacts Every 30s",
        "props": [{"p": "payload"}],
        "repeat": "30",
        "crontab": "",
        "once": true,
        "x": 100,
        "y": 100,
        "wires": [["function1"]]
    },
    {
        "id": "function1",
        "type": "function",
        "name": "Request Contact Status",
        "func": "const contacts = [\n    'door_front',\n    'door_back',\n    'door_garage'\n];\n\nconst messages = contacts.map(device => ({\n    topic: `zigbee2mqtt/${device}/get`,\n    payload: JSON.stringify({}),\n    device: device\n}));\n\nreturn messages;",
        "outputs": "multiple",
        "x": 300,
        "y": 100,
        "wires": [["mqtt-out1"]]
    },
    {
        "id": "mqtt-out1",
        "type": "mqtt out",
        "name": "Request Status",
        "topic": "zigbee2mqtt/{{msg.device}}/get",
        "qos": "0",
        "broker": "broker1",
        "x": 500,
        "y": 100,
        "wires": []
    },
    {
        "id": "mqtt-in1",
        "type": "mqtt in",
        "name": "Monitor All Contacts",
        "topic": "zigbee2mqtt/#",
        "qos": "0",
        "broker": "broker1",
        "x": 100,
        "y": 200,
        "wires": [["function2"]]
    },
    {
        "id": "function2",
        "type": "function",
        "name": "Parse Contact Status",
        "func": "const contacts = [\n    'door_front',\n    'door_back',\n    'door_garage',\n    'window_living_room'\n];\n\nconst topic = msg.topic;\nconst deviceName = topic.split('/').pop();\n\nif (!contacts.includes(deviceName)) {\n    return null;\n}\n\nlet data;\ntry {\n    data = typeof msg.payload === 'string' ? JSON.parse(msg.payload) : msg.payload;\n} catch(e) {\n    data = { contact: msg.payload };\n}\n\nconst contactState = data.contact !== undefined ? data.contact : \n                data.state?.contact !== undefined ? data.state.contact :\n                data.state || msg.payload;\n\nmsg.device = deviceName;\nmsg.contactState = contactState;\nmsg.isOpen = contactState === false || contactState === 'open' || contactState === 'OPEN';\nmsg.isClosed = contactState === true || contactState === 'closed' || contactState === 'CLOSED';\nmsg.timestamp = new Date().toISOString();\n\nreturn msg;",
        "outputs": 1,
        "x": 300,
        "y": 200,
        "wires": [["function3"]]
    },
    {
        "id": "function3",
        "type": "function",
        "name": "Store and Aggregate Status",
        "func": "const device = msg.device;\nconst contacts = [\n    'door_front',\n    'door_back',\n    'door_garage',\n    'window_living_room'\n];\n\n// Store current device status\nconst status = {\n    state: msg.contactState,\n    isOpen: msg.isOpen,\n    isClosed: msg.isClosed,\n    timestamp: msg.timestamp,\n    lastUpdate: Date.now()\n};\n\nflow.set(`contact_${device}`, status);\n\n// Get all contacts status\nconst allStatus = {};\ncontacts.forEach(contact => {\n    const stored = flow.get(`contact_${contact}`);\n    allStatus[contact] = stored || { \n        state: 'unknown', \n        isOpen: null,\n        timestamp: null\n    };\n});\n\n// Count open/closed\nconst openContacts = [];\nconst closedContacts = [];\n\nObject.keys(allStatus).forEach(name => {\n    const s = allStatus[name];\n    if (s.isOpen === true) {\n        openContacts.push(name);\n    } else if (s.isClosed === true) {\n        closedContacts.push(name);\n    }\n});\n\nmsg.allContacts = allStatus;\nmsg.openContacts = openContacts;\nmsg.closedContacts = closedContacts;\nmsg.hasOpenContacts = openContacts.length > 0;\nmsg.summary = {\n    total: contacts.length,\n    open: openContacts.length,\n    closed: closedContacts.length,\n    unknown: contacts.length - openContacts.length - closedContacts.length\n};\n\nreturn msg;",
        "outputs": 1,
        "x": 500,
        "y": 200,
        "wires": [["switch1", "debug1"]]
    },
    {
        "id": "switch1",
        "type": "switch",
        "name": "Check if Open",
        "property": "msg.hasOpenContacts",
        "propertyType": "msg",
        "rules": [
            {"t": "true"},
            {"t": "false"}
        ],
        "checkAll": "false",
        "x": 700,
        "y": 200,
        "wires": [["function4"], ["function5"]]
    },
    {
        "id": "function4",
        "type": "function",
        "name": "Alert: Open Contacts",
        "func": "const openContacts = msg.openContacts || [];\nconst allStatus = msg.allContacts || {};\n\n// Create alert message\nconst alertMsg = {\n    alert: true,\n    type: 'door_open',\n    message: `${openContacts.length} contact(s) are OPEN`,\n    openContacts: openContacts.map(name => ({\n        device: name,\n        status: allStatus[name]\n    })),\n    timestamp: new Date().toISOString()\n};\n\nmsg.payload = alertMsg;\nmsg.topic = 'alerts/contacts';\n\nreturn msg;",
        "outputs": 1,
        "x": 900,
        "y": 150,
        "wires": [["mqtt-out2", "notify1"]]
    },
    {
        "id": "function5",
        "type": "function",
        "name": "Log Status",
        "func": "msg.payload = {\n    status: 'ok',\n    summary: msg.summary,\n    timestamp: new Date().toISOString()\n};\n\nreturn msg;",
        "outputs": 1,
        "x": 900,
        "y": 250,
        "wires": [["debug2"]]
    },
    {
        "id": "mqtt-out2",
        "type": "mqtt out",
        "name": "Publish Alert",
        "topic": "alerts/contacts",
        "qos": "0",
        "broker": "broker1",
        "x": 1100,
        "y": 150,
        "wires": []
    },
    {
        "id": "debug1",
        "type": "debug",
        "name": "All Contacts Status",
        "active": true,
        "x": 700,
        "y": 300,
        "wires": []
    },
    {
        "id": "debug2",
        "type": "debug",
        "name": "Status OK",
        "active": true,
        "x": 1100,
        "y": 250,
        "wires": []
    },
    {
        "id": "notify1",
        "type": "notify",
        "name": "Notify Open Contacts",
        "x": 1100,
        "y": 200,
        "wires": []
    }
]
```

## Method 4: Simplified Version

### Simple Periodic Check Flow

**Node 1: Inject (Periodic)**
- Repeat: `interval`
- Every: `30 seconds`

**Node 2: Function: Get Contact List**
```javascript
// List of contacts to check
const contacts = [
    "door_front",
    "door_back",
    "door_garage"
];

// Store contact list in flow
flow.set("contactList", contacts);

msg.contacts = contacts;
return msg;
```

**Node 3: Function: Request Status for Each**
```javascript
const contacts = msg.contacts || flow.get("contactList") || [];

// Create request messages
return contacts.map(device => ({
    topic: `zigbee2mqtt/${device}/get`,
    payload: JSON.stringify({}),
    device: device
}));
```

**Node 4: MQTT Out**
- Topic: `zigbee2mqtt/{{msg.device}}/get`

**Node 5: MQTT In**
- Topic: `zigbee2mqtt/#`

**Node 6: Function: Parse and Store**
```javascript
const contacts = flow.get("contactList") || [];
const topic = msg.topic;
const device = topic.split('/')[1];

if (!contacts.includes(device)) {
    return null;
}

let data;
try {
    data = typeof msg.payload === 'string' ? JSON.parse(msg.payload) : msg.payload;
} catch(e) {
    data = { contact: msg.payload };
}

const contactState = data.contact !== undefined ? data.contact : data.state?.contact;

// Store status
flow.set(`status_${device}`, {
    state: contactState,
    isOpen: contactState === false || contactState === 'open',
    timestamp: new Date().toISOString()
});

// Check all contacts after a delay
setTimeout(() => {
    const allStatus = {};
    let allReceived = true;
    
    contacts.forEach(contact => {
        const status = flow.get(`status_${contact}`);
        if (status) {
            allStatus[contact] = status;
        } else {
            allReceived = false;
        }
    });
    
    if (allReceived) {
        const openContacts = contacts.filter(c => allStatus[c]?.isOpen);
        
        node.send({
            payload: {
                allContacts: allStatus,
                openContacts: openContacts,
                hasOpen: openContacts.length > 0
            }
        });
    }
}, 1000);

return null;
```

## Method 5: Using Sub-Flow for Reusability

### Create "Check Contact Status" Sub-Flow

**Sub-Flow Properties:**
- `contactName` (string) - Name of contact device
- `checkInterval` (number) - Interval in seconds

**Sub-Flow Nodes:**
```
[input] → [function: request status] → [mqtt out]
[mqtt in] → [function: parse] → [output]
```

**Use Sub-Flow:**
```
[inject: 30s] → [Check Contact: door_front] → [aggregate]
              → [Check Contact: door_back] → [aggregate]
              → [Check Contact: door_garage] → [aggregate]
```

## Dashboard Integration

### Add Dashboard Nodes

**Node: Dashboard UI - Table**
- Show contact status table
- Columns: Device, Status, Last Update

**Node: Dashboard UI - Switch**
- Control to manually check status

**Node: Dashboard UI - Notification**
- Alert when contacts are open

## Complete Example Flow

### Step-by-Step Setup

1. **MQTT In: Monitor All**
   - Topic: `zigbee2mqtt/#`
   - Name: "Monitor zigbee2mqtt"

2. **Function: Filter Contacts**
   ```javascript
   const contacts = ["door_front", "door_back", "door_garage"];
   const device = msg.topic.split('/')[1];
   
   if (!contacts.includes(device)) return null;
   
   const data = JSON.parse(msg.payload);
   msg.device = device;
   msg.contact = data.contact !== undefined ? data.contact : data.state?.contact;
   msg.isOpen = msg.contact === false || msg.contact === 'open';
   
   return msg;
   ```

3. **Function: Store Status**
   ```javascript
   const device = msg.device;
   flow.set(`contact_${device}`, {
       state: msg.contact,
       isOpen: msg.isOpen,
       timestamp: Date.now()
   });
   
   return msg;
   ```

4. **Inject: Periodic Check** (every 30s)
   - Triggers status check

5. **Function: Aggregate Status**
   ```javascript
   const contacts = ["door_front", "door_back", "door_garage"];
   const status = {};
   const open = [];
   
   contacts.forEach(c => {
       const s = flow.get(`contact_${c}`);
       status[c] = s || { state: 'unknown' };
       if (s?.isOpen) open.push(c);
   });
   
   msg.payload = {
       contacts: status,
       openContacts: open,
       hasOpen: open.length > 0
   };
   
   return msg;
   ```

6. **Switch: Check if Open**
   - Property: `msg.payload.hasOpen`
   - True → Alert
   - False → Log OK

## Testing

### Test Individual Contact

```javascript
// Inject test message
{
    topic: "zigbee2mqtt/door_front",
    payload: JSON.stringify({ contact: false }) // false = open
}
```

### Verify Status Storage

Add debug node showing: `{{flow.get("contact_door_front")}}`

## Summary

**Key Components:**
1. **MQTT In** - Subscribe to `zigbee2mqtt/#`
2. **Function** - Parse and filter contact messages
3. **Flow Context** - Store contact status (`flow.set()`)
4. **Inject** - Periodic check trigger
5. **Function** - Aggregate all contact statuses
6. **Switch/Alert** - Notify if any contacts are open

**Quick Setup:**
1. Subscribe to `zigbee2mqtt/#`
2. Filter for your contact devices
3. Store status in flow context
4. Periodically check all stored statuses
5. Alert if any are open

This workflow will continuously monitor your door contacts and alert you when any are open!
