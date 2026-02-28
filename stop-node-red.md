# How to Stop Node-RED Instance

## Method 1: If Running as System Service (systemd)

```bash
# Stop Node-RED service
sudo systemctl stop nodered

# Check status
sudo systemctl status nodered

# Disable from starting on boot (optional)
sudo systemctl disable nodered
```

## Method 2: If Running in Docker

```bash
# Stop Node-RED container
docker stop node-red

# Or if using docker-compose
docker-compose stop node-red

# Stop and remove container
docker stop node-red
docker rm node-red

# Or using docker-compose (stops and removes)
docker-compose down node-red
```

## Method 3: If Running Manually (node-red command)

```bash
# Find the process
ps aux | grep node-red

# Kill the process (using PID from above)
kill <PID>

# Or force kill
kill -9 <PID>

# Or kill all node-red processes
pkill node-red

# Or force kill all
pkill -9 node-red
```

## Method 4: Stop All Node-RED Instances

```bash
# Kill all node-red processes
sudo pkill -f node-red

# Or more forcefully
sudo pkill -9 -f node-red

# Verify it's stopped
ps aux | grep node-red
```

## Quick Commands

### Stop service:
```bash
sudo systemctl stop nodered
```

### Stop Docker container:
```bash
docker-compose stop node-red
# Or
docker stop node-red
```

### Stop manually running instance:
```bash
pkill node-red
```

## Verify It's Stopped

```bash
# Check if process is running
ps aux | grep node-red

# Check if port 1880 is still in use
sudo lsof -i :1880

# Check service status (if using systemd)
sudo systemctl status nodered
```

## Restart Node-RED After Stopping

### If using systemd:
```bash
sudo systemctl start nodered
# Or restart
sudo systemctl restart nodered
```

### If using Docker:
```bash
docker-compose start node-red
# Or restart
docker-compose restart node-red
```


