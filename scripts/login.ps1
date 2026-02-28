# PowerShell Script to authenticate and get JWT token
# Usage: .\login.ps1 [username] [password] [server_url]

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
Write-Host "Authenticating to: $ServerUrl" -ForegroundColor Cyan
Write-Host "Username: $Username" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Prepare the request body
$body = @{
    username = $Username
    password = $Password
} | ConvertTo-Json

try {
    # Make the POST request
    $response = Invoke-RestMethod -Uri "$ServerUrl/api/auth/login" `
        -Method Post `
        -ContentType "application/json" `
        -Body $body `
        -ErrorAction Stop

    # Check if response contains token
    if ($response.token) {
        Write-Host ""
        Write-Host "========================================" -ForegroundColor Green
        Write-Host "Authentication SUCCESSFUL!" -ForegroundColor Green
        Write-Host "========================================" -ForegroundColor Green
        Write-Host ""
        
        Write-Host "Token: " -NoNewline -ForegroundColor Yellow
        Write-Host $response.token -ForegroundColor White
        Write-Host ""
        Write-Host "Username: " -NoNewline -ForegroundColor Yellow
        Write-Host $response.username -ForegroundColor White
        Write-Host "Role: " -NoNewline -ForegroundColor Yellow
        Write-Host $response.role -ForegroundColor White
        Write-Host ""
        
        Write-Host "Full Response:" -ForegroundColor Cyan
        $response | ConvertTo-Json -Depth 10 | Write-Host
        
        Write-Host ""
        Write-Host "========================================" -ForegroundColor Cyan
        Write-Host "Authorization Header:" -ForegroundColor Cyan
        Write-Host "Authorization: Bearer $($response.token)" -ForegroundColor White
        Write-Host "========================================" -ForegroundColor Cyan
        Write-Host ""
        
        # Ask if user wants to save token to file
        $saveToken = Read-Host "Save token to file? (Y/N)"
        if ($saveToken -eq "Y" -or $saveToken -eq "y") {
            $response.token | Out-File -FilePath "token.txt" -Encoding ASCII
            Write-Host "Token saved to: token.txt" -ForegroundColor Green
        }
        
        # Also set as environment variable for current session
        $env:JWT_TOKEN = $response.token
        Write-Host "Token also set as environment variable: `$env:JWT_TOKEN" -ForegroundColor Green
        
        return $response.token
    } else {
        Write-Host ""
        Write-Host "========================================" -ForegroundColor Red
        Write-Host "Authentication FAILED!" -ForegroundColor Red
        Write-Host "========================================" -ForegroundColor Red
        Write-Host ""
        Write-Host "Response: " -ForegroundColor Yellow
        $response | ConvertTo-Json | Write-Host
        exit 1
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
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $responseBody = $reader.ReadToEnd()
        Write-Host "Response Body: " -ForegroundColor Yellow
        Write-Host $responseBody -ForegroundColor Red
    }
    
    Write-Host ""
    Write-Host "Please check:" -ForegroundColor Yellow
    Write-Host "  1. Server is running at $ServerUrl" -ForegroundColor White
    Write-Host "  2. Username and password are correct" -ForegroundColor White
    Write-Host "  3. Network connectivity" -ForegroundColor White
    
    exit 1
}

