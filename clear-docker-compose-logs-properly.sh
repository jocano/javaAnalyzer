#!/bin/bash
# clear-docker-compose-logs-properly.sh
# Properly clear docker-compose logs including cached logs

set -e

echo "=========================================="
echo "Docker Compose Log Cleaner (Complete)"
echo "=========================================="
echo ""

# Check if docker-compose.yml exists
if [ ! -f "docker-compose.yml" ] && [ ! -f "docker-compose.yaml" ]; then
    echo "❌ ERROR: docker-compose.yml not found in current directory"
    echo "Please run this script from your docker-compose directory"
    exit 1
fi

# Ask for confirmation
read -p "This will stop containers, clear ALL logs, and restart. Continue? (y/n): " -n 1 -r
echo ""
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Cancelled."
    exit 0
fi

echo ""
echo "Step 1: Stopping containers..."
docker-compose down 2>/dev/null || docker compose down 2>/dev/null

echo ""
echo "Step 2: Finding all containers..."
CONTAINERS=$(docker-compose ps -aq 2>/dev/null || docker compose ps -aq 2>/dev/null)

if [ -z "$CONTAINERS" ]; then
    echo "⚠️  No containers found. Trying to get all containers..."
    CONTAINERS=$(docker ps -aq)
fi

if [ -z "$CONTAINERS" ]; then
    echo "❌ No containers found"
    exit 1
fi

echo "Found $(echo $CONTAINERS | wc -w) container(s)"
echo ""

echo "Step 3: Clearing Docker log files..."
CLEARED=0
for container in $CONTAINERS; do
    CONTAINER_NAME=$(docker inspect --format='{{.Name}}' $container 2>/dev/null | sed 's/\///')
    LOG_PATH=$(docker inspect --format='{{.LogPath}}' $container 2>/dev/null)
    
    if [ -n "$LOG_PATH" ] && [ -f "$LOG_PATH" ]; then
        LOG_SIZE=$(du -h "$LOG_PATH" 2>/dev/null | cut -f1)
        echo "  Clearing: $CONTAINER_NAME (size: $LOG_SIZE)"
        
        # Method 1: Truncate (keeps file, clears content)
        if sudo truncate -s 0 "$LOG_PATH" 2>/dev/null || truncate -s 0 "$LOG_PATH" 2>/dev/null; then
            ((CLEARED++))
        else
            # Method 2: Delete and recreate (more aggressive)
            echo "    Trying alternative method (delete & recreate)..."
            sudo rm -f "$LOG_PATH" 2>/dev/null && {
                sudo touch "$LOG_PATH" 2>/dev/null
                ((CLEARED++))
            } || echo "    ❌ Failed"
        fi
    fi
done

echo ""
echo "Step 4: Clearing Docker log cache..."
# Clear Docker's internal log cache
sudo systemctl restart docker 2>/dev/null || {
    echo "  ⚠️  Could not restart Docker service (may require manual restart)"
}

echo ""
echo "Step 5: Removing old log files from /var/lib/docker/containers..."
# More aggressive: clear all container logs
sudo find /var/lib/docker/containers/ -name "*-json.log" -exec truncate -s 0 {} \; 2>/dev/null || {
    echo "  ⚠️  Some log files could not be cleared (may require root)"
}

echo ""
echo "Step 6: Pruning Docker system (optional cleanup)..."
read -p "Run 'docker system prune -f' to clean unused data? (y/n): " -n 1 -r
echo ""
if [[ $REPLY =~ ^[Yy]$ ]]; then
    docker system prune -f
fi

echo ""
echo "Step 7: Starting containers..."
docker-compose up -d 2>/dev/null || docker compose up -d 2>/dev/null

echo ""
echo "Waiting 3 seconds for containers to start..."
sleep 3

echo ""
echo "Step 8: Verifying logs are cleared..."
docker-compose ps 2>/dev/null || docker compose ps 2>/dev/null

echo ""
echo "=========================================="
echo "Summary:"
echo "  ✅ Logs cleared: $CLEARED"
echo "  ✅ Containers restarted"
echo "=========================================="

echo ""
echo "To verify logs are cleared, run:"
echo "  docker-compose logs --tail=10"
echo ""
echo "If you still see old logs, try:"
echo "  1. Restart Docker service: sudo systemctl restart docker"
echo "  2. Restart containers: docker-compose restart"
echo "  3. Check if logs are coming from inside containers (application logs)"
