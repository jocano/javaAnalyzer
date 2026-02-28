# Nano Text Editor Commands Guide

## Basic Usage

### Opening Files
```bash
# Open a file
nano filename.txt

# Open file and go to specific line
nano +line_number filename.txt

# Example: Open file at line 50
nano +50 filename.txt
```

### Creating New Files
```bash
# Create and open new file
nano newfile.txt

# If file doesn't exist, nano creates it when you save
```

## Essential Commands

### Saving and Exiting

| Command | Action | Description |
|---------|--------|-------------|
| `Ctrl + O` | **Write Out** | Save file (asks for filename if new) |
| `Ctrl + X` | **Exit** | Exit nano (prompts to save if unsaved changes) |
| `Ctrl + S` | **Save** | Quick save (in some versions) |

**Quick Save & Exit:**
1. Press `Ctrl + O` to save
2. Press `Enter` to confirm filename
3. Press `Ctrl + X` to exit

### Cursor Movement

| Command | Action | Description |
|---------|--------|-------------|
| `Ctrl + F` | Forward | Move forward one character |
| `Ctrl + B` | Back | Move backward one character |
| `Ctrl + P` | Previous | Move up one line |
| `Ctrl + N` | Next | Move down one line |
| `Ctrl + A` | Beginning | Move to start of line |
| `Ctrl + E` | End | Move to end of line |
| `Ctrl + V` | Page Down | Move down one page |
| `Ctrl + Y` | Page Up | Move up one page |
| `Alt + \` | Beginning | Move to beginning of file |
| `Alt + /` | End | Move to end of file |

### Text Selection and Copy/Paste

| Command | Action | Description |
|---------|--------|-------------|
| `Alt + A` | Mark | Start/stop text selection |
| `Alt + 6` | Copy | Copy selected text |
| `Ctrl + U` | Paste | Paste text at cursor |
| `Ctrl + K` | Cut | Cut entire line (or selected text) |
| `Ctrl + 6` | Copy | Copy (in some versions) |

**To Copy/Paste:**
1. `Alt + A` to start marking
2. Move cursor to select text
3. `Alt + 6` to copy (or `Ctrl + K` to cut)
4. Move cursor to destination
5. `Ctrl + U` to paste

### Search and Replace

| Command | Action | Description |
|---------|--------|-------------|
| `Ctrl + W` | Where Is | Search for text |
| `Alt + W` | Next | Find next occurrence |
| `Ctrl + \` | Replace | Find and replace |

**Search and Replace Steps:**
1. `Ctrl + \` to start replace
2. Enter text to find
3. Press `Enter`
4. Enter replacement text
5. Press `Enter`
6. Choose: `Y` (yes), `N` (no), `A` (all), or `Ctrl + C` (cancel)

### Editing Commands

| Command | Action | Description |
|---------|--------|-------------|
| `Ctrl + D` | Delete | Delete character under cursor |
| `Backspace` | Delete | Delete character before cursor |
| `Ctrl + H` | Delete | Delete character before cursor |
| `Ctrl + K` | Cut Line | Cut entire line |
| `Alt + T` | Cut To End | Cut from cursor to end of line |
| `Ctrl + U` | Uncut | Paste cut text (undo cut) |

### Undo and Redo

| Command | Action | Description |
|---------|--------|-------------|
| `Alt + U` | Undo | Undo last action |
| `Alt + E` | Redo | Redo (in some versions) |

### Indentation

| Command | Action | Description |
|---------|--------|-------------|
| `Alt + }` | Indent | Indent current line or selection |
| `Alt + {` | Unindent | Unindent current line or selection |

## Useful Shortcuts

### File Operations
```bash
Ctrl + O    # Save file
Ctrl + X    # Exit nano
Ctrl + R    # Read/Insert file into current file
Ctrl + W    # Search
Ctrl + \    # Find and replace
```

### Line Operations
```bash
Ctrl + K    # Cut entire line
Ctrl + U    # Paste (paste cut line)
Alt + T     # Cut to end of line
Alt + 6     # Copy (when text is marked)
```

### Navigation
```bash
Ctrl + A    # Beginning of line
Ctrl + E    # End of line
Alt + \     # Beginning of file
Alt + /     # End of file
Ctrl + W    # Search
Alt + W     # Next search result
```

## Common Workflows

### Edit and Save a File
1. `nano filename.txt` - Open file
2. Edit text
3. `Ctrl + O` - Save
4. `Enter` - Confirm
5. `Ctrl + X` - Exit

### Find Text
1. `Ctrl + W` - Open search
2. Type search term
3. `Enter` - Find
4. `Alt + W` - Find next occurrence

### Copy a Line
1. Move cursor to line
2. `Ctrl + K` - Cut line
3. Move cursor to destination
4. `Ctrl + U` - Paste

### Cut Multiple Lines
1. Move cursor to first line
2. `Ctrl + K` - Cut line (repeat for each line)
3. Move cursor to destination
4. `Ctrl + U` - Paste all

### Select and Copy Text
1. `Alt + A` - Start marking
2. Move cursor to select text
3. `Alt + 6` - Copy
4. Move cursor to destination
5. `Ctrl + U` - Paste

## Status Bar Indicators

At the bottom of nano, you'll see:

```
^G Get Help    ^O Write Out   ^W Where Is    ^K Cut Text    ^J Justify
^X Exit        ^R Read File   ^\ Replace     ^U Uncut Text  ^T To Spell
```

- `^` = `Ctrl` key
- `M-` = `Alt` key (Meta key)

## Getting Help in Nano

```bash
# While in nano, press:
Ctrl + G    # Get help (shows all commands)

# Exit help
Ctrl + X
```

## Nano Configuration

### Syntax Highlighting

Enable syntax highlighting:

```bash
# Check if syntax files exist
ls /usr/share/nano/

# Enable in ~/.nanorc
nano ~/.nanorc
```

Add:
```
include /usr/share/nano/*.nanorc
```

### Useful .nanorc Settings

Create/edit `~/.nanorc`:

```bash
nano ~/.nanorc
```

Add settings:
```
# Show line numbers
set linenumbers

# Enable mouse (if terminal supports it)
set mouse

# Use tabs for indentation (spaces instead of tabs)
set tabsize 4
set tabstospaces

# Auto-indent
set autoindent

# Show whitespace
set whitespace "·"

# Smooth scrolling
set smooth
```

## Tips and Tricks

### 1. Open File at Specific Line
```bash
nano +50 filename.txt  # Opens at line 50
```

### 2. View Multiple Files
```bash
# Open multiple files (not simultaneously in same window)
nano file1.txt
# Exit and open another
nano file2.txt
```

### 3. Read Another File into Current File
```bash
# While editing, press:
Ctrl + R    # Read file
# Enter filename to insert
```

### 4. Search with Regular Expressions (Advanced)
```bash
# In search (Ctrl + W), use regex:
^text       # Lines starting with "text"
text$       # Lines ending with "text"
```

### 5. Backup Before Editing
```bash
# Always good to backup:
cp filename.txt filename.txt.backup
nano filename.txt
```

## Common Mistakes and Fixes

### Mistake: Accidentally Exited Without Saving
**Fix:** Nano prompts you before exiting if there are unsaved changes. Press `N` to cancel exit, then `Ctrl + O` to save.

### Mistake: Cut Line by Accident
**Fix:** Press `Ctrl + U` immediately to paste it back (undo cut).

### Mistake: Lost Cursor Position
**Fix:** Use `Ctrl + W` to search, or `Alt + \` to go to beginning, `Alt + /` to go to end.

## Quick Reference Card

```
SAVE & EXIT:
  Ctrl + O  = Save (Write Out)
  Ctrl + X  = Exit
  Enter     = Confirm

MOVEMENT:
  Ctrl + A  = Start of line
  Ctrl + E  = End of line
  Ctrl + F  = Forward (right)
  Ctrl + B  = Backward (left)
  Ctrl + P  = Previous (up)
  Ctrl + N  = Next (down)
  Alt + \   = Beginning of file
  Alt + /   = End of file

EDIT:
  Ctrl + K  = Cut line
  Ctrl + U  = Paste
  Ctrl + D  = Delete character
  Backspace = Delete character before cursor

SEARCH:
  Ctrl + W  = Search
  Alt + W   = Find next
  Ctrl + \  = Find and replace

COPY/PASTE:
  Alt + A   = Mark text (start selection)
  Alt + 6   = Copy marked text
  Ctrl + U  = Paste

HELP:
  Ctrl + G  = Get help
```

## Summary

**Most Important Commands:**
- `Ctrl + O` - Save
- `Ctrl + X` - Exit
- `Ctrl + W` - Search
- `Ctrl + K` - Cut line
- `Ctrl + U` - Paste
- `Ctrl + G` - Help

Remember: `^` means `Ctrl`, and commands are shown at the bottom of the nano screen!


