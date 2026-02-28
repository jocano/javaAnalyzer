# Edit Raspberry Pi Files from Mac using VSCode

## Method 1: Using VSCode Remote-SSH Extension (Recommended)

### Step 1: Enable SSH on Raspberry Pi

```bash
# SSH into Raspberry Pi
ssh pi@<raspberry-pi-ip>

# Enable SSH service (if not already enabled)
sudo systemctl enable ssh
sudo systemctl start ssh

# Check SSH status
sudo systemctl status ssh

# Find Raspberry Pi IP address
hostname -I
# Or
ip addr show | grep inet
```

### Step 2: Install Remote-SSH Extension in VSCode

1. Open VSCode on your Mac
2. Click Extensions icon (or `Cmd+Shift+X`)
3. Search for "Remote - SSH"
4. Install "Remote - SSH" by Microsoft
5. Restart VSCode if prompted

### Step 3: Configure SSH Connection

#### Option A: Using VSCode UI

1. Click Remote Explorer icon in left sidebar (or `Cmd+Shift+P`)
2. Click "+" next to "SSH Targets"
3. Enter SSH connection command:
   ```
   ssh pi@<raspberry-pi-ip>
   ```
   Or with custom port:
   ```
   ssh pi@<raspberry-pi-ip> -p <port>
   ```
4. Select SSH config file location (usually `~/.ssh/config`)
5. Click "Connect"

#### Option B: Edit SSH Config Manually

1. On your Mac, edit SSH config:
   ```bash
   nano ~/.ssh/config
   ```

2. Add Raspberry Pi configuration:
   ```
   Host raspberrypi
       HostName <raspberry-pi-ip>
       User pi
       Port 22
       IdentityFile ~/.ssh/id_rsa
   ```

   **Example:**
   ```
   Host raspberrypi
       HostName 192.168.1.100
       User pi
       Port 22
   ```

3. Save and close (`Ctrl+X`, `Y`, `Enter`)

4. Test SSH connection:
   ```bash
   ssh raspberrypi
   ```

### Step 4: Connect from VSCode

1. Press `Cmd+Shift+P` to open command palette
2. Type "Remote-SSH: Connect to Host"
3. Select your configured host (e.g., "raspberrypi")
4. Enter password when prompted
5. VSCode will connect and install VSCode Server on Raspberry Pi (first time only)

### Step 5: Open Folder/Workspace

1. Once connected, click "Open Folder"
2. Navigate to the folder you want to edit
   - Example: `/home/pi/docker-compose/`
   - Example: `/opt/zigbee2mqtt/`
   - Example: `/home/pi/.node-red/`
3. Click "OK"

Now you can edit files directly on Raspberry Pi!

## Method 2: Using SSH Key Authentication (No Password Required)

### Step 1: Generate SSH Key on Mac (if not exists)

```bash
# Check if you have SSH keys
ls -la ~/.ssh/id_rsa*

# If not, generate one
ssh-keygen -t rsa -b 4096 -C "your_email@example.com"
# Press Enter for default location
# Optionally set a passphrase
```

### Step 2: Copy SSH Key to Raspberry Pi

```bash
# Copy public key to Raspberry Pi
ssh-copy-id pi@<raspberry-pi-ip>

# Or manually:
cat ~/.ssh/id_rsa.pub | ssh pi@<raspberry-pi-ip> "mkdir -p ~/.ssh && cat >> ~/.ssh/authorized_keys"

# Test passwordless login
ssh pi@<raspberry-pi-ip>
# Should not ask for password
```

### Step 3: Update SSH Config

```bash
nano ~/.ssh/config
```

```
Host raspberrypi
    HostName <raspberry-pi-ip>
    User pi
    IdentityFile ~/.ssh/id_rsa
    IdentitiesOnly yes
```

### Step 4: Connect from VSCode

Now you can connect without entering password each time!

## Method 3: Using VSCode Remote-SSH via Command Line

```bash
# From Mac terminal
code --remote ssh-remote+raspberrypi /home/pi/docker-compose
```

## Common Use Cases

### Edit Docker Compose Files

```bash
# Connect to Raspberry Pi via VSCode Remote-SSH
# Open folder: /opt/zigbee2mqtt/ or /home/pi/docker-compose/
# Edit docker-compose.yml
```

### Edit Node-RED Flows

```bash
# Connect via Remote-SSH
# Open folder: /home/pi/.node-red/
# Edit flows.json or settings.js
```

### Edit Configuration Files

```bash
# Connect via Remote-SSH
# Navigate to any folder:
# - /etc/ - System configs
# - /home/pi/ - User files
# - /opt/ - Application files
```

## Troubleshooting

### Issue 1: "Could not establish connection"

**Solutions:**
```bash
# Verify SSH is enabled on Raspberry Pi
sudo systemctl status ssh

# Test SSH connection from Mac
ssh pi@<raspberry-pi-ip>

# Check firewall
sudo ufw status
sudo ufw allow ssh
```

### Issue 2: "Permission denied (publickey)"

**Solutions:**
```bash
# Use password authentication first
# In VSCode, use: ssh pi@<ip> -o PreferredAuthentications=password

# Or set up SSH keys (see Method 2)
ssh-copy-id pi@<raspberry-pi-ip>
```

### Issue 3: VSCode Server installation fails

**Solutions:**
```bash
# Check disk space on Raspberry Pi
df -h

# Manually install VSCode Server (if needed)
# VSCode will prompt and try again automatically
```

### Issue 4: Connection is slow

**Solutions:**
```bash
# Use ethernet instead of WiFi
# Or optimize SSH config:

# Edit ~/.ssh/config on Mac:
Host raspberrypi
    HostName <ip>
    User pi
    Compression yes
    ServerAliveInterval 60
    ServerAliveCountMax 3
```

## Tips and Tricks

### 1. Multiple Raspberry Pis

Edit `~/.ssh/config`:

```
Host raspberrypi
    HostName 192.168.1.100
    User pi

Host raspberrypi2
    HostName 192.168.1.101
    User pi
```

### 2. Use Different Usernames

```
Host raspberrypi
    HostName 192.168.1.100
    User root  # or any other user
```

### 3. Custom SSH Port

```
Host raspberrypi
    HostName 192.168.1.100
    User pi
    Port 2222  # Custom SSH port
```

### 4. Keep Connection Alive

Add to `~/.ssh/config`:

```
Host *
    ServerAliveInterval 60
    ServerAliveCountMax 3
```

## Quick Setup Commands

```bash
# 1. Enable SSH on Raspberry Pi (if not done)
sudo systemctl enable ssh
sudo systemctl start ssh

# 2. Find Raspberry Pi IP
hostname -I

# 3. On Mac, test connection
ssh pi@<raspberry-pi-ip>

# 4. Copy SSH key (optional, for passwordless login)
ssh-copy-id pi@<raspberry-pi-ip>

# 5. Add to SSH config on Mac
nano ~/.ssh/config
# Add configuration (see examples above)

# 6. Open VSCode, install Remote-SSH extension
# 7. Connect: Cmd+Shift+P → "Remote-SSH: Connect to Host"
# 8. Select your host and connect!
```

## Accessing Raspberry Pi from VSCode

Once connected:
- ✅ Edit any file on Raspberry Pi
- ✅ Use terminal within VSCode (`Ctrl+` or `Cmd+`)
- ✅ Install extensions (they run on Raspberry Pi)
- ✅ Use Git, debuggers, and all VSCode features
- ✅ Edit docker-compose.yml, Node-RED flows, configs, etc.

## Example: Edit Docker Compose File

1. Connect to Raspberry Pi via Remote-SSH
2. Open folder: `/opt/zigbee2mqtt/` or wherever your docker-compose.yml is
3. Edit `docker-compose.yml` in VSCode
4. Save (auto-syncs to Raspberry Pi)
5. In terminal within VSCode:
   ```bash
   docker-compose restart
   ```

## Summary

**Quick Steps:**
1. Enable SSH on Raspberry Pi: `sudo systemctl enable ssh`
2. Install "Remote - SSH" extension in VSCode
3. Connect: `Cmd+Shift+P` → "Remote-SSH: Connect to Host"
4. Enter: `ssh pi@<raspberry-pi-ip>`
5. Open folder you want to edit
6. Start editing!

You can now edit files on Raspberry Pi directly from VSCode on your Mac! 🎉


