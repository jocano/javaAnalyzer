# Node-RED Sub-Flows Guide: Create and Invoke Sub-Flows

## What is a Sub-Flow?

A **sub-flow** is a reusable group of nodes that can be used multiple times in your flows. Think of it as a function or module that you can call from anywhere.

## Method 1: Create Sub-Flow from Existing Nodes

### Step 1: Select Nodes to Convert

1. Open Node-RED editor: `http://<raspberry-pi-ip>:1880`
2. **Select multiple nodes** you want to convert to a sub-flow:
   - Click and drag to select multiple nodes
   - Or hold `Ctrl` (Mac: `Cmd`) and click individual nodes
3. Selected nodes will be highlighted

### Step 2: Create Sub-Flow

1. Right-click on selected nodes
2. Choose **"Selection"** → **"Create sub-flow"**
3. Or use menu: **Edit** → **Selection** → **Create sub-flow**

### Step 3: Configure Sub-Flow

A new sub-flow node will appear with:
- **Input nodes** (where data enters)
- **Output nodes** (where data exits)
- **Sub-flow definition** (the reusable component)

**Configure the sub-flow:**
1. Double-click the sub-flow definition (the box with "subflow" label)
2. Set properties:
   - **Name**: Descriptive name (e.g., "Process Sensor Data")
   - **Category**: Where it appears in palette
   - **Description**: What it does
   - **Color**: Visual identifier
3. Click **Done**

### Step 4: Configure Inputs/Outputs

1. Double-click **input nodes** (left side of sub-flow):
   - Set **Label**: What this input represents
   - Set **Name**: Internal reference
   - Configure properties as needed

2. Double-click **output nodes** (right side of sub-flow):
   - Set **Label**: What this output represents
   - Set **Name**: Internal reference
   - Configure properties as needed

## Method 2: Create Sub-Flow from Scratch

### Step 1: Create Empty Sub-Flow

1. In Node-RED editor, click **Menu** (☰) → **Subflows** → **Create sub-flow**
2. Or use keyboard: `Ctrl+Shift+F` (Mac: `Cmd+Shift+F`)

### Step 2: Add Nodes to Sub-Flow

1. Drag nodes into the sub-flow workspace
2. Add **input** node (from palette) - this is where data enters
3. Add **output** node (from palette) - this is where data exits
4. Connect nodes between input and output

### Step 3: Configure Sub-Flow

1. Double-click the sub-flow definition
2. Configure:
   - **Name**: "My Sub-Flow"
   - **Category**: "custom"
   - **Description**: "Does something useful"
3. Click **Done**

## Using Sub-Flows in Your Flows

### Step 1: Find Sub-Flow in Palette

1. Look in the **palette** (left sidebar)
2. Find your sub-flow under its **Category** (or "subflows")
3. It will appear with the name you gave it

### Step 2: Add Sub-Flow to Flow

1. **Drag** the sub-flow from palette to your flow
2. It appears as a single node (with your custom name)
3. Connect it like any other node

### Step 3: Configure Sub-Flow Instance

1. **Double-click** the sub-flow instance in your flow
2. Configure any **properties** or **parameters** you defined
3. Click **Done**

## Example: Creating a "Format MQTT Message" Sub-Flow

### Step 1: Create the Sub-Flow

1. Create new sub-flow: Menu → Subflows → Create sub-flow
2. Name it: "Format MQTT Message"

### Step 2: Build the Logic

Add nodes inside sub-flow:
```
[input] → [function] → [output]
```

**Function node code:**
```javascript
// Format message for MQTT
const formatted = {
    timestamp: new Date().toISOString(),
    device: msg.topic.split('/').pop(),
    value: msg.payload,
    unit: msg.unit || 'unknown'
};

msg.payload = formatted;
return msg;
```

### Step 3: Use in Main Flow

1. Drag "Format MQTT Message" from palette
2. Connect it:
```
[inject] → [Format MQTT Message] → [mqtt out]
```

## Advanced: Sub-Flow with Parameters

### Step 1: Add Properties to Sub-Flow

1. Double-click sub-flow definition
2. Go to **Properties** tab
3. Click **+ property**
4. Add properties:
   - **Name**: `deviceName`
   - **Label**: "Device Name"
   - **Type**: `string`
   - **Default**: `"sensor1"`

### Step 2: Use Properties in Sub-Flow

In function nodes inside sub-flow:
```javascript
// Access property value
const deviceName = flow.get('deviceName') || 'default';

// Or use env variable
const deviceName = env.get('DEVICE_NAME');
```

### Step 3: Configure Instance

When you add sub-flow to flow:
1. Double-click sub-flow instance
2. Set **Device Name** property
3. Each instance can have different values

## Example: Complete Sub-Flow for zigbee2mqtt

### Create "Process Zigbee Device" Sub-Flow

**Sub-Flow Structure:**
```
[input] → [function: parse] → [switch: filter] → [function: format] → [output]
```

**Function: Parse**
```javascript
// Parse zigbee2mqtt message
const data = JSON.parse(msg.payload);
msg.device = data.friendly_name || msg.topic.split('/').pop();
msg.value = data.state || data;
return msg;
```

**Switch: Filter**
- Property: `msg.device`
- Rules: Filter specific devices

**Function: Format**
```javascript
// Format for output
msg.payload = {
    device: msg.device,
    value: msg.value,
    timestamp: new Date().toISOString()
};
return msg;
```

**Use in Main Flow:**
```
[mqtt in: zigbee2mqtt/#] → [Process Zigbee Device] → [mqtt out: processed/]
```

## Best Practices

### 1. Naming Conventions

- Use descriptive names: "Format Sensor Data" not "subflow1"
- Use consistent categories
- Add descriptions

### 2. Input/Output Design

- Keep inputs minimal (only what's needed)
- Make outputs clear and consistent
- Use multiple outputs for different paths

### 3. Error Handling

Add error handling inside sub-flow:
```javascript
// In function node
try {
    // Process data
    return msg;
} catch (error) {
    msg.error = error.message;
    return msg;
}
```

### 4. Documentation

- Add descriptions to sub-flow
- Document inputs/outputs
- Add comments in function nodes

## Managing Sub-Flows

### Export Sub-Flow

1. Right-click sub-flow definition
2. Choose **Export**
3. Save as JSON file

### Import Sub-Flow

1. Menu → **Import**
2. Paste JSON or select file
3. Click **Import**

### Edit Sub-Flow

1. Double-click sub-flow definition
2. Make changes
3. All instances update automatically

### Delete Sub-Flow

1. Right-click sub-flow definition
2. Choose **Delete**
3. Confirm deletion
4. All instances are removed

## Sub-Flow Templates

### Template 1: Data Transformation

```
[input] → [function: transform] → [output]
```

### Template 2: Conditional Processing

```
[input] → [switch] → [function: process] → [output]
              ↓
         [function: alternative] → [output]
```

### Template 3: Error Handling

```
[input] → [try-catch] → [function: process] → [output]
              ↓
         [function: handle-error] → [output]
```

## Troubleshooting

### Issue: Sub-Flow Not Appearing in Palette

**Solution:**
- Check category name
- Refresh browser
- Check sub-flow is saved (Deploy)

### Issue: Properties Not Working

**Solution:**
- Ensure properties are defined in sub-flow definition
- Check property names match
- Verify property types

### Issue: Data Not Flowing Through

**Solution:**
- Check input/output nodes are connected
- Verify msg structure
- Check function node returns msg

## Quick Reference

### Create Sub-Flow:
1. Select nodes → Right-click → "Create sub-flow"
2. Or: Menu → Subflows → Create sub-flow

### Use Sub-Flow:
1. Find in palette
2. Drag to flow
3. Connect like any node

### Configure:
- Double-click sub-flow definition (to edit)
- Double-click sub-flow instance (to configure)

## Example: Complete Workflow

### Step 1: Create "MQTT Logger" Sub-Flow

1. Create sub-flow: "MQTT Logger"
2. Add nodes:
   ```
   [input] → [function: add timestamp] → [debug] → [output]
   ```
3. Function code:
   ```javascript
   msg.timestamp = new Date().toISOString();
   msg.logged = true;
   return msg;
   ```

### Step 2: Use in Multiple Places

```
[mqtt in: topic1] → [MQTT Logger] → [process]
[mqtt in: topic2] → [MQTT Logger] → [process]
[mqtt in: topic3] → [MQTT Logger] → [process]
```

All three use the same sub-flow!

## Summary

**To Create:**
1. Select nodes → Right-click → "Create sub-flow"
2. Configure inputs/outputs
3. Name and describe

**To Use:**
1. Find in palette
2. Drag to flow
3. Connect

**Benefits:**
- Reusable code
- Easier maintenance
- Cleaner flows
- Better organization

Sub-flows make your Node-RED flows more organized and maintainable!
