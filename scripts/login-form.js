/**
 * Node.js Script to authenticate via form and get JWT token
 * 
 * Usage:
 *   node login-form.js [username] [password] [server_url]
 * 
 * This script:
 * 1. GETs the /authenticate endpoint to fetch the form
 * 2. Parses the HTML form to extract action, CSRF token, etc.
 * 3. Submits credentials via POST
 * 4. Extracts the JWT token from the response
 */

const readline = require('readline');
const http = require('http');
const https = require('https');
const { URL } = require('url');

// Simple HTML parser to extract form data
function parseForm(html, baseUrl) {
    const form = {
        action: '/authenticate',
        method: 'POST',
        fields: {}
    };
    
    // Extract form action
    const actionMatch = html.match(/<form[^>]*action=["']([^"']+)["']/i);
    if (actionMatch) {
        form.action = actionMatch[1];
        if (!form.action.startsWith('http')) {
            const base = new URL(baseUrl);
            if (form.action.startsWith('/')) {
                form.action = `${base.origin}${form.action}`;
            } else {
                form.action = `${base.origin}/${form.action}`;
            }
        }
    }
    
    // Extract form method
    const methodMatch = html.match(/<form[^>]*method=["']([^"']+)["']/i);
    if (methodMatch) {
        form.method = methodMatch[1].toUpperCase();
    }
    
    // Extract CSRF token
    const csrfMatch = html.match(/<input[^>]*name=["'](_csrf|csrfToken|csrf)["'][^>]*value=["']([^"']+)["']/i);
    if (csrfMatch) {
        form.fields[csrfMatch[1]] = csrfMatch[2];
        console.log(`Found CSRF token: ${csrfMatch[1]}`);
    }
    
    return form;
}

// Helper function to make HTTP request
function makeRequest(url, options, data, cookies = '') {
    return new Promise((resolve, reject) => {
        const urlObj = new URL(url);
        const isHttps = urlObj.protocol === 'https:';
        const httpModule = isHttps ? https : http;
        
        const headers = options.headers || {};
        if (cookies) {
            headers['Cookie'] = cookies;
        }
        
        const requestOptions = {
            hostname: urlObj.hostname,
            port: urlObj.port || (isHttps ? 443 : 80),
            path: urlObj.pathname + urlObj.search,
            method: options.method || 'GET',
            headers: headers,
            maxRedirects: options.maxRedirects !== undefined ? options.maxRedirects : 5
        };
        
        const req = httpModule.request(requestOptions, (res) => {
            let body = '';
            let cookies = res.headers['set-cookie'] || [];
            
            res.on('data', (chunk) => {
                body += chunk;
            });
            
            res.on('end', () => {
                const cookieHeader = cookies.map(c => c.split(';')[0]).join('; ');
                
                resolve({
                    status: res.statusCode,
                    statusText: res.statusMessage,
                    headers: res.headers,
                    body: body,
                    cookies: cookieHeader,
                    location: res.headers['location']
                });
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

// Extract token from response
function extractToken(responseBody) {
    // Try JSON first
    try {
        const json = JSON.parse(responseBody);
        if (json.token) {
            return json.token;
        }
    } catch (e) {
        // Not JSON
    }
    
    // Try regex patterns
    const patterns = [
        /"token"\s*:\s*"([^"]+)"/,
        /token["']?\s*[:=]\s*["']([^"']+)["']/,
        /Bearer\s+([A-Za-z0-9\-_\.]+)/,
        /<input[^>]*name=["']token["'][^>]*value=["']([^"']+)["']/
    ];
    
    for (const pattern of patterns) {
        const match = responseBody.match(pattern);
        if (match) {
            return match[1];
        }
    }
    
    return null;
}

// Main authentication function
async function authenticate() {
    const args = process.argv.slice(2);
    let username = args[0] || process.env.USERNAME || '';
    let password = args[1] || process.env.PASSWORD || '';
    let serverUrl = args[2] || process.env.SERVER_URL || 'http://localhost:8080';
    
    // Prompt for credentials if not provided
    if (!username) {
        username = await prompt('Enter username: ');
    }
    if (!password) {
        console.log('Note: Password will be visible as you type');
        password = await prompt('Enter password: ');
    }
    
    console.log('');
    console.log('========================================');
    console.log('Step 1: Requesting /authenticate...');
    console.log(`Server: ${serverUrl}`);
    console.log('========================================');
    console.log('');
    
    try {
        // Step 1: GET /authenticate (expects 302 redirect)
        const authenticateUrl = `${serverUrl}/authenticate`;
        let formResponse = await makeRequest(authenticateUrl, {
            method: 'GET',
            headers: {
                'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8'
            }
        }, null, '');
        
        // Check if we got a redirect (302/301)
        if (formResponse.status >= 300 && formResponse.status < 400) {
            console.log(`✓ Received redirect (${formResponse.status}) - following...`);
            
            if (!formResponse.location) {
                throw new Error('Redirect received but no Location header found');
            }
            
            let redirectLocation = formResponse.location;
            
            // Make redirect location absolute if relative
            if (!redirectLocation.startsWith('http')) {
                const baseUrl = serverUrl.endsWith('/') ? serverUrl.slice(0, -1) : serverUrl;
                redirectLocation = redirectLocation.startsWith('/')
                    ? `${baseUrl}${redirectLocation}`
                    : `${baseUrl}/${redirectLocation}`;
            }
            
            console.log(`Redirect Location: ${redirectLocation}`);
            console.log('');
            console.log('========================================');
            console.log('Step 2: Fetching form from redirect...');
            console.log('========================================');
            console.log('');
            
            // Step 2: Follow redirect to get the actual form
            formResponse = await makeRequest(redirectLocation, {
                method: 'GET',
                headers: {
                    'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
                    'Cookie': formResponse.cookies
                }
            }, null, formResponse.cookies);
            
            console.log(`✓ Form page fetched successfully (Status: ${formResponse.status})`);
            console.log(`Form URL: ${redirectLocation}`);
        } else if (formResponse.status === 200) {
            console.log(`✓ Form fetched directly (no redirect) (Status: ${formResponse.status})`);
        } else {
            throw new Error(`Unexpected response: ${formResponse.status}`);
        }
        
        // Parse the form
        const form = parseForm(formResponse.body, serverUrl);
        console.log(`Form Action: ${form.action}`);
        console.log(`Form Method: ${form.method}`);
        console.log('');
        
        // Step 3: Prepare form data
        console.log('========================================');
        console.log('Step 3: Submitting credentials...');
        console.log(`Username: ${username}`);
        console.log('========================================');
        console.log('');
        
        const formData = new URLSearchParams();
        formData.append('username', username);
        formData.append('password', password);
        
        // Add CSRF token if found
        for (const [key, value] of Object.entries(form.fields)) {
            formData.append(key, value);
        }
        
        // Submit the form (use cookies from form response)
        const loginResponse = await makeRequest(form.action, {
            method: form.method,
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
                'Content-Length': Buffer.byteLength(formData.toString())
            }
        }, formData.toString(), formResponse.cookies);
        
        console.log(`✓ Form submitted (Status: ${loginResponse.status})`);
        
        // Follow redirect if present
        let finalResponse = loginResponse;
        if (loginResponse.status >= 300 && loginResponse.status < 400 && loginResponse.location) {
            console.log(`Following redirect to: ${loginResponse.location}`);
            finalResponse = await makeRequest(loginResponse.location, {
                method: 'GET',
                headers: {
                    'Cookie': loginResponse.cookies || formResponse.cookies
                }
            }, null, loginResponse.cookies || formResponse.cookies);
        }
        
        // Step 4: Extract token
        console.log('');
        console.log('========================================');
        console.log('Step 4: Extracting token...');
        console.log('========================================');
        console.log('');
        
        const token = extractToken(finalResponse.body);
        
        if (token) {
            console.log('========================================');
            console.log('Authentication SUCCESSFUL!');
            console.log('========================================');
            console.log('');
            console.log('Token:', token);
            console.log('');
            console.log('========================================');
            console.log('Authorization Header:');
            console.log(`Authorization: Bearer ${token}`);
            console.log('========================================');
            console.log('');
            
            // Save token if requested
            const fs = require('fs');
            const saveToken = await prompt('Save token to file? (Y/N): ');
            if (saveToken.toUpperCase() === 'Y') {
                fs.writeFileSync('token.txt', token, 'utf8');
                console.log('Token saved to: token.txt');
            }
            
            return token;
        } else {
            console.log('========================================');
            console.log('Warning: Could not extract token automatically');
            console.log('========================================');
            console.log('');
            console.log('Response Body (first 500 chars):');
            console.log(finalResponse.body.substring(0, Math.min(500, finalResponse.body.length)));
            console.log('');
            console.log('Please check the response above for the token.');
            return null;
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
        console.log(`  1. Server is running at ${serverUrl}`);
        console.log('  2. /authenticate endpoint exists and returns a form');
        console.log('  3. Username and password are correct');
        process.exit(1);
    }
}

// Run the authentication
authenticate().catch(console.error);

