#!/bin/bash
# clear-docker-logs.sh
# Script to clear Docker logs for docker-compose services on Raspberry Pi

set -e

echo "=========================================="
echo "Docker Log Cleaner for docker-compose"
echo "=========================================="
echo ""

# Check if docker-compose.yml exists
if [ ! -f "docker-compose.yml" ] && [ ! -f "docker-compose.yaml" ]; then
    echo "❌ ERROR: docker-compose.yml not found in current directory"
    echo "Please run this script from your docker-compose directory"
    exit 1
fi

# Check if running as root or with sudo
if [ "$EUID" -ne 0 ]; then 
    echo "⚠️  Note: This script may require sudo for log file access"
    echo ""
fi

# Get all container IDs from docker-compose
echo "Finding containers..."
CONTAINERS=$(docker-compose ps -q 2>/dev/null || docker compose ps -q 2>/dev/null)

if [ -z "$CONTAINERS" ]; then
    echo "⚠️  No running containers found."
    echo "Attempting to find all containers (including stopped)..."
    CONTAINERS=$(docker-compose ps -aq 2>/dev/null || docker compose ps -aq 2>/dev/null)
    
    if [ -z "$CONTAINERS" ]; then
        echo "❌ No containers found. Make sure docker-compose is set up correctly."
        exit 1
    fi
fi

echo "Found $(echo $CONTAINERS | wc -w) container(s)"
echo ""

# Ask for confirmation
read -p "Do you want to clear logs for all containers? (y/n): " -n 1 -r
echo ""
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Cancelled."
    exit 0
fi

echo ""
echo "Clearing logs..."

# Clear logs for each container
CLEARED=0
FAILED=0

for container in $CONTAINERS; do
    CONTAINER_NAME=$(docker inspect --format='{{.Name}}' $container 2>/dev/null | sed 's/\///')
    LOG_PATH=$(docker inspect --format='{{.LogPath}}' $container 2>/dev/null)
    
    if [ -z "$LOG_PATH" ]; then
        echo "⚠️  Could not find log path for container: $container"
        ((FAILED++))
        continue
    fi
    
    if [ -f "$LOG_PATH" ]; then
        LOG_SIZE=$(du -h "$LOG_PATH" 2>/dev/null | cut -f1)
        echo "  Clearing: $CONTAINER_NAME (size: $LOG_SIZE)"
        
        if sudo truncate -s 0 "$LOG_PATH" 2>/dev/null || truncate -s 0 "$LOG_PATH" 2>/dev/null; then
            ((CLEARED++))
        else
            echo "    ❌ Failed to clear log for $CONTAINER_NAME"
            ((FAILED++))
        fi
    else
        echo "  ⚠️  Log file not found for: $CONTAINER_NAME"
    fi
done

echo ""
echo "=========================================="
echo "Summary:"
echo "  ✅ Cleared: $CLEARED"
if [ $FAILED -gt 0 ]; then
    echo "  ❌ Failed: $FAILED"
fi
echo "=========================================="

# Show disk space
echo ""
echo "Current disk usage:"
df -h / | tail -1

echo ""
echo "Docker disk usage:"
docker system df 2>/dev/null || echo "Could not get Docker disk usage"

echo ""
echo "✅ Done!"
