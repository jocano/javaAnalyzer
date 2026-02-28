# Filter Docker Compose Logs - Remove Debug Messages

## Method 1: Exclude Debug Level (Recommended)

### Exclude "debug" messages:
```bash
docker-compose logs | grep -v "debug:"
```

### Exclude multiple levels:
```bash
docker-compose logs | grep -vE "debug:|trace:"
```

### Show only info, warn, and error:
```bash
docker-compose logs | grep -E "info:|warn:|error:"
```

## Method 2: Filter by Log Level

### Show only errors:
```bash
docker-compose logs | grep -i "error"
```

### Show only warnings and errors:
```bash
docker-compose logs | grep -E "warn|error" -i
```

### Show only info level and above:
```bash
docker-compose logs | grep -vE "debug|trace" -i
```

## Method 3: Filter Specific Service

### Filter debug from specific service:
```bash
docker-compose logs zigbee2mqtt | grep -v "debug:"
```

### Show only errors from specific service:
```bash
docker-compose logs zigbee2mqtt | grep -i "error"
```

## Method 4: Real-time Filtering (Follow Mode)

### Follow logs excluding debug:
```bash
docker-compose logs -f | grep -v "debug:"
```

### Follow logs showing only errors and warnings:
```bash
docker-compose logs -f | grep -E "warn|error" -i
```

### Follow logs for specific service excluding debug:
```bash
docker-compose logs -f zigbee2mqtt | grep -v "debug:"
```

## Method 4: Advanced Filtering with Multiple Patterns

### Exclude debug and specific patterns:
```bash
docker-compose logs | grep -vE "debug:|zh:adapter:discovery|zh:controller"
```

### Show only important messages:
```bash
docker-compose logs | grep -E "info:.*started|info:.*error|warn:|error:" | grep -v "debug:"
```

## Method 5: Filter by Timestamp and Level

### Show recent logs (last 100 lines) without debug:
```bash
docker-compose logs --tail=100 | grep -v "debug:"
```

### Show logs since specific time without debug:
```bash
docker-compose logs --since 10m | grep -v "debug:"
```

## Method 6: Create an Alias for Easy Filtering

Add to your `~/.bashrc` or `~/.zshrc`:

```bash
# Filter docker-compose logs
alias dcl='docker-compose logs'
alias dcl-no-debug='docker-compose logs | grep -v "debug:"'
alias dcl-errors='docker-compose logs | grep -i "error"'
alias dcl-info='docker-compose logs | grep "info:"'
alias dcl-follow='docker-compose logs -f | grep -v "debug:"'
```

Then reload:
```bash
source ~/.bashrc
```

Usage:
```bash
dcl-no-debug zigbee2mqtt
dcl-errors
dcl-follow
```

## Method 7: Filter with sed (More Control)

### Remove lines containing "debug:":
```bash
docker-compose logs | sed '/debug:/d'
```

### Remove multiple patterns:
```bash
docker-compose logs | sed '/debug:/d; /trace:/d'
```

### Keep only error, warn, info:
```bash
docker-compose logs | sed -n '/error:/p; /warn:/p; /info:/p'
```

## Method 8: Filter by Service and Level

### Specific service, no debug:
```bash
docker-compose logs zigbee2mqtt | grep -v "debug:"
```

### Multiple services, no debug:
```bash
docker-compose logs zigbee2mqtt mqtt | grep -v "debug:"
```

## Method 9: Save Filtered Logs to File

### Save logs without debug messages:
```bash
docker-compose logs | grep -v "debug:" > filtered-logs.txt
```

### Save only errors and warnings:
```bash
docker-compose logs | grep -E "warn|error" -i > errors-warnings.txt
```

## Method 10: Custom Filter Script

Create a script `filter-logs.sh`:

```bash
#!/bin/bash
# filter-logs.sh - Filter docker-compose logs

SERVICE="${1:-}"  # Service name (optional)
LEVEL="${2:-no-debug}"  # Filter level

if [ -z "$SERVICE" ]; then
    LOGS=$(docker-compose logs)
else
    LOGS=$(docker-compose logs "$SERVICE")
fi

case "$LEVEL" in
    "no-debug")
        echo "$LOGS" | grep -v "debug:"
        ;;
    "errors-only")
        echo "$LOGS" | grep -i "error"
        ;;
    "warnings-errors")
        echo "$LOGS" | grep -E "warn|error" -i
        ;;
    "info-only")
        echo "$LOGS" | grep "info:"
        ;;
    "no-trace-debug")
        echo "$LOGS" | grep -vE "debug:|trace:"
        ;;
    *)
        echo "$LOGS"
        ;;
esac
```

Make executable and use:
```bash
chmod +x filter-logs.sh

# Usage examples:
./filter-logs.sh                    # All services, no debug
./filter-logs.sh zigbee2mqtt        # zigbee2mqtt only, no debug
./filter-logs.sh zigbee2mqtt errors-only  # Only errors
./filter-logs.sh zigbee2mqtt warnings-errors  # Warnings and errors
```

## Method 11: Using awk for Advanced Filtering

### Filter by log level column:
```bash
docker-compose logs | awk '!/debug:/ && !/trace:/'
```

### Show only specific log levels:
```bash
docker-compose logs | awk '/info:/ || /warn:/ || /error:/'
```

## Common Use Cases

### 1. Watch logs in real-time without debug:
```bash
docker-compose logs -f | grep -v "debug:"
```

### 2. Check for errors only:
```bash
docker-compose logs | grep -i "error" | tail -20
```

### 3. Monitor specific service without debug:
```bash
docker-compose logs -f zigbee2mqtt | grep -v "debug:"
```

### 4. Get last 50 lines without debug:
```bash
docker-compose logs --tail=50 | grep -v "debug:"
```

### 5. Search for specific pattern excluding debug:
```bash
docker-compose logs | grep -v "debug:" | grep "started"
```

## Quick Reference

```bash
# No debug messages
docker-compose logs | grep -v "debug:"

# Only errors
docker-compose logs | grep -i "error"

# Only warnings and errors
docker-compose logs | grep -E "warn|error" -i

# Follow mode without debug
docker-compose logs -f | grep -v "debug:"

# Specific service, no debug
docker-compose logs zigbee2mqtt | grep -v "debug:"

# Last 100 lines, no debug
docker-compose logs --tail=100 | grep -v "debug:"
```

## Pro Tips

1. **Combine filters**: `docker-compose logs | grep -v "debug:" | grep "error" -i`
2. **Save filtered output**: `docker-compose logs | grep -v "debug:" > clean-logs.txt`
3. **Use tail for recent**: `docker-compose logs --tail=50 | grep -v "debug:"`
4. **Follow specific service**: `docker-compose logs -f zigbee2mqtt | grep -v "debug:"`
5. **Count errors**: `docker-compose logs | grep -i "error" | wc -l`
