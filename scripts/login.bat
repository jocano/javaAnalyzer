@echo off
REM Windows Batch Script to authenticate and get JWT token
REM Usage: login.bat [username] [password] [server_url]

setlocal enabledelayedexpansion

REM Default values
set USERNAME=%1
set PASSWORD=%2
set SERVER_URL=%3

REM If no arguments provided, prompt for input
if "%USERNAME%"=="" (
    set /p USERNAME="Enter username: "
)
if "%PASSWORD%"=="" (
    set /p PASSWORD="Enter password: "
)
if "%SERVER_URL%"=="" (
    set SERVER_URL=http://localhost:8080
)

echo.
echo ========================================
echo Authenticating to: %SERVER_URL%
echo Username: %USERNAME%
echo ========================================
echo.

REM Make the POST request using curl
REM Check if curl is available
where curl >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: curl is not installed or not in PATH
    echo Please install curl: https://curl.se/download.html
    echo Or use PowerShell script instead: login.ps1
    pause
    exit /b 1
)

REM Create temporary JSON file
set TEMP_JSON=%TEMP%\login_request_%RANDOM%.json
echo {"username":"%USERNAME%","password":"%PASSWORD%"} > "%TEMP_JSON%"

REM Make the request and save response
set RESPONSE_FILE=%TEMP%\login_response_%RANDOM%.json
curl -s -X POST "%SERVER_URL%/api/auth/login" ^
    -H "Content-Type: application/json" ^
    -d "@%TEMP_JSON%" ^
    -o "%RESPONSE_FILE%"

REM Check if request was successful
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ERROR: Failed to connect to server
    echo Please check if the server is running at %SERVER_URL%
    del "%TEMP_JSON%" 2>nul
    del "%RESPONSE_FILE%" 2>nul
    pause
    exit /b 1
)

REM Display response
echo Response:
type "%RESPONSE_FILE%"
echo.

REM Check if response contains token (simple check)
findstr /C:"token" "%RESPONSE_FILE%" >nul
if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo Authentication SUCCESSFUL!
    echo ========================================
    echo.
    
    REM Try to extract token using PowerShell (if available)
    REM For Windows 10/11, PowerShell is usually available
    where powershell >nul 2>nul
    if %ERRORLEVEL% EQU 0 (
        echo Extracting token...
        for /f "delims=" %%i in ('powershell -Command "$json = Get-Content '%RESPONSE_FILE%' | ConvertFrom-Json; if ($json.token) { Write-Output $json.token }"') do set TOKEN=%%i
        
        if not "!TOKEN!"=="" (
            echo.
            echo TOKEN: !TOKEN!
            echo.
            echo Save this token for use in API requests:
            echo   Authorization: Bearer !TOKEN!
            echo.
            
            REM Optionally save token to file
            set /p SAVE_TOKEN="Save token to file? (Y/N): "
            if /i "!SAVE_TOKEN!"=="Y" (
                echo !TOKEN! > token.txt
                echo Token saved to: token.txt
            )
        )
    ) else (
        echo.
        echo To extract the token, use PowerShell script: login.ps1
        echo Or use a JSON parser to extract the "token" field from the response above.
    )
) else (
    echo.
    echo ========================================
    echo Authentication FAILED!
    echo ========================================
    echo.
    echo Please check your username and password.
)

REM Cleanup
del "%TEMP_JSON%" 2>nul
del "%RESPONSE_FILE%" 2>nul

echo.
pause

