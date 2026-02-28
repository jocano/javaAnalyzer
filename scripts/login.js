/**
 * JavaScript/Node.js Script to authenticate and get JWT token
 * 
 * Usage:
 *   node login.js [username] [password] [server_url]
 * 
 * Or with environment variables:
 *   USERNAME=admin PASSWORD=password SERVER_URL=http://localhost:8080 node login.js
 * 
 * Requirements:
 *   - Node.js installed
 *   - No additional dependencies needed (uses built-in fetch or node-fetch)
 */

const readline = require('readline');
const https = require('https');
const http = require('http');

// Parse command line arguments
const args = process.argv.slice(2);
let username = args[0] || process.env.USERNAME || '';
let password = args[1] || process.env.PASSWORD || '';
let serverUrl = args[2] || process.env.SERVER_URL || 'http://localhost:8080';

// Helper function to make HTTP request (Node.js < 18 compatibility)
function makeRequest(url, options, data) {
    return new Promise((resolve, reject) => {
        const urlObj = new URL(url);
        const isHttps = urlObj.protocol === 'https:';
        const httpModule = isHttps ? https : http;
        
        const requestOptions = {
            hostname: urlObj.hostname,
            port: urlObj.port || (isHttps ? 443 : 80),
            path: urlObj.pathname + urlObj.search,
            method: options.method || 'GET',
            headers: options.headers || {}
        };
        
        const req = httpModule.request(requestOptions, (res) => {
            let body = '';
            
            res.on('data', (chunk) => {
                body += chunk;
            });
            
            res.on('end', () => {
                try {
                    const jsonBody = JSON.parse(body);
                    resolve({
                        status: res.statusCode,
                        statusText: res.statusMessage,
                        headers: res.headers,
                        body: jsonBody,
                        rawBody: body
                    });
                } catch (e) {
                    resolve({
                        status: res.statusCode,
                        statusText: res.statusMessage,
                        headers: res.headers,
                        body: body,
                        rawBody: body
                    });
                }
            });
        });
        
        req.on('error', (error) => {
            reject(error);
        });
        
        if (data) {
            req.write(data);
        }
        
        req.end();
    });
}

// Helper function to prompt for input
function prompt(question) {
    const rl = readline.createInterface({
        input: process.stdin,
        output: process.stdout
    });
    
    return new Promise((resolve) => {
        rl.question(question, (answer) => {
            rl.close();
            resolve(answer);
        });
    });
}

// Main authentication function
async function authenticate() {
    // Prompt for username if not provided
    if (!username) {
        username = await prompt('Enter username: ');
    }
    
    // Prompt for password if not provided
    if (!password) {
        const readline = require('readline');
        const rl = readline.createInterface({
            input: process.stdin,
            output: process.stdout
        });
        
        // On Windows, we can't easily hide password input without additional packages
        // For security, show a warning
        console.log('Note: Password will be visible as you type (for security, use environment variable PASSWORD)');
        password = await prompt('Enter password: ');
    }
    
    console.log('');
    console.log('========================================');
    console.log(`Authenticating to: ${serverUrl}`);
    console.log(`Username: ${username}`);
    console.log('========================================');
    console.log('');
    
    // Prepare request body
    const requestBody = JSON.stringify({
        username: username,
        password: password
    });
    
    const url = `${serverUrl}/api/auth/login`;
    
    try {
        // Try to use fetch if available (Node.js 18+)
        let response;
        if (typeof fetch !== 'undefined') {
            response = await fetch(url, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: requestBody
            });
            
            const data = await response.json();
            
            if (response.ok && data.token) {
                console.log('');
                console.log('========================================');
                console.log('Authentication SUCCESSFUL!');
                console.log('========================================');
                console.log('');
                console.log('Token:', data.token);
                console.log('Username:', data.username);
                console.log('Role:', data.role);
                console.log('');
                console.log('Full Response:');
                console.log(JSON.stringify(data, null, 2));
                console.log('');
                console.log('========================================');
                console.log('Authorization Header:');
                console.log(`Authorization: Bearer ${data.token}`);
                console.log('========================================');
                console.log('');
                
                // Save token to file if requested
                const fs = require('fs');
                const saveToken = await prompt('Save token to file? (Y/N): ');
                if (saveToken.toUpperCase() === 'Y') {
                    fs.writeFileSync('token.txt', data.token, 'utf8');
                    console.log('Token saved to: token.txt');
                }
                
                return data.token;
            } else {
                console.log('');
                console.log('========================================');
                console.log('Authentication FAILED!');
                console.log('========================================');
                console.log('');
                console.log('Response:', JSON.stringify(data, null, 2));
                process.exit(1);
            }
        } else {
            // Use built-in http/https module
            response = await makeRequest(url, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Content-Length': Buffer.byteLength(requestBody)
                }
            }, requestBody);
            
            if (response.status === 200 && response.body.token) {
                console.log('');
                console.log('========================================');
                console.log('Authentication SUCCESSFUL!');
                console.log('========================================');
                console.log('');
                console.log('Token:', response.body.token);
                console.log('Username:', response.body.username);
                console.log('Role:', response.body.role);
                console.log('');
                console.log('Full Response:');
                console.log(JSON.stringify(response.body, null, 2));
                console.log('');
                console.log('========================================');
                console.log('Authorization Header:');
                console.log(`Authorization: Bearer ${response.body.token}`);
                console.log('========================================');
                console.log('');
                
                // Save token to file if requested
                const fs = require('fs');
                const saveToken = await prompt('Save token to file? (Y/N): ');
                if (saveToken.toUpperCase() === 'Y') {
                    fs.writeFileSync('token.txt', response.body.token, 'utf8');
                    console.log('Token saved to: token.txt');
                }
                
                return response.body.token;
            } else {
                console.log('');
                console.log('========================================');
                console.log('Authentication FAILED!');
                console.log('========================================');
                console.log('');
                console.log('Status:', response.status, response.statusText);
                console.log('Response:', JSON.stringify(response.body, null, 2));
                process.exit(1);
            }
        }
    } catch (error) {
        console.log('');
        console.log('========================================');
        console.log('ERROR: Authentication failed');
        console.log('========================================');
        console.log('');
        console.log('Error Message:', error.message);
        console.log('');
        console.log('Please check:');
        console.log('  1. Server is running at', serverUrl);
        console.log('  2. Username and password are correct');
        console.log('  3. Network connectivity');
        process.exit(1);
    }
}

// Run the authentication
authenticate().catch(console.error);

