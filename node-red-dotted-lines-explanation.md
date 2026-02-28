# Node-RED Dotted Lines Explanation

## What Do Dotted Lines Mean?

In Node-RED, **dotted lines** typically indicate:

### 1. Disabled/Deactivated Connections
- **Solid line** = Active connection
- **Dotted line** = Disabled/deactivated connection

### 2. Error/Invalid Connections
- Dotted lines may appear when there's an error in the connection
- Or when a node is disabled

### 3. Debug Flow Path (in Debug Node)
- When using Debug node, dotted lines show the message flow path
- Helps visualize which messages reach the debug node

### 4. Context Variable Connections
- In some Node-RED versions, dotted lines may indicate context variable flows
- Less common in recent versions

## Common Scenarios

### Scenario 1: Disabled Node

**Appearance:**
- Node appears grayed out
- Connections to/from node appear as dotted lines

**Fix:**
1. Right-click the disabled node
2. Choose **"Enable"** (or check in node properties)
3. Lines will become solid

### Scenario 2: Disabled Connection

**Appearance:**
- Some connections are dotted, others are solid

**Fix:**
- Enable the nodes in the flow
- Check node status (should not show "disabled" in properties)

### Scenario 3: Node Error

**Appearance:**
- Node has error indicator (red triangle)
- Connections may appear dotted

**Fix:**
1. Click on node to see error message
2. Fix configuration error
3. Lines should become solid

### Scenario 4: Flow is Disabled

**Appearance:**
- Entire flow appears grayed out
- All connections are dotted

**Fix:**
1. Right-click on flow tab
2. Choose **"Enable"**
3. Or click the flow enable/disable button

## How to Fix Dotted Lines

### Method 1: Enable Disabled Nodes

1. **Find grayed-out nodes** (they appear lighter)
2. **Right-click** on node
3. Choose **"Enable"** or **"Enable all"**
4. Connections should become solid

### Method 2: Check Node Status

1. **Double-click** each node
2. Check if there's an **"Enabled"** checkbox
3. Make sure it's **checked**
4. Click **Done**

### Method 3: Enable Entire Flow

1. Look at **flow tab** (top of editor)
2. Check if there's a **disable/enable button**
3. Click to **enable** the flow
4. All connections should become solid

### Method 4: Deploy Flow

1. Click **Deploy** button (top right)
2. This enables all nodes and connections
3. Dotted lines should become solid

## Visual Indicators

### Solid Line (Normal)
```
[Node A] ──────→ [Node B]
```
- Active connection
- Messages flow normally

### Dotted Line (Disabled/Error)
```
[Node A] ······→ [Node B]
```
- Connection is disabled
- Messages may not flow
- Or indicates error state

## Check Connection Status

### Inspect Node Properties

1. **Double-click** a node with dotted connections
2. Check:
   - **Enabled** checkbox (should be checked)
   - **Status** (should not show errors)
   - **Configuration** (should be valid)

### Check Flow Status

1. Look at **flow tab** at top
2. Check for **disable indicator**
3. Enable flow if disabled

## Common Causes

### 1. Node is Disabled
- Solution: Enable the node

### 2. Flow is Disabled
- Solution: Enable the flow

### 3. Configuration Error
- Solution: Fix node configuration

### 4. Missing Node Type
- Solution: Install required node palette

### 5. Deploy Required
- Solution: Click Deploy button

## Quick Checklist

```bash
✓ Check if flow is enabled
✓ Check if nodes are enabled
✓ Check for error indicators (red triangles)
✓ Click Deploy button
✓ Check node configurations
```

## Summary

**Dotted lines usually mean:**
- Connection is **disabled**
- Node is **disabled**
- Flow is **disabled**
- There's an **error** state

**To fix:**
1. Enable disabled nodes/flows
2. Fix any configuration errors
3. Click **Deploy**
4. Lines should become solid

**Normal state:**
- All lines should be **solid**
- Nodes should not be grayed out
- No red error indicators

If you see dotted lines, check if any nodes or the flow itself is disabled!
