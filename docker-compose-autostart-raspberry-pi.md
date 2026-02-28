# Auto-Start Docker Compose on Raspberry Pi Reboot

## Method 1: Using Restart Policy in docker-compose.yml (Recommended)

Edit your `docker-compose.yml` and add `restart` policy to services:

```yaml
version: '3.8'

services:
  zigbee2mqtt:
    image: koenkk/zigbee2mqtt
    restart: always  # ← Add this
    # ... other config

  mosquitto:
    image: eclipse-mosquitto
    restart: always  # ← Add this
    # ... other config

  node-red:
    image: nodered/node-red
    restart: always  # ← Add this
    # ... other config
```

**Restart policies:**
- `no` - Never restart (default)
- `always` - Always restart if container stops
- `on-failure` - Restart only on failure
- `unless-stopped` - Always restart unless manually stopped

**After adding restart policies:**
```bash
# Restart docker-compose to apply
docker-compose down
docker-compose up -d
```

## Method 2: Create Systemd Service (Better Control)

Create a systemd service to manage docker-compose:

### Step 1: Create service file

```bash
sudo nano /etc/systemd/system/docker-compose.service
```

### Step 2: Add service configuration

```ini
[Unit]
Description=Docker Compose Application Service
Requires=docker.service
After=docker.service

[Service]
Type=oneshot
RemainAfterExit=yes
WorkingDirectory=/path/to/your/docker-compose/directory
ExecStart=/usr/local/bin/docker-compose up -d
ExecStop=/usr/local/bin/docker-compose down
TimeoutStartSec=0

[Install]
WantedBy=multi-user.target
```

**Important:** Replace `/path/to/your/docker-compose/directory` with your actual path (e.g., `/opt/zigbee2mqtt` or `/home/pi/docker-compose`)

### Step 3: Enable and start service

```bash
# Reload systemd
sudo systemctl daemon-reload

# Enable service (start on boot)
sudo systemctl enable docker-compose.service

# Start service now
sudo systemctl start docker-compose.service

# Check status
sudo systemctl status docker-compose.service
```

### Step 4: Verify it works

```bash
# Check if containers are running
docker-compose ps

# Check service logs
sudo journalctl -u docker-compose -f
```

## Method 3: Using docker-compose path (If installed via pip)

If docker-compose is installed via pip, update the path:

```bash
# Find docker-compose location
which docker-compose
# Usually: /usr/local/bin/docker-compose or /usr/bin/docker-compose

# Update service file with correct path
sudo nano /etc/systemd/system/docker-compose.service
```

Or use `docker compose` (newer Docker CLI plugin):

```ini
[Unit]
Description=Docker Compose Application Service
Requires=docker.service
After=docker.service

[Service]
Type=oneshot
RemainAfterExit=yes
WorkingDirectory=/path/to/your/docker-compose/directory
ExecStart=/usr/bin/docker compose up -d
ExecStop=/usr/bin/docker compose down
TimeoutStartSec=0

[Install]
WantedBy=multi-user.target
```

## Method 4: Using rc.local (Simple but less robust)

Edit `/etc/rc.local`:

```bash
sudo nano /etc/rc.local
```

Add before `exit 0`:

```bash
cd /path/to/your/docker-compose/directory
/usr/local/bin/docker-compose up -d &
```

Make sure the file ends with `exit 0`.

## Method 5: Using Crontab @reboot

```bash
# Edit crontab
crontab -e

# Add this line (replace path with your docker-compose directory)
@reboot cd /path/to/your/docker-compose/directory && /usr/local/bin/docker-compose up -d
```

## Complete Systemd Service Example

Here's a complete example for a specific directory:

```bash
# Create service file
sudo nano /etc/systemd/system/docker-compose.service
```

```ini
[Unit]
Description=Docker Compose Application Service
Requires=docker.service
After=docker.service

[Service]
Type=oneshot
RemainAfterExit=yes
WorkingDirectory=/opt/zigbee2mqtt
ExecStart=/usr/local/bin/docker-compose up -d
ExecStop=/usr/local/bin/docker-compose down
TimeoutStartSec=0
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

Then:

```bash
# Reload and enable
sudo systemctl daemon-reload
sudo systemctl enable docker-compose.service
sudo systemctl start docker-compose.service

# Check status
sudo systemctl status docker-compose.service
```

## Find Your Docker Compose Directory

```bash
# Find where your docker-compose.yml is
find ~ -name "docker-compose.yml" 2>/dev/null

# Or if you know it's in a specific location
ls -la /opt/zigbee2mqtt/docker-compose.yml
ls -la ~/docker-compose/docker-compose.yml
```

## Verify Auto-Start is Working

```bash
# 1. Enable service
sudo systemctl enable docker-compose.service

# 2. Reboot to test
sudo reboot

# 3. After reboot, check if containers are running
docker-compose ps

# 4. Check service status
sudo systemctl status docker-compose.service
```

## Troubleshooting

### Service fails to start:

```bash
# Check logs
sudo journalctl -u docker-compose -n 50

# Check if docker-compose path is correct
which docker-compose

# Check if working directory exists and has docker-compose.yml
ls -la /path/to/your/docker-compose/directory
```

### Containers don't start after reboot:

```bash
# Check Docker is running
sudo systemctl status docker

# Check docker-compose service
sudo systemctl status docker-compose

# Manually test
cd /path/to/your/docker-compose/directory
docker-compose up -d
```

## Quick Setup Script

Create a setup script:

```bash
#!/bin/bash
# setup-docker-compose-autostart.sh

DOCKER_COMPOSE_DIR="/path/to/your/docker-compose/directory"  # CHANGE THIS
DOCKER_COMPOSE_CMD=$(which docker-compose || echo "/usr/local/bin/docker-compose")

echo "Setting up docker-compose auto-start..."
echo "Directory: $DOCKER_COMPOSE_DIR"
echo "Command: $DOCKER_COMPOSE_CMD"

# Create service file
sudo tee /etc/systemd/system/docker-compose.service > /dev/null <<EOF
[Unit]
Description=Docker Compose Application Service
Requires=docker.service
After=docker.service

[Service]
Type=oneshot
RemainAfterExit=yes
WorkingDirectory=$DOCKER_COMPOSE_DIR
ExecStart=$DOCKER_COMPOSE_CMD up -d
ExecStop=$DOCKER_COMPOSE_CMD down
TimeoutStartSec=0

[Install]
WantedBy=multi-user.target
EOF

# Reload and enable
sudo systemctl daemon-reload
sudo systemctl enable docker-compose.service
sudo systemctl start docker-compose.service

echo "✅ Service created and enabled!"
echo "Check status: sudo systemctl status docker-compose.service"
```

Make executable and run:
```bash
chmod +x setup-docker-compose-autostart.sh
./setup-docker-compose-autostart.sh
```

## Recommendation

**Best approach:** Use **Method 1 (restart policy)** + **Method 2 (systemd service)**

- Restart policy in docker-compose.yml handles individual container restarts
- Systemd service ensures docker-compose starts on boot

This gives you:
- Containers restart automatically if they crash
- docker-compose starts on system boot
- Better control and monitoring via systemd
