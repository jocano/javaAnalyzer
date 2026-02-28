# Simple Node-RED Flow: Monitor Door Contacts

## Quick Setup (5 Nodes)

### Step 1: MQTT In Node
- **Name**: "Monitor Contacts"
- **Topic**: `zigbee2mqtt/#`
- **QoS**: 0

### Step 2: Function Node - Filter Contacts
**Name**: "Filter Contact Devices"

**Code:**
```javascript
// List your contact device names (update these!)
const contacts = [
    "door_front",
    "door_back", 
    "door_garage"
];

// Extract device name from topic
const device = msg.topic.split('/')[1];

// Only process contact devices
if (!contacts.includes(device)) {
    return null; // Drop non-contact messages
}

// Parse payload
let data = {};
try {
    data = typeof msg.payload === 'string' ? JSON.parse(msg.payload) : msg.payload;
} catch(e) {
    data = { contact: msg.payload };
}

// Extract contact state (adjust based on your device format)
const contact = data.contact !== undefined ? data.contact : 
                data.state?.contact !== undefined ? data.state.contact :
                data.state;

// Determine status
// false or 'open' = OPEN
// true or 'closed' = CLOSED
msg.device = device;
msg.isOpen = contact === false || contact === 'open';
msg.isClosed = contact === true || contact === 'closed';
msg.timestamp = new Date().toISOString();

// Store in flow context
flow.set(`contact_${device}`, {
    device: device,
    isOpen: msg.isOpen,
    isClosed: msg.isClosed,
    timestamp: msg.timestamp
});

return msg;
```

### Step 3: Inject Node - Periodic Check
- **Name**: "Check Every 30s"
- **Repeat**: interval
- **Interval**: 30 seconds

### Step 4: Function Node - Check All Contacts
**Name**: "Check All Contacts Status"

**Code:**
```javascript
// List of contacts to check
const contacts = [
    "door_front",
    "door_back",
    "door_garage"
];

// Get status of all contacts
const allStatus = {};
const openContacts = [];

contacts.forEach(device => {
    const status = flow.get(`contact_${device}`);
    if (status) {
        allStatus[device] = status;
        if (status.isOpen) {
            openContacts.push(device);
        }
    } else {
        allStatus[device] = { device: device, isOpen: null, status: 'unknown' };
    }
});

// Create summary
msg.payload = {
    timestamp: new Date().toISOString(),
    total: contacts.length,
    open: openContacts.length,
    closed: contacts.length - openContacts.length,
    openContacts: openContacts,
    allStatus: allStatus,
    hasOpen: openContacts.length > 0
};

msg.hasOpenContacts = openContacts.length > 0;

return msg;
```

### Step 5: Switch Node - Alert if Open
- **Name**: "Check if Open"
- **Property**: `msg.hasOpenContacts`
- **Rules**:
  - `true` → Alert path
  - `false` → OK path

### Step 6: Function Node - Create Alert
**Name**: "Alert Message"

**Code:**
```javascript
const openContacts = msg.payload.openContacts || [];

msg.payload = {
    alert: true,
    message: `⚠️ ${openContacts.length} contact(s) OPEN: ${openContacts.join(', ')}`,
    openContacts: openContacts,
    timestamp: new Date().toISOString()
};

msg.topic = "alerts/contacts";

return msg;
```

### Step 7: MQTT Out (Optional)
- **Name**: "Publish Alert"
- **Topic**: `alerts/contacts`
- **QoS**: 0

## Complete Flow Structure

```
[inject: 30s] → [function: check all] → [switch: has open?]
                                                    ↓
[mqtt in: zigbee2mqtt/#] → [function: filter] → [function: store] → [switch: has open?]
                                                                              ↓
                                                                        [function: alert] → [mqtt out]
```

## Configuration Steps

1. **Update Contact List**: Edit the `contacts` array in both function nodes with your actual device names

2. **Configure MQTT Broker**: 
   - Set MQTT broker connection in MQTT In/Out nodes
   - Use: `mqtt://localhost:1883` or `mqtt://10.0.0.202:1883`

3. **Adjust Contact State Parsing**:
   - Check your zigbee2mqtt device payload format
   - Adjust the contact state extraction in "Filter Contact Devices" function

4. **Set Check Interval**:
   - Modify inject node interval (default: 30 seconds)

## Testing

### Test with Inject Node

Add inject node with:
```json
{
    "topic": "zigbee2mqtt/door_front",
    "payload": "{\"contact\": false}"
}
```

This simulates door_front being OPEN.

## Quick Import (Copy-Paste)

1. In Node-RED, click **Menu** (☰) → **Import**
2. Copy the flow structure above
3. Paste and click **Import**
4. Configure MQTT broker connections
5. Update contact device names

## Summary

**Key Nodes:**
1. **MQTT In** - Subscribe to all zigbee2mqtt messages
2. **Function: Filter** - Extract contact devices only
3. **Flow Context** - Store contact status (`flow.set()`)
4. **Inject** - Trigger periodic check
5. **Function: Check All** - Aggregate all contact statuses
6. **Switch** - Route based on open/closed
7. **Alert** - Notify if any contacts are open

**Update These:**
- Contact device names in `contacts` array
- MQTT broker connection
- Contact state parsing (if your devices use different format)

This will continuously monitor your door contacts and alert you when any are open!
