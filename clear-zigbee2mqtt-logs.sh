#!/bin/bash
# clear-zigbee2mqtt-logs.sh
# Script to clear log files for zigbee2mqtt on Raspberry Pi

set -e

echo "=========================================="
echo "Zigbee2MQTT Log Cleaner"
echo "=========================================="
echo ""

# Function to check if command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Detect zigbee2mqtt installation type
Z2M_INSTALL_TYPE="unknown"
Z2M_CONTAINER=""
Z2M_LOG_DIR=""

# Check if running in Docker
if command_exists docker; then
    # Check for docker-compose
    if [ -f "docker-compose.yml" ] || [ -f "docker-compose.yaml" ]; then
        Z2M_CONTAINER=$(docker-compose ps -q zigbee2mqtt 2>/dev/null || docker compose ps -q zigbee2mqtt 2>/dev/null)
        if [ -n "$Z2M_CONTAINER" ]; then
            Z2M_INSTALL_TYPE="docker-compose"
        fi
    fi
    
    # Check for standalone Docker container
    if [ -z "$Z2M_CONTAINER" ]; then
        Z2M_CONTAINER=$(docker ps -q -f "name=zigbee2mqtt" 2>/dev/null)
        if [ -n "$Z2M_CONTAINER" ]; then
            Z2M_INSTALL_TYPE="docker"
        fi
    fi
fi

# Check for direct installation
if [ "$Z2M_INSTALL_TYPE" = "unknown" ]; then
    # Common installation paths
    if [ -d "/opt/zigbee2mqtt" ]; then
        Z2M_LOG_DIR="/opt/zigbee2mqtt/data/log"
        Z2M_INSTALL_TYPE="direct"
    elif [ -d "/home/pi/zigbee2mqtt" ]; then
        Z2M_LOG_DIR="/home/pi/zigbee2mqtt/data/log"
        Z2M_INSTALL_TYPE="direct"
    elif [ -d "$HOME/zigbee2mqtt" ]; then
        Z2M_LOG_DIR="$HOME/zigbee2mqtt/data/log"
        Z2M_INSTALL_TYPE="direct"
    fi
fi

# Display detected installation
echo "Detected installation type: $Z2M_INSTALL_TYPE"
echo ""

# Clear logs based on installation type
CLEARED=0

if [ "$Z2M_INSTALL_TYPE" = "docker-compose" ] || [ "$Z2M_INSTALL_TYPE" = "docker" ]; then
    echo "Clearing Docker logs for zigbee2mqtt..."
    
    if [ -n "$Z2M_CONTAINER" ]; then
        LOG_PATH=$(docker inspect --format='{{.LogPath}}' $Z2M_CONTAINER 2>/dev/null)
        CONTAINER_NAME=$(docker inspect --format='{{.Name}}' $Z2M_CONTAINER 2>/dev/null | sed 's/\///')
        
        if [ -n "$LOG_PATH" ] && [ -f "$LOG_PATH" ]; then
            LOG_SIZE=$(du -h "$LOG_PATH" 2>/dev/null | cut -f1)
            echo "  Container: $CONTAINER_NAME"
            echo "  Log file: $LOG_PATH"
            echo "  Size: $LOG_SIZE"
            
            if sudo truncate -s 0 "$LOG_PATH" 2>/dev/null || truncate -s 0 "$LOG_PATH" 2>/dev/null; then
                echo "  ✅ Docker log cleared"
                ((CLEARED++))
            else
                echo "  ❌ Failed to clear Docker log"
            fi
        else
            echo "  ⚠️  Could not find Docker log file"
        fi
        
        # Also clear application logs inside container (if accessible)
        echo ""
        echo "Clearing application logs inside container..."
        docker exec $Z2M_CONTAINER sh -c "find /app/data/log -name '*.log' -type f -exec truncate -s 0 {} \;" 2>/dev/null && {
            echo "  ✅ Application logs cleared"
            ((CLEARED++))
        } || echo "  ⚠️  Could not clear application logs (may not exist or not accessible)"
    else
        echo "❌ zigbee2mqtt container not found"
    fi
    
elif [ "$Z2M_INSTALL_TYPE" = "direct" ]; then
    echo "Clearing direct installation logs..."
    echo "Log directory: $Z2M_LOG_DIR"
    
    if [ -d "$Z2M_LOG_DIR" ]; then
        # Find and clear all log files
        LOG_FILES=$(find "$Z2M_LOG_DIR" -name "*.log" -type f 2>/dev/null)
        
        if [ -n "$LOG_FILES" ]; then
            while IFS= read -r log_file; do
                if [ -f "$log_file" ]; then
                    LOG_SIZE=$(du -h "$log_file" 2>/dev/null | cut -f1)
                    echo "  Clearing: $log_file (size: $LOG_SIZE)"
                    if truncate -s 0 "$log_file" 2>/dev/null || sudo truncate -s 0 "$log_file" 2>/dev/null; then
                        ((CLEARED++))
                    fi
                fi
            done <<< "$LOG_FILES"
        else
            echo "  ℹ️  No log files found in $Z2M_LOG_DIR"
        fi
        
        # Also check for log files in data directory
        DATA_DIR=$(dirname "$Z2M_LOG_DIR")
        if [ -d "$DATA_DIR" ]; then
            ADDITIONAL_LOGS=$(find "$DATA_DIR" -maxdepth 1 -name "*.log" -type f 2>/dev/null)
            if [ -n "$ADDITIONAL_LOGS" ]; then
                while IFS= read -r log_file; do
                    if [ -f "$log_file" ]; then
                        LOG_SIZE=$(du -h "$log_file" 2>/dev/null | cut -f1)
                        echo "  Clearing: $log_file (size: $LOG_SIZE)"
                        if truncate -s 0 "$log_file" 2>/dev/null || sudo truncate -s 0 "$log_file" 2>/dev/null; then
                            ((CLEARED++))
                        fi
                    fi
                done <<< "$ADDITIONAL_LOGS"
            fi
        fi
    else
        echo "⚠️  Log directory not found: $Z2M_LOG_DIR"
    fi
    
    # Check systemd journal logs if running as service
    if systemctl is-active --quiet zigbee2mqtt 2>/dev/null; then
        echo ""
        echo "Clearing systemd journal logs for zigbee2mqtt..."
        if sudo journalctl --vacuum-time=1s --unit=zigbee2mqtt >/dev/null 2>&1; then
            echo "  ✅ Systemd logs cleared"
            ((CLEARED++))
        fi
    fi
    
else
    echo "❌ Could not detect zigbee2mqtt installation"
    echo ""
    echo "Please specify the installation type:"
    echo "  1. Docker/Docker Compose"
    echo "  2. Direct installation"
    echo ""
    echo "Or manually clear logs:"
    echo "  Docker: docker logs zigbee2mqtt (then clear with truncate)"
    echo "  Direct: Clear files in /opt/zigbee2mqtt/data/log/"
    exit 1
fi

echo ""
echo "=========================================="
echo "Summary:"
echo "  ✅ Logs cleared: $CLEARED"
echo "=========================================="

# Show disk space
echo ""
echo "Current disk usage:"
df -h / | tail -1

# Show Docker disk usage if Docker is used
if [ "$Z2M_INSTALL_TYPE" = "docker-compose" ] || [ "$Z2M_INSTALL_TYPE" = "docker" ]; then
    echo ""
    echo "Docker disk usage:"
    docker system df 2>/dev/null || echo "Could not get Docker disk usage"
fi

echo ""
echo "✅ Done!"
echo ""
echo "Note: You may need to restart zigbee2mqtt for changes to take effect:"
if [ "$Z2M_INSTALL_TYPE" = "docker-compose" ]; then
    echo "  docker-compose restart zigbee2mqtt"
elif [ "$Z2M_INSTALL_TYPE" = "docker" ]; then
    echo "  docker restart zigbee2mqtt"
elif [ "$Z2M_INSTALL_TYPE" = "direct" ]; then
    echo "  sudo systemctl restart zigbee2mqtt"
    echo "  # Or: cd /opt/zigbee2mqtt && npm start"
fi
