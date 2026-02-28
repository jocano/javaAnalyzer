# Spring Boot MVC Controller with JWT Security

This project demonstrates a Spring Boot application with:
- Spring Web MVC for REST endpoints
- Spring Security with JWT authentication
- Two protected GET endpoints and one public endpoint

## Project Structure

- `SpringMvcControllerApplication.java` - Main Spring Boot application class
- `ApiController.java` - Controller with GET endpoints
- `AuthController.java` - Authentication controller for login
- `SecurityConfig.java` - Spring Security configuration
- `JwtUtil.java` - JWT utility for token generation and validation
- `JwtAuthenticationFilter.java` - JWT authentication filter

## Endpoints

### Public Endpoints (No Authentication Required)
- `GET /api/public/hello` - Returns a public hello message

### Authentication Endpoint
- `POST /api/auth/login` - Login to get JWT token
  - Body: `{"username": "admin", "password": "password"}`

### Protected Endpoints (JWT Token Required)
- `GET /api/protected/user-info` - Returns user information
- `GET /api/protected/data` - Returns sensitive data

## How to Run

### Option 1: Maven (recommended)
From the project root (`spring-mvc-controller/`):

```bash
# Build
mvn clean install

# Run the application
mvn spring-boot:run
```

The app starts on **http://localhost:8080**.

### Option 2: Run the JAR
```bash
mvn clean package
java -jar target/spring-mvc-controller-0.0.1-SNAPSHOT.jar
```

### Option 3: IntelliJ (or other IDE)
- Open the project as a Maven project.
- Main class: `com.example.springmvccontroller.SpringMvcControllerApplication`
- Run that class (e.g. right‑click → Run ‘SpringMvcControllerApplication’).

### Optional: custom port or profile
```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=9090"
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

## Testing the API

### 1. Test Public Endpoint
```bash
curl http://localhost:8080/api/public/hello
```

### 2. Login to Get JWT Token
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "password"}'
```

### 3. Test Protected Endpoints
Use the token from step 2 in the Authorization header:
```bash
curl -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  http://localhost:8080/api/protected/user-info

curl -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  http://localhost:8080/api/protected/data
```

## Default Credentials
- Username: `admin`
- Password: `password`

## Dependencies
- Spring Boot 3.2.0
- Spring Web
- Spring Security
- JWT (jjwt library)
- Maven for dependency management


## Comands to start and test this application:

pkill -f "spring-boot:run" || true
mvn -q -DskipTests spring-boot:run | cat

curl -i http://localhost:8080/api/public/hello

TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"password"}' | jq -r .token)

curl -i -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/protected/user-info


## Database Integration Update

The application now uses an H2 in-memory database for user authentication instead of hardcoded credentials.

### Database Configuration
- **Database**: H2 in-memory database  
- **Console**: Available at http://localhost:8080/h2-console/
- **JDBC URL**: `jdbc:h2:mem:testdb`
- **Username**: `sa`
- **Password**: (empty)

### Pre-loaded Users
The application automatically creates these users on startup:
- **admin/password** (role: ADMIN)
- **user/password** (role: USER)  
- **demo/demo123** (role: USER)

### New Endpoints

#### User Registration
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"newuser","password":"newpass","email":"user@example.com"}'
```

#### User Management (Protected)
```bash
# Get all users (requires valid JWT token)
curl -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  http://localhost:8080/api/users/all

# Get specific user by username
curl -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  http://localhost:8080/api/users/username
```

### Testing All Features
```bash
# 1. Test login with pre-loaded users
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"password"}'

curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"demo","password":"demo123"}'

# 2. Register new user
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"testpass","email":"test@example.com"}'

# 3. Login with new user
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"testpass"}'

# 4. Use token to access protected endpoints
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"password"}' | jq -r .token)

curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/users/all
```

### Database Features
- User entities with username, password, email, role, and enabled status
- Automatic table creation/deletion on application restart
- H2 console for database inspection and management
- Repository and service layers for clean separation of concerns


## https://reqbin.com/req/c-hlt4gkzd/curl-bearer-token-authorization-header-example
