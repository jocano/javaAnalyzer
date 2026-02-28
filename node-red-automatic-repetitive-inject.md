# Node-RED: Automatic Repetitive Inject (No Manual Action)

## Method 1: Using Inject Node with Interval (Recommended)

### Step 1: Add Inject Node

1. Drag **Inject** node from palette
2. Drop on your flow

### Step 2: Configure for Automatic Repeat

1. **Double-click** Inject node
2. **Name**: "Check Every 30s" (or your description)
3. **Repeat**: Select **"interval"**
4. **Every**: `30` seconds (or your desired interval)
5. **Once at start**: Check this box (optional - sends immediately on deploy)
6. **Click "Done"**

### Step 3: Deploy

1. Click **Deploy** button (top right)
2. Node will automatically start sending messages every 30 seconds
3. **No manual action needed!**

## Configuration Options

### Option 1: Interval (Every X Seconds)

**Settings:**
- **Repeat**: `interval`
- **Every**: `30` seconds (or any number)
- **Once at start**: ✅ (optional - sends immediately)

**Result:** Sends message every 30 seconds automatically

### Option 2: Cron Schedule

**Settings:**
- **Repeat**: `cron`
- **Cron**: `*/30 * * * * *` (every 30 seconds)
- Or: `0 */5 * * * *` (every 5 minutes)

**Cron Examples:**
```
*/30 * * * * *    - Every 30 seconds
0 */1 * * * *     - Every 1 minute
0 */5 * * * *     - Every 5 minutes
0 0 * * * *       - Every hour
0 0 0 * * *       - Every day at midnight
```

### Option 3: Once at Start + Interval

**Settings:**
- **Repeat**: `interval`
- **Every**: `30` seconds
- **Once at start**: ✅ Checked

**Result:** 
- Sends immediately when flow is deployed
- Then continues every 30 seconds

## Complete Example: Periodic Contact Check

### Inject Node Configuration

1. **Double-click** Inject node
2. **Name**: "Check Contacts Every 30s"
3. **Repeat**: `interval`
4. **Every**: `30` seconds
5. **Once at start**: ✅ (sends immediately)
6. **Payload**: Leave empty or set default
7. **Click "Done"**

### Connect to Your Flow

```
[inject: 30s interval] → [function: check contacts] → [rest of flow]
```

**Result:** Automatically checks contacts every 30 seconds without any manual action!

## Advanced: Multiple Automatic Inject Nodes

### Different Intervals

**Inject Node 1:**
- Every: `10` seconds (quick checks)

**Inject Node 2:**
- Every: `300` seconds (5 minutes - detailed check)

**Inject Node 3:**
- Cron: `0 0 * * * *` (hourly summary)

All run automatically in parallel!

## Verify It's Working

### Check Inject Node Status

1. **Deploy** the flow
2. Look at inject node - it should show activity
3. Check **Debug** panel - should see messages flowing
4. **No button clicking needed!**

### Debug Output

Add **Debug** node after inject:
- You'll see messages appearing automatically
- Every 30 seconds (or your interval)
- Confirms it's working

## Troubleshooting

### Issue: Inject Node Not Repeating

**Check:**
1. Is **Repeat** set to `interval` or `cron`?
2. Is **Every** value set correctly?
3. Did you click **Deploy**?
4. Is flow enabled?

**Fix:**
- Double-check inject node configuration
- Ensure "Repeat" is not set to "none"
- Click Deploy again

### Issue: Not Starting Automatically

**Solution:**
- Check **"Once at start"** option
- Or set **Repeat** to `interval` with small value (1 second)

### Issue: Too Many Messages

**Solution:**
- Increase **Every** value (e.g., 60 seconds instead of 1)
- Or use cron for more control

## Quick Setup Checklist

✅ **Inject Node Added**
✅ **Repeat**: Set to `interval`
✅ **Every**: Set to desired seconds (e.g., 30)
✅ **Once at start**: Checked (optional)
✅ **Deploy**: Clicked
✅ **Automatic**: Working without manual action

## Example Configurations

### Every 30 Seconds
```
Repeat: interval
Every: 30 seconds
Once at start: ✅
```

### Every 5 Minutes
```
Repeat: interval
Every: 300 seconds
Once at start: ✅
```

### Every Hour
```
Repeat: cron
Cron: 0 0 * * * *
Once at start: ✅
```

### Every Day at Midnight
```
Repeat: cron
Cron: 0 0 0 * * *
Once at start: ❌
```

## Summary

**To make inject node automatic and repetitive:**

1. **Double-click** Inject node
2. **Repeat**: Select `interval`
3. **Every**: Set seconds (e.g., `30`)
4. **Once at start**: ✅ (optional)
5. **Click "Done"**
6. **Click "Deploy"**

**Result:** Node automatically sends messages every X seconds without any manual action!

**Key Settings:**
- ✅ **Repeat**: `interval` (not "none")
- ✅ **Every**: Your interval in seconds
- ✅ **Deploy**: Must click Deploy for changes to take effect

Once configured and deployed, the inject node will run automatically forever!
