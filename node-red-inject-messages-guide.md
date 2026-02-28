# How to Inject Messages in Node-RED

## Method 1: Using Inject Node (GUI)

### Basic Inject Node

1. **Drag Inject Node** from palette to flow
2. **Double-click** to configure
3. **Set payload**:
   - Type: `string`, `number`, `boolean`, `JSON`, etc.
   - Value: Your message content

4. **Optional settings**:
   - **Name**: "Test Message" (for identification)
   - **Topic**: `zigbee2mqtt/door_front` (optional)
   - **Payload**: Your message
   - **Repeat**: Once or interval
   - **Button**: Shows button in editor

5. **Click "Done"**
6. **Click the button** on the inject node (or deploy and click button)

### Inject JSON Message

**Example: Inject contact status**

1. **Double-click** Inject node
2. **Payload**: Select **"JSON"**
3. **Value**: 
   ```json
   {
     "contact": false
   }
   ```
4. **Topic**: `zigbee2mqtt/door_front`
5. **Click "Done"**

### Inject with Topic

1. **Double-click** Inject node
2. **Topic**: `zigbee2mqtt/door_front`
3. **Payload**: Select **"JSON"**
4. **Value**: 
   ```json
   {"contact": false}
   ```
5. **Click "Done"**

## Method 2: Using Function Node with msg Object

### Create Message in Function Node

**Function Node Code:**
```javascript
// Create message to inject
msg.topic = "zigbee2mqtt/door_front";
msg.payload = {
    contact: false  // false = open
};

return msg;
```

Then connect Inject node → Function node → Rest of flow

## Method 3: Using Change Node

### Set Multiple Properties

1. **Add Inject node**
2. **Add Change node**
3. **Configure Change node**:
   - **Action 1**: Set `msg.topic` to `zigbee2mqtt/door_front`
   - **Action 2**: Set `msg.payload` to JSON: `{"contact": false}`
4. **Connect**: Inject → Change → Rest of flow

## Method 4: Using Template Node

### Template Node for Complex Messages

**Template Node Code:**
```javascript
{% raw %}
{
    "topic": "zigbee2mqtt/door_front",
    "payload": {
        "contact": false,
        "timestamp": "{{$now}}"
    }
}
{% endraw %}
```

## Complete Examples

### Example 1: Inject Contact Status (Open)

**Inject Node Configuration:**
- **Topic**: `zigbee2mqtt/door_front`
- **Payload Type**: `JSON`
- **Payload Value**:
  ```json
  {
    "contact": false
  }
  ```
- **Name**: "Test: Door Open"

**Connect**: Inject → Your processing nodes

### Example 2: Inject Contact Status (Closed)

**Inject Node Configuration:**
- **Topic**: `zigbee2mqtt/door_front`
- **Payload Type**: `JSON`
- **Payload Value**:
  ```json
  {
    "contact": true
  }
  ```
- **Name**: "Test: Door Closed"

### Example 3: Inject Multiple Messages

**Function Node** (connected to Inject):
```javascript
// Create multiple messages
const messages = [
    {
        topic: "zigbee2mqtt/door_front",
        payload: { contact: false }
    },
    {
        topic: "zigbee2mqtt/door_back",
        payload: { contact: true }
    },
    {
        topic: "zigbee2mqtt/door_garage",
        payload: { contact: false }
    }
];

return messages; // Return array of messages
```

**Inject** → **Function** → **Rest of flow**

## Step-by-Step: Inject Your Test Message

### Step 1: Add Inject Node

1. Open Node-RED editor
2. Drag **"inject"** node from palette
3. Drop it on flow

### Step 2: Configure Inject Node

1. **Double-click** inject node
2. **Name**: "Test Door Front"
3. **Topic**: `zigbee2mqtt/door_front`
4. **Payload**: Select **"JSON"**
5. **Value**: 
   ```json
   {"contact": false}
   ```
6. **Click "Done"**

### Step 3: Connect and Test

1. **Connect** inject node to your processing nodes
2. **Click Deploy** (top right)
3. **Click the button** on inject node (left side)
4. Message will flow through connected nodes

## Advanced: Inject with Properties

### Using msg Properties

**Function Node** (after Inject):
```javascript
// Inject node sends basic payload
// Function node adds properties

msg.topic = "zigbee2mqtt/door_front";
msg.payload = {
    contact: false,
    battery: 85,
    voltage: 3.2,
    linkquality: 100
};

return msg;
```

## Periodic Inject

### Inject Every X Seconds

1. **Double-click** Inject node
2. **Repeat**: Select **"interval"**
3. **Every**: `30` seconds (or your interval)
4. **Click "Done"**

Now inject node will automatically send messages every 30 seconds!

## Inject on Startup

### Inject Once on Deploy

1. **Double-click** Inject node
2. **Repeat**: Select **"interval"**
3. Check **"Once at start"** (or "once after X seconds")
4. **Click "Done"**

Node will inject message when flow is deployed.

## Quick Reference

### Inject Node Properties:

- **Topic**: MQTT topic or message property
- **Payload**: Message content (string, JSON, number, etc.)
- **Repeat**: Once, interval, or cron
- **Button**: Show button in editor
- **Name**: Node label

### Common Payload Types:

- **String**: `"Hello"`
- **Number**: `123`
- **Boolean**: `true` or `false`
- **JSON**: `{"key": "value"}`
- **Timestamp**: Current time
- **Date**: Current date

## Testing Your Flow

### Test Pattern:

```
[inject] → [your processing nodes] → [debug]
```

1. **Add Inject** node
2. **Configure** with your test message
3. **Connect** to processing nodes
4. **Add Debug** node at end
5. **Click Deploy**
6. **Click inject button**
7. **Check Debug** panel to see message

## Example: Test Contact Monitor

**Complete Test Flow:**

1. **Inject Node**:
   - Topic: `zigbee2mqtt/door_front`
   - Payload: `{"contact": false}`
   - Name: "Test: Door Open"

2. **Connect to** your contact monitoring flow

3. **Add Debug Node** at end

4. **Click inject button** to test

## Summary

**Quick Steps:**
1. Drag **Inject** node from palette
2. **Double-click** to configure
3. Set **Topic** and **Payload** (JSON)
4. **Connect** to your flow
5. **Click Deploy**
6. **Click inject button** to send message

**For your test message:**
- Topic: `zigbee2mqtt/door_front`
- Payload (JSON): `{"contact": false}`

The inject node will send this message when you click its button!
