# Pass Flow Variables to Sub-Flow in Node-RED

## Methods to Pass Variables to Sub-Flow

### Method 1: Using Flow Context Variables (Recommended)

Flow context variables can be accessed from both main flow and sub-flows.

#### Step 1: Set Flow Variable in Main Flow

**Using Function Node:**
```javascript
// Set flow variable
flow.set("deviceName", "sensor1");
flow.set("threshold", 25);

// Or set multiple values
flow.set("config", {
    deviceName: "sensor1",
    threshold: 25,
    unit: "Celsius"
});
```

**Using Change Node:**
1. Add Change node to main flow
2. Set action: **"Set"**
3. To: `flow.deviceName`
4. Using: `"sensor1"` (or `msg.payload`)

#### Step 2: Access Flow Variable in Sub-Flow

**In Function Node inside Sub-Flow:**
```javascript
// Get flow variable
const deviceName = flow.get("deviceName") || "default";
const threshold = flow.get("threshold") || 0;
const config = flow.get("config");

// Use in processing
msg.deviceName = deviceName;
msg.threshold = threshold;
return msg;
```

**In Switch Node:**
- Property: `flow.deviceName`
- Or: `flow.config.deviceName`

#### Complete Example:

**Main Flow:**
```javascript
// Function node before sub-flow
flow.set("deviceId", msg.deviceId);
flow.set("timestamp", Date.now());
return msg;
```

**Sub-Flow Function Node:**
```javascript
// Access flow variables
const deviceId = flow.get("deviceId");
const timestamp = flow.get("timestamp");

msg.payload = {
    device: deviceId,
    data: msg.payload,
    processedAt: timestamp
};
return msg;
```

### Method 2: Using Sub-Flow Properties (Best for Parameters)

This is the best method for configurable sub-flow instances.

#### Step 1: Define Properties in Sub-Flow

1. Double-click **sub-flow definition**
2. Go to **Properties** tab
3. Click **"+ property"**
4. Add properties:
   - **Name**: `deviceName`
   - **Label**: "Device Name"
   - **Type**: `string`
   - **Default**: `"sensor1"`

#### Step 2: Access Properties in Sub-Flow

**In Function Node inside Sub-Flow:**
```javascript
// Access sub-flow property
// Properties are available via env.get() or flow context

// Method 1: Using property value (if configured in sub-flow definition)
const deviceName = msg.deviceName || "default";

// Method 2: Access via sub-flow instance property
// This is set when configuring the sub-flow instance
```

**Access in Switch/Condition Nodes:**
- Use property names directly
- Or via `msg.propertyName`

#### Step 3: Set Property Value When Using Sub-Flow

1. **Double-click sub-flow instance** in your flow
2. You'll see your properties (e.g., "Device Name")
3. Set the value: `sensor1`
4. Each instance can have different values!

**Example Configuration:**
```
Sub-Flow Instance 1:
  Device Name: "sensor1"
  Threshold: 25

Sub-Flow Instance 2:
  Device Name: "sensor2"
  Threshold: 30
```

#### Complete Example with Properties:

**Sub-Flow Definition Properties:**
- `deviceName` (string, default: "sensor")
- `threshold` (number, default: 25)
- `unit` (string, default: "Celsius")

**Sub-Flow Function Node:**
```javascript
// Access properties (set when configuring instance)
// Properties are passed via msg or flow context
// Check sub-flow documentation for your Node-RED version

// For newer versions, properties are in msg
const deviceName = msg.deviceName || "default";
const threshold = msg.threshold || 25;

// Process with properties
if (msg.payload > threshold) {
    msg.alert = true;
}
msg.device = deviceName;
return msg;
```

### Method 3: Using Message Properties (msg)

Pass data via `msg` object.

#### Step 1: Set msg Properties Before Sub-Flow

**In Function Node:**
```javascript
// Set msg properties
msg.deviceName = "sensor1";
msg.threshold = 25;
msg.config = {
    unit: "Celsius",
    precision: 2
};

return msg;
```

#### Step 2: Access msg Properties in Sub-Flow

**In Function Node inside Sub-Flow:**
```javascript
// Access msg properties
const deviceName = msg.deviceName;
const threshold = msg.threshold;
const config = msg.config;

// Use in processing
msg.payload = {
    device: deviceName,
    value: msg.payload,
    aboveThreshold: msg.payload > threshold
};

return msg;
```

### Method 4: Using Global Context

For variables shared across all flows.

#### Set Global Variable:
```javascript
// In any flow or sub-flow
global.set("apiKey", "abc123");
global.set("serverConfig", {
    host: "192.168.1.100",
    port: 8080
});
```

#### Access Global Variable:
```javascript
// In sub-flow
const apiKey = global.get("apiKey");
const serverConfig = global.get("serverConfig");
```

### Method 5: Using Environment Variables

For configuration that doesn't change.

#### Step 1: Set Environment Variable

**In settings.js** (Node-RED config):
```javascript
module.exports = {
    functionGlobalContext: {
        DEVICE_NAME: process.env.DEVICE_NAME || 'sensor1',
        THRESHOLD: process.env.THRESHOLD || '25'
    }
};
```

**Or set in system:**
```bash
export DEVICE_NAME="sensor1"
export THRESHOLD="25"
```

#### Step 2: Access in Sub-Flow

```javascript
// Access via global context
const deviceName = global.get("DEVICE_NAME");
const threshold = global.get("THRESHOLD");
```

## Complete Examples

### Example 1: Pass Device Config via Flow Context

**Main Flow:**
```javascript
// Function node: Set Device Config
flow.set("deviceConfig", {
    name: "living_room_sensor",
    type: "temperature",
    unit: "Celsius",
    min: 15,
    max: 30
});

msg.deviceConfig = flow.get("deviceConfig");
return msg;
```

**Sub-Flow Function Node:**
```javascript
// Get device config from flow context
const config = flow.get("deviceConfig") || {};

// Use config in processing
if (msg.payload < config.min || msg.payload > config.max) {
    msg.alert = true;
    msg.message = `Value ${msg.payload}${config.unit} is out of range`;
}

msg.device = config.name;
msg.unit = config.unit;
return msg;
```

### Example 2: Pass Values via msg Properties

**Main Flow:**
```javascript
// Function node before sub-flow
msg.deviceId = "0x847127fffe166cb3";
msg.deviceName = "sensor_living_room";
msg.timestamp = Date.now();
return msg;
```

**Sub-Flow Function Node:**
```javascript
// Access msg properties
const deviceId = msg.deviceId;
const deviceName = msg.deviceName;
const timestamp = msg.timestamp;

// Process with passed values
msg.payload = {
    id: deviceId,
    name: deviceName,
    value: msg.payload,
    timestamp: timestamp,
    processed: true
};

return msg;
```

### Example 3: Sub-Flow with Configurable Properties

**Create Sub-Flow with Properties:**

1. Create sub-flow: "Process Sensor Data"
2. Add properties:
   - `sensorName` (string, default: "sensor")
   - `threshold` (number, default: 25)
   - `enabled` (boolean, default: true)

**Sub-Flow Function Node:**
```javascript
// Access properties (method depends on Node-RED version)
// For Node-RED 3.0+, properties are passed via env or flow context

// Method 1: Via flow context (if set in sub-flow definition)
const sensorName = flow.get("sensorName") || "default";
const threshold = flow.get("threshold") || 25;

// Method 2: Via msg (if properties are set on instance)
// const sensorName = msg.sensorName || flow.get("sensorName");

msg.sensor = sensorName;
msg.aboveThreshold = msg.payload > threshold;
return msg;
```

**Use Sub-Flow:**

1. Add "Process Sensor Data" to flow
2. Double-click instance
3. Set properties:
   - Sensor Name: `living_room`
   - Threshold: `30`
   - Enabled: `true`

Each instance can have different values!

### Example 4: Dynamic Flow Variables

**Main Flow Function:**
```javascript
// Dynamically set flow variable based on msg
const deviceType = msg.topic.split('/')[1]; // Extract from topic
flow.set("currentDeviceType", deviceType);
flow.set("lastUpdate", Date.now());

return msg;
```

**Sub-Flow Function:**
```javascript
// Get dynamic flow variable
const deviceType = flow.get("currentDeviceType");
const lastUpdate = flow.get("lastUpdate");

msg.processedBy = deviceType;
msg.lastUpdateTime = lastUpdate;
return msg;
```

## Quick Reference

### Setting Variables:

```javascript
// Flow context (per flow)
flow.set("key", "value");
const value = flow.get("key");

// Global context (all flows)
global.set("key", "value");
const value = global.get("key");

// Message properties
msg.key = "value";
const value = msg.key;
```

### Accessing in Sub-Flow:

```javascript
// Flow context
const value = flow.get("key");

// Global context
const value = global.get("key");

// Message property
const value = msg.key;

// Sub-flow property (set on instance)
// Check Node-RED version documentation
```

## Best Practices

### 1. Choose the Right Method

- **Flow context**: For flow-specific config
- **Sub-flow properties**: For instance-specific config
- **msg properties**: For per-message data
- **Global context**: For app-wide config

### 2. Initialize Variables

```javascript
// Always provide defaults
const value = flow.get("key") || "default";
const config = flow.get("config") || {};
```

### 3. Clear Variables When Done

```javascript
// Clear flow variable
flow.set("key", null);
// Or
delete flow.context().flow.key;
```

### 4. Document Variables

Add comments explaining what variables are used:
```javascript
// Flow variables used:
// - deviceName: Current device being processed
// - threshold: Alert threshold value
// - config: Device configuration object
```

## Troubleshooting

### Issue: Variable Not Available in Sub-Flow

**Solution:**
- Ensure variable is set before sub-flow is called
- Check if using `flow.get()` vs `global.get()`
- Verify variable name matches exactly

### Issue: Properties Not Working

**Solution:**
- Ensure properties are defined in sub-flow definition
- Check Node-RED version (property handling varies)
- Try using flow context as alternative

### Issue: Variable Value Not Updating

**Solution:**
- Use `flow.set()` to update values
- Clear cache if needed: restart Node-RED
- Check if variable is being overwritten

## Summary

**Methods (in order of preference):**

1. **Sub-Flow Properties** - For instance-specific config
2. **Flow Context** - For flow-specific variables
3. **Message Properties** - For per-message data
4. **Global Context** - For app-wide config

**Quick Example:**
```javascript
// Main flow: Set variable
flow.set("deviceName", "sensor1");

// Sub-flow: Get variable
const name = flow.get("deviceName");
```

The most common approach is using **flow context** (`flow.set()` / `flow.get()`) for sharing variables between main flow and sub-flows!
