# Clear Docker Logs on Raspberry Pi (docker-compose)

## Method 1: Clear logs for all containers in docker-compose

```bash
# Navigate to your docker-compose directory
cd /path/to/your/docker-compose/directory

# Stop containers (optional, but safer)
docker-compose down

# Clear logs for all services
docker-compose down -v  # This removes volumes, be careful!
# OR better approach:
truncate -s 0 $(docker inspect --format='{{.LogPath}}' $(docker-compose ps -q))
```

## Method 2: Clear logs for specific service

```bash
# Find the container name
docker-compose ps

# Clear logs for a specific service (e.g., 'zigbee2mqtt')
docker-compose logs --no-color zigbee2mqtt > /dev/null 2>&1
truncate -s 0 $(docker inspect --format='{{.LogPath}}' $(docker-compose ps -q zigbee2mqtt))
```

## Method 3: Clear all Docker logs (all containers)

```bash
# Find all log files
sudo find /var/lib/docker/containers/ -name "*-json.log" -exec truncate -s 0 {} \;

# Or using a loop
for container in $(docker ps -aq); do
    truncate -s 0 $(docker inspect --format='{{.LogPath}}' $container)
done
```

## Method 4: Clear logs and restart containers

```bash
# Stop containers
docker-compose down

# Clear logs
sudo truncate -s 0 /var/lib/docker/containers/*/*-json.log

# Start containers again
docker-compose up -d
```

## Method 5: Using docker-compose with log rotation

```bash
# Clear logs for all services in docker-compose
docker-compose down
docker system prune -f  # Optional: clean up unused data

# Clear log files directly
sudo sh -c "truncate -s 0 /var/lib/docker/containers/*/*-json.log"

# Restart services
docker-compose up -d
```

## Method 6: Script to clear logs for docker-compose services

Create a script `clear-docker-logs.sh`:

```bash
#!/bin/bash
# clear-docker-logs.sh

echo "Clearing Docker logs for docker-compose services..."

# Get all container IDs from docker-compose
CONTAINERS=$(docker-compose ps -q)

if [ -z "$CONTAINERS" ]; then
    echo "No containers found. Make sure you're in the docker-compose directory."
    exit 1
fi

# Clear logs for each container
for container in $CONTAINERS; do
    LOG_PATH=$(docker inspect --format='{{.LogPath}}' $container)
    if [ -f "$LOG_PATH" ]; then
        echo "Clearing logs for container: $(docker inspect --format='{{.Name}}' $container)"
        sudo truncate -s 0 "$LOG_PATH"
    fi
done

echo "Logs cleared successfully!"
```

Make it executable and run:
```bash
chmod +x clear-docker-logs.sh
./clear-docker-logs.sh
```

## Method 7: One-liner for docker-compose

```bash
# Clear logs for all containers managed by docker-compose
docker-compose ps -q | xargs -I {} sh -c 'sudo truncate -s 0 $(docker inspect --format="{{.LogPath}}" {})'
```

## Method 8: Configure log rotation (prevent logs from growing too large)

Add to your `docker-compose.yml`:

```yaml
version: '3.8'

services:
  your-service:
    image: your-image
    logging:
      driver: "json-file"
      options:
        max-size: "10m"
        max-file: "3"
```

This automatically rotates logs when they reach 10MB, keeping only 3 files.

## Method 9: Complete cleanup (logs + containers + volumes)

```bash
# Stop and remove containers, networks, volumes
docker-compose down -v

# Clear all logs
sudo truncate -s 0 /var/lib/docker/containers/*/*-json.log

# Optional: Clean up system
docker system prune -a -f
```

## Quick Reference Commands

```bash
# View current log sizes
docker-compose ps -q | xargs -I {} sh -c 'echo "{}: $(du -h $(docker inspect --format="{{.LogPath}}" {}) 2>/dev/null | cut -f1)"'

# Clear logs for one specific service
SERVICE_NAME="zigbee2mqtt"
CONTAINER_ID=$(docker-compose ps -q $SERVICE_NAME)
sudo truncate -s 0 $(docker inspect --format='{{.LogPath}}' $CONTAINER_ID)

# View logs (to verify they're cleared)
docker-compose logs --tail=50

# Follow logs in real-time
docker-compose logs -f
```

## Important Notes

⚠️ **Warning:**
- Clearing logs is permanent - you cannot recover them
- Make sure you don't need the logs before clearing
- Consider backing up important logs first

💡 **Tips:**
- Use log rotation (Method 8) to prevent logs from growing too large
- Check disk space: `df -h` (logs can fill up your SD card)
- Monitor log sizes regularly on Raspberry Pi (limited storage)

## Check Disk Space Before/After

```bash
# Check current disk usage
df -h

# Check Docker disk usage
docker system df

# Check log file sizes
sudo du -sh /var/lib/docker/containers/*/*-json.log | sort -h
```
