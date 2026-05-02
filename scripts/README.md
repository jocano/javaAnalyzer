# Authentication Scripts

This directory contains scripts to authenticate with the Spring Boot application and retrieve JWT tokens.

## Available Scripts

### 1. **login.html** - Browser-based Login (Recommended for Quick Testing)
   - **Platform**: Any (Windows, Mac, Linux)
   - **Requirements**: Web browser (Chrome, Firefox, Edge, etc.)
   - **Usage**: 
     - Open `login.html` in your web browser
     - Enter username, password, and server URL
     - Click "Login & Get Token"
     - Copy the token or authorization header

### 2. **login.ps1** - PowerShell Script (Windows)
   - **Platform**: Windows (PowerShell 5.1+)
   - **Requirements**: PowerShell (included in Windows 10/11)
   - **Usage**:
     ```powershell
     .\login.ps1
     .\login.ps1 admin password
     .\login.ps1 admin password http://localhost:8080
     ```
   - Features:
     - Secure password input (hidden)
     - Automatic token extraction
     - Option to save token to file
     - Sets environment variable `$env:JWT_TOKEN`

### 3. **login.bat** - Windows Batch Script
   - **Platform**: Windows (CMD)
   - **Requirements**: curl (download from https://curl.se/download.html)
   - **Usage**:
     ```cmd
     login.bat
     login.bat admin password
     login.bat admin password http://localhost:8080
     ```
   - Features:
     - Works with Windows Command Prompt
     - Uses curl for HTTP requests
     - Simple token extraction with PowerShell

### 4. **login.js** - Node.js Script (Cross-platform)
   - **Platform**: Windows, Mac, Linux
   - **Requirements**: Node.js (download from https://nodejs.org/)
   - **Usage**:
     ```bash
     node login.js
     node login.js admin password
     node login.js admin password http://localhost:8080
     ```
   - Or with environment variables:
     ```bash
     USERNAME=admin PASSWORD=password SERVER_URL=http://localhost:8080 node login.js
     ```
   - Features:
     - Cross-platform support
     - Secure password input
     - No external dependencies (uses built-in Node.js modules)
     - Option to save token to file

## Default Credentials

The application comes with these pre-loaded users:
- **Username**: `admin`, **Password**: `admin123` (Role: ADMIN)
- **Username**: `user`, **Password**: `user123` (Role: USER)
- **Username**: `demo`, **Password**: `demo123` (Role: USER)

## Server URL

Default server URL: `http://localhost:8080`

If your server is running on a different host/port, update the `SERVER_URL` parameter.

## Using the Token

Once you have the token, use it in API requests:

### Using curl:
```bash
curl -H "Authorization: Bearer YOUR_TOKEN_HERE" \
  http://localhost:8080/api/protected/user-info
```

### Using PowerShell:
```powershell
$headers = @{
    "Authorization" = "Bearer YOUR_TOKEN_HERE"
}
Invoke-RestMethod -Uri "http://localhost:8080/api/protected/user-info" -Headers $headers
```

### Using Node.js:
```javascript
fetch('http://localhost:8080/api/protected/user-info', {
    headers: {
        'Authorization': 'Bearer YOUR_TOKEN_HERE'
    }
})
```

### Using JavaScript in Browser:
```javascript
fetch('http://localhost:8080/api/protected/user-info', {
    headers: {
        'Authorization': 'Bearer YOUR_TOKEN_HERE'
    }
})
```

## Examples

### Example 1: Quick Login with PowerShell
```powershell
PS> .\login.ps1 admin admin123
```
Output:
```
========================================
Authenticating to: http://localhost:8080
Username: admin
========================================

========================================
Authentication SUCCESSFUL!
========================================

Token: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
Username: admin
Role: ADMIN
```

### Example 2: Login and Use Token in Same Script
```powershell
# Get token
$token = .\login.ps1 admin admin123

# Use token
$headers = @{
    "Authorization" = "Bearer $token"
}
Invoke-RestMethod -Uri "http://localhost:8080/api/protected/user-info" -Headers $headers
```

### Example 3: Using Saved Token File
```bash
# Login and save token
node login.js admin admin123

# Use saved token
TOKEN=$(cat token.txt)
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/protected/user-info
```

## Troubleshooting

### Error: "Failed to connect to server"
- Ensure the Spring Boot application is running
- Check the server URL is correct
- Verify the port (default: 8080)

### Error: "Authentication FAILED" or "Invalid credentials"
- Verify username and password are correct
- Check that the user exists in the database
- Ensure the user account is enabled

### PowerShell Script Execution Policy Error
If you get an execution policy error in PowerShell:
```powershell
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```

### curl Not Found (Windows Batch Script)
- Download curl from: https://curl.se/download.html
- Or use PowerShell script instead: `login.ps1`

### Node.js Not Found
- Download and install Node.js from: https://nodejs.org/
- Verify installation: `node --version`

## IntelliJ `idea://` links (portable opener)

`idea://open?file=...&line=...` only works if the OS (or a plugin) registered a handler for `idea://`. To get the **same behavior on every machine** without that registration, call the launcher explicitly:

1. In IntelliJ: **Tools → Create Command-line Launcher…** and ensure `idea` is on your `PATH`.
2. Run:

```bash
chmod +x scripts/idea-open-from-url.sh
./scripts/idea-open-from-url.sh 'idea://open?file=/absolute/path/YourFile.java&line=26'
```

Optional: `export IDEA_BIN=/path/to/idea` if the launcher is not named `idea`.

**Optional: make `idea://` open this script (macOS)**  
Create an **Automator** “Application” with **Run Shell Script**, pass input as **arguments**, and run:

`/path/to/scripts/idea-open-from-url.sh "$1"`

Then use **RCDefaultApp** or similar to associate the `idea` URL scheme with that app (or open the Automator app from a bookmark). Details vary by macOS version.

## Security Notes

- **Never commit tokens to version control**
- Tokens expire after 24 hours (default configuration)
- Store tokens securely and never share them
- Use HTTPS in production environments

