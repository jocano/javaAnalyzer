# PowerShell Script to authenticate via form and get JWT token
# Usage: .\login-form.ps1 [username] [password] [server_url]

param(
    [string]$Username = "",
    [string]$Password = "",
    [string]$ServerUrl = "http://localhost:8080"
)

# If username/password not provided, prompt for them
if ([string]::IsNullOrEmpty($Username)) {
    $Username = Read-Host "Enter username"
}

if ([string]::IsNullOrEmpty($Password)) {
    $SecurePassword = Read-Host "Enter password" -AsSecureString
    $BSTR = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecurePassword)
    $Password = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($BSTR)
}

    Write-Host ""
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host "Step 1: Requesting /authenticate..." -ForegroundColor Cyan
    Write-Host "Server: $ServerUrl" -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host ""

    # Step 1: GET /authenticate (expects 302 redirect)
    try {
        $initialResponse = $null
        try {
            $initialResponse = Invoke-WebRequest -Uri "$ServerUrl/authenticate" `
                -Method Get `
                -SessionVariable session `
                -UseBasicParsing `
                -MaximumRedirection 0 `
                -ErrorAction Stop
        } catch {
            # Expecting a redirect (302), so this is normal
            if ($_.Exception.Response.StatusCode -eq 302 -or $_.Exception.Response.StatusCode -eq 301) {
                Write-Host "✓ Received redirect (302/301) - following..." -ForegroundColor Green
                $redirectLocation = $_.Exception.Response.Headers["Location"]
                
                if (-not $redirectLocation) {
                    throw "Redirect received but no Location header found"
                }
                
                # Make redirect location absolute if relative
                if (-not $redirectLocation.StartsWith("http")) {
                    $baseUrl = $ServerUrl.TrimEnd('/')
                    if ($redirectLocation.StartsWith("/")) {
                        $redirectLocation = "$baseUrl$redirectLocation"
                    } else {
                        $redirectLocation = "$baseUrl/$redirectLocation"
                    }
                }
                
                Write-Host "Redirect Location: $redirectLocation" -ForegroundColor Gray
                Write-Host ""
                Write-Host "========================================" -ForegroundColor Cyan
                Write-Host "Step 2: Fetching form from redirect..." -ForegroundColor Cyan
                Write-Host "========================================" -ForegroundColor Cyan
                Write-Host ""
                
                # Step 2: Follow redirect to get the actual form
                $formResponse = Invoke-WebRequest -Uri $redirectLocation `
                    -WebSession $session `
                    -UseBasicParsing `
                    -ErrorAction Stop
                
                Write-Host "✓ Form page fetched successfully" -ForegroundColor Green
                Write-Host "Status Code: $($formResponse.StatusCode)" -ForegroundColor Gray
                Write-Host "Form URL: $redirectLocation" -ForegroundColor Gray
                Write-Host ""
            } else {
                throw $_
            }
        }
        
        # If we didn't get a redirect, the initial response contains the form
        if (-not $formResponse) {
            if ($initialResponse.StatusCode -eq 200) {
                $formResponse = $initialResponse
                Write-Host "✓ Form fetched directly (no redirect)" -ForegroundColor Green
                Write-Host "Status Code: $($formResponse.StatusCode)" -ForegroundColor Gray
                Write-Host ""
            } else {
                throw "Unexpected response: $($initialResponse.StatusCode)"
            }
        }
        
        # Parse HTML to find form action, method, and CSRF token
        $htmlContent = $formResponse.Content
    
    # Extract form action (default to /authenticate if not found)
    $formAction = "/authenticate"
    if ($htmlContent -match 'form[^>]*action=["\']([^"\']+)["\']') {
        $formAction = $matches[1]
        if (-not $formAction.StartsWith("http")) {
            # Relative URL - make it absolute
            $baseUrl = $ServerUrl.TrimEnd('/')
            if ($formAction.StartsWith("/")) {
                $formAction = "$baseUrl$formAction"
            } else {
                $formAction = "$baseUrl/$formAction"
            }
        }
    } elseif ($htmlContent -match 'form[^>]*action=([^\s>]+)') {
        $formAction = $matches[1]
    }
    
    Write-Host "Form Action: $formAction" -ForegroundColor Gray
    
    # Extract CSRF token if present
    $csrfToken = $null
    $csrfTokenName = $null
    
    if ($htmlContent -match 'name=["\'](_csrf|csrfToken|csrf)["\']\s+value=["\']([^"\']+)["\']') {
        $csrfTokenName = $matches[1]
        $csrfToken = $matches[2]
        Write-Host "CSRF Token found: $csrfTokenName" -ForegroundColor Gray
    } elseif ($htmlContent -match '<input[^>]*name=["\'](_csrf|csrfToken|csrf)["\'][^>]*value=["\']([^"\']+)["\']') {
        $csrfTokenName = $matches[1]
        $csrfToken = $matches[2]
        Write-Host "CSRF Token found: $csrfTokenName" -ForegroundColor Gray
    }
    
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host "Step 3: Submitting credentials..." -ForegroundColor Cyan
    Write-Host "Username: $Username" -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host ""
    
    # Step 2: Prepare form data
    $formData = @{
        username = $Username
        password = $Password
    }
    
    # Add CSRF token if found
    if ($csrfToken) {
        $formData[$csrfTokenName] = $csrfToken
    }
    
    # Submit the form
    try {
        $loginResponse = Invoke-WebRequest -Uri $formAction `
            -WebSession $session `
            -Method Post `
            -Body $formData `
            -ContentType "application/x-www-form-urlencoded" `
            -UseBasicParsing `
            -MaximumRedirection 0 `
            -ErrorAction SilentlyContinue
        
        # If we get here, there was no redirect (might be success)
        $responseContent = $loginResponse.Content
        
    } catch {
        # Redirect is expected (301/302) - check the response
        if ($_.Exception.Response.StatusCode -eq 302 -or $_.Exception.Response.StatusCode -eq 301) {
            Write-Host "✓ Form submitted, following redirect..." -ForegroundColor Green
            
            # Get the redirect location
            $redirectLocation = $_.Exception.Response.Headers["Location"]
            if ($redirectLocation) {
                if (-not $redirectLocation.StartsWith("http")) {
                    $baseUrl = $ServerUrl.TrimEnd('/')
                    $redirectLocation = "$baseUrl$redirectLocation"
                }
                
                Write-Host "Redirect Location: $redirectLocation" -ForegroundColor Gray
                
                # Follow redirect to get the token
                $finalResponse = Invoke-WebRequest -Uri $redirectLocation `
                    -WebSession $session `
                    -UseBasicParsing `
                    -ErrorAction Stop
                
                $responseContent = $finalResponse.Content
            } else {
                # No redirect location, check cookies for token
                $responseContent = $_.Exception.Response.Content
            }
        } else {
            throw $_
        }
    }
    
    # Step 3: Extract token from response
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host "Step 4: Extracting token..." -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host ""
    
    # Try to parse as JSON first
    $token = $null
    try {
        $jsonResponse = $responseContent | ConvertFrom-Json
        if ($jsonResponse.token) {
            $token = $jsonResponse.token
        }
    } catch {
        # Not JSON, try to extract from HTML or other formats
        # Look for token in various formats
        if ($responseContent -match '"token"\s*:\s*"([^"]+)"') {
            $token = $matches[1]
        } elseif ($responseContent -match 'token["\']?\s*[:=]\s*["\']([^"\']+)["\']') {
            $token = $matches[1]
        } elseif ($responseContent -match 'Bearer\s+([A-Za-z0-9\-_\.]+)') {
            $token = $matches[1]
        }
        
        # Check cookies for token
        if (-not $token -and $session.Cookies) {
            $tokenCookie = $session.Cookies.GetCookies($formAction) | Where-Object { $_.Name -like "*token*" -or $_.Name -like "*jwt*" -or $_.Name -like "*auth*" }
            if ($tokenCookie) {
                $token = $tokenCookie.Value
            }
        }
    }
    
    if ($token) {
        Write-Host "========================================" -ForegroundColor Green
        Write-Host "Authentication SUCCESSFUL!" -ForegroundColor Green
        Write-Host "========================================" -ForegroundColor Green
        Write-Host ""
        Write-Host "Token: " -NoNewline -ForegroundColor Yellow
        Write-Host $token -ForegroundColor White
        Write-Host ""
        
        # Save token to file if requested
        $saveToken = Read-Host "Save token to file? (Y/N)"
        if ($saveToken -eq "Y" -or $saveToken -eq "y") {
            $token | Out-File -FilePath "token.txt" -Encoding ASCII
            Write-Host "Token saved to: token.txt" -ForegroundColor Green
        }
        
        # Set environment variable
        $env:JWT_TOKEN = $token
        Write-Host "Token set as environment variable: `$env:JWT_TOKEN" -ForegroundColor Green
        
        return $token
    } else {
        Write-Host "========================================" -ForegroundColor Yellow
        Write-Host "Warning: Could not extract token automatically" -ForegroundColor Yellow
        Write-Host "========================================" -ForegroundColor Yellow
        Write-Host ""
        Write-Host "Response Content (first 500 chars):" -ForegroundColor Cyan
        Write-Host $responseContent.Substring(0, [Math]::Min(500, $responseContent.Length)) -ForegroundColor White
        Write-Host ""
        Write-Host "Please check the response above for the token." -ForegroundColor Yellow
        Write-Host "Or check cookies/session data." -ForegroundColor Yellow
        
        return $null
    }
    
} catch {
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Red
    Write-Host "ERROR: Authentication failed" -ForegroundColor Red
    Write-Host "========================================" -ForegroundColor Red
    Write-Host ""
    Write-Host "Error Message: " -ForegroundColor Yellow
    Write-Host $_.Exception.Message -ForegroundColor Red
    Write-Host ""
    
    if ($_.Exception.Response) {
        Write-Host "Status Code: $($_.Exception.Response.StatusCode)" -ForegroundColor Yellow
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $responseBody = $reader.ReadToEnd()
        Write-Host "Response Body: " -ForegroundColor Yellow
        Write-Host $responseBody -ForegroundColor Red
    }
    
    Write-Host ""
    Write-Host "Please check:" -ForegroundColor Yellow
    Write-Host "  1. Server is running at $ServerUrl" -ForegroundColor White
    Write-Host "  2. /authenticate endpoint exists and returns a form" -ForegroundColor White
    Write-Host "  3. Username and password are correct" -ForegroundColor White
    
    exit 1
}

