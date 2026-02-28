#!/bin/bash
# fix-node-red-failure.sh
# Fix Node-RED service failure on Raspberry Pi

echo "=== Node-RED Failure Diagnostic ==="
echo ""

echo "1. Checking recent error logs..."
sudo journalctl -u nodered -n 30 --no-pager | tail -20
echo ""

echo "2. Checking for common issues..."
echo ""

# Check Node.js version
echo "Node.js version:"
node --version 2>/dev/null || echo "   ❌ Node.js not found"
echo ""

# Check Node-RED installation
echo "Node-RED installation:"
which node-red-pi 2>/dev/null && echo "   ✅ node-red-pi found" || echo "   ❌ node-red-pi not found"
echo ""

# Check .node-red directory
echo "Node-RED directory:"
if [ -d ~/.node-red ]; then
    echo "   ✅ ~/.node-red exists"
    ls -la ~/.node-red/ | head -5
else
    echo "   ⚠️  ~/.node-red not found (will be created on first run)"
fi
echo ""

# Check permissions
echo "Permissions check:"
if [ -d ~/.node-red ]; then
    ls -ld ~/.node-red | awk '{print "   Directory: " $1 " " $3 " " $4}'
fi
echo ""

echo "3. Common fixes to try:"
echo ""
echo "   a) Check full error: sudo journalctl -u nodered -n 50"
echo "   b) Fix permissions: sudo chown -R \$USER:\$USER ~/.node-red"
echo "   c) Reinstall Node-RED: bash <(curl -sL https://raw.githubusercontent.com/node-red/linux-installers/master/deb/update-nodejs-and-nodered)"
echo "   d) Check Node.js: node --version"
echo ""
