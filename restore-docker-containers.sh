#!/bin/bash
# restore-docker-containers.sh
# Restore docker-compose containers after they were removed

set -e

echo "=========================================="
echo "Restore Docker Compose Containers"
echo "=========================================="
echo ""

# Check if docker-compose.yml exists
if [ ! -f "docker-compose.yml" ] && [ ! -f "docker-compose.yaml" ]; then
    echo "❌ ERROR: docker-compose.yml not found in current directory"
    echo "Please run this script from your docker-compose directory"
    exit 1
fi

echo "Step 1: Checking docker-compose file..."
if [ -f "docker-compose.yml" ]; then
    echo "  Found: docker-compose.yml"
elif [ -f "docker-compose.yaml" ]; then
    echo "  Found: docker-compose.yaml"
fi

echo ""
echo "Step 2: Recreating containers..."
echo "  Running: docker-compose up -d"
echo ""

# Try docker-compose first, then docker compose (newer syntax)
if command -v docker-compose &> /dev/null; then
    docker-compose up -d
elif docker compose version &> /dev/null; then
    docker compose up -d
else
    echo "❌ ERROR: docker-compose not found"
    exit 1
fi

echo ""
echo "Step 3: Checking container status..."
sleep 2

if command -v docker-compose &> /dev/null; then
    docker-compose ps
else
    docker compose ps
fi

echo ""
echo "Step 4: Checking container logs..."
echo "  Showing last 10 lines of logs for each service..."
echo ""

if command -v docker-compose &> /dev/null; then
    docker-compose logs --tail=10
else
    docker compose logs --tail=10
fi

echo ""
echo "=========================================="
echo "✅ Containers restored!"
echo "=========================================="
echo ""
echo "To view logs in real-time:"
echo "  docker-compose logs -f"
echo ""
echo "To check status:"
echo "  docker-compose ps"
echo ""
echo "To restart if needed:"
echo "  docker-compose restart"
