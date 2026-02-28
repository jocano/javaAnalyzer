# Form-Based Authentication Scripts

These scripts authenticate via a form-based flow with redirect:
1. **GET** `/authenticate` - Receives a 302 redirect
2. **Follow Redirect** - Gets the actual form page
3. **Parse Form** - Extracts form action, method, and CSRF token
4. **POST** - Submits credentials through the form
5. **Extract Token** - Gets the JWT token from the response

## Available Scripts

### 1. **login-form.html** - Browser-based (Recommended)
   - **Platform**: Any (Windows, Mac, Linux)
   - **Requirements**: Web browser
   - **Usage**: 
     - Open `login-form.html` in your web browser
     - Enter server URL, username, and password
     - Click "Authenticate & Get Token"
     - Automatically handles form fetching, CSRF tokens, and token extraction

### 2. **login-form.ps1** - PowerShell Script (Windows)
   - **Platform**: Windows (PowerShell 5.1+)
   - **Requirements**: PowerShell
   - **Usage**:
     ```powershell
     .\login-form.ps1
     .\login-form.ps1 admin password
     .\login-form.ps1 admin password http://localhost:8080
     ```
   - Features:
     - Fetches form from `/authenticate`
     - Extracts CSRF tokens automatically
     - Handles redirects
     - Extracts token from response

### 3. **login-form.js** - Node.js Script (Cross-platform)
   - **Platform**: Windows, Mac, Linux
   - **Requirements**: Node.js
   - **Usage**:
     ```bash
     node login-form.js
     node login-form.js admin password
     node login-form.js admin password http://localhost:8080
     ```
   - Features:
     - Parses HTML form
     - Extracts CSRF tokens
     - Handles form submission
     - Extracts token from response

## How It Works

### Step 1: Request /authenticate (Gets Redirect)
```bash
GET http://localhost:8080/authenticate
```

The server returns a **302 Redirect** to the actual form page:
```
HTTP/1.1 302 Found
Location: http://localhost:8080/login/form
```

### Step 2: Follow Redirect (Get Form Page)
```bash
GET http://localhost:8080/login/form
```

The redirected page returns an HTML form like:
```html
<form action="/authenticate" method="POST">
    <input type="hidden" name="_csrf" value="abc123...">
    <input type="text" name="username">
    <input type="password" name="password">
    <button type="submit">Login</button>
</form>
```

### Step 3: Submit Credentials
```bash
POST http://localhost:8080/login/form  (or form action URL)
Content-Type: application/x-www-form-urlencoded

username=admin&password=admin123&_csrf=abc123...
```

### Step 4: Extract Token
The server responds with the JWT token (in JSON, HTML, or redirect).

## Example Usage

### PowerShell:
```powershell
PS> .\login-form.ps1 admin admin123

========================================
Step 1: Fetching authentication form...
Server: http://localhost:8080
========================================

✓ Form fetched successfully
Status Code: 200
Form Action: http://localhost:8080/authenticate
CSRF Token found: _csrf

========================================
Step 2: Submitting credentials...
Username: admin
========================================

✓ Form submitted, following redirect...

========================================
Step 3: Extracting token...
========================================

========================================
Authentication SUCCESSFUL!
========================================

Token: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

### Node.js:
```bash
$ node login-form.js admin admin123

========================================
Step 1: Fetching authentication form...
Server: http://localhost:8080
========================================

✓ Form fetched successfully (Status: 200)
Form Action: http://localhost:8080/authenticate
Form Method: POST
Found CSRF token: _csrf

========================================
Step 2: Submitting credentials...
Username: admin
========================================

✓ Form submitted (Status: 302)
Following redirect to: http://localhost:8080/dashboard

========================================
Step 3: Extracting token...
========================================

========================================
Authentication SUCCESSFUL!
========================================

Token: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

## Troubleshooting

### Error: "Failed to fetch form"
- Ensure the server is running
- Check that `/authenticate` endpoint exists
- Verify the server URL is correct

### Error: "No form found in the response"
- The `/authenticate` endpoint should return HTML with a `<form>` element
- Check the actual response content

### Error: "CSRF token not found"
- Some applications don't use CSRF tokens
- The script will still work, just without the CSRF token

### Error: "Could not extract token automatically"
- The token might be in a different format or location
- Check the response body for the token
- Token might be in cookies or response headers
- Token might require parsing JSON or HTML

## Token Extraction

The scripts try multiple methods to extract the token:

1. **JSON Response**: `{"token": "..."}`
2. **HTML with token field**: `<input name="token" value="...">`
3. **Bearer token in response**: `Bearer abc123...`
4. **Cookies**: Token stored in session cookie
5. **Response headers**: Token in custom header

If automatic extraction fails, the script will show the response content for manual inspection.

