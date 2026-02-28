# Check Raspberry Pi SD Card Free Space

## Quick Commands

### Method 1: Using `df` (Most Common)

```bash
# Show disk space in human-readable format
df -h

# Show only root filesystem
df -h /

# Show specific filesystem
df -h /dev/root
```

**Example Output:**
```
Filesystem      Size  Used Avail Use% Mounted on
/dev/root        29G   15G   13G  54% /
devtmpfs        459M     0  459M   0% /dev
tmpfs           464M     0  464M   0% /dev/shm
tmpfs           186M  1.1M  185M   1% /run
tmpfs           5.0M     0  5.0M   0% /run/lock
/dev/mmcblk0p1  253M   54M  199M  22% /boot
```

### Method 2: Using `du` (Disk Usage)

```bash
# Show disk usage of current directory
du -h

# Show total size of directory
du -sh /path/to/directory

# Show sizes sorted by largest
du -h / | sort -h | tail -20
```

### Method 3: One-Liner Quick Check

```bash
# Show free space on root filesystem
df -h / | tail -1 | awk '{print "Free: " $4 " / Total: " $2}'

# Or simpler
df -h / | grep -v Filesystem | awk '{print "Free: " $4 " / Total: " $2 " / Used: " $5}'
```

## Detailed Commands

### Show All Filesystems

```bash
df -h
```

Shows:
- `Size` - Total size
- `Used` - Space used
- `Avail` - Available space
- `Use%` - Percentage used
- `Mounted on` - Mount point

### Show Root Filesystem Only

```bash
# Human-readable
df -h /

# In megabytes
df -m /

# In gigabytes
df -BG /
```

### Show Boot Partition

```bash
df -h /boot
```

## Check Specific Directories

### Check Docker Disk Usage

```bash
# Docker disk usage
docker system df

# Docker disk usage (verbose)
docker system df -v

# Clean up Docker
docker system prune -a
```

### Check Largest Directories

```bash
# Top 10 largest directories in root
sudo du -h --max-depth=1 / 2>/dev/null | sort -h | tail -10

# Top 10 largest directories in home
du -h --max-depth=1 ~ | sort -h | tail -10
```

### Check Log Files

```bash
# Check log sizes
sudo du -sh /var/log/* | sort -h

# Check journal logs
journalctl --disk-usage
```

## Quick Script: Disk Space Summary

Create `check-disk-space.sh`:

```bash
#!/bin/bash
echo "=== Raspberry Pi Disk Space Summary ==="
echo ""

# Root filesystem
echo "Root Filesystem (/):"
df -h / | tail -1 | awk '{printf "  Total: %s\n  Used: %s (%s)\n  Free: %s\n", $2, $3, $5, $4}'
echo ""

# Boot partition
echo "Boot Partition (/boot):"
df -h /boot | tail -1 | awk '{printf "  Total: %s\n  Used: %s (%s)\n  Free: %s\n", $2, $3, $5, $4}'
echo ""

# Docker (if installed)
if command -v docker &> /dev/null; then
    echo "Docker Disk Usage:"
    docker system df
    echo ""
fi

# Top 10 largest directories
echo "Top 10 Largest Directories in /:"
sudo du -h --max-depth=1 / 2>/dev/null | sort -h | tail -10 | awk '{printf "  %s\n", $0}'
```

Make executable and run:
```bash
chmod +x check-disk-space.sh
./check-disk-space.sh
```

## Monitoring Disk Space

### Watch Disk Space (Updates Every 2 Seconds)

```bash
watch -n 2 df -h
```

Press `Ctrl+C` to exit.

### Check Disk Space Regularly

```bash
# Add to crontab to check daily
crontab -e

# Add line:
0 9 * * * df -h / | mail -s "Disk Space Report" your@email.com
```

## Free Up Space

### Common Space Cleanup Commands

```bash
# Clean package cache
sudo apt-get clean
sudo apt-get autoremove -y

# Clean Docker (if using)
docker system prune -a -f

# Clean logs (be careful!)
sudo journalctl --vacuum-time=7d  # Keep only 7 days of logs
sudo journalctl --vacuum-size=100M  # Keep only 100MB of logs

# Find large files
sudo find / -type f -size +100M 2>/dev/null

# Remove old kernels (if not needed)
sudo apt-get purge -y $(dpkg -l | grep '^ii linux-image' | awk '{print $2}' | tail -n +4)
```

### Check What's Using Space

```bash
# Find largest files
sudo find / -type f -size +100M -exec ls -lh {} \; 2>/dev/null | \
  awk '{print $5, $9}' | sort -h

# Check specific directories
du -sh /var/* 2>/dev/null | sort -h
du -sh /home/* 2>/dev/null | sort -h
du -sh /opt/* 2>/dev/null | sort -h
```

## Quick Reference

### Most Common Commands

```bash
# Show disk space (human-readable)
df -h

# Show root filesystem only
df -h /

# Show boot partition
df -h /boot

# Watch disk space
watch -n 5 df -h

# Check Docker space
docker system df

# Find large files
sudo find / -type f -size +100M 2>/dev/null
```

### Quick One-Liner

```bash
# Just show free space
df -h / | awk 'NR==2 {print "Free: " $4 " / Total: " $2 " / Used: " $5}'
```

## Alerts for Low Disk Space

### Script to Alert on Low Space

Create `check-disk-alert.sh`:

```bash
#!/bin/bash
THRESHOLD=80  # Alert if usage > 80%

USAGE=$(df / | tail -1 | awk '{print $5}' | sed 's/%//')

if [ $USAGE -gt $THRESHOLD ]; then
    echo "⚠️  WARNING: Disk usage is ${USAGE}% (threshold: ${THRESHOLD}%)"
    df -h /
    exit 1
else
    echo "✅ Disk usage is ${USAGE}% (OK)"
    exit 0
fi
```

Run:
```bash
chmod +x check-disk-alert.sh
./check-disk-alert.sh
```

## Summary

**Quickest command:**
```bash
df -h /
```

**Show everything:**
```bash
df -h
```

**Watch in real-time:**
```bash
watch -n 5 df -h
```

These commands will show you how much free space is available on your Raspberry Pi SD card!


