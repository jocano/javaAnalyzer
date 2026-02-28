# REST API Endpoints Documentation

## Spring Boot Application: SpringMvcControllerApplication

Base URL: `http://localhost:8080`

---

## 1. ApiController
**Base Path:** `/api`

### Public Endpoints (No Authentication Required)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| GET | `/api/public/hello` | Returns a public hello message with timestamp | None | JSON with message and timestamp |

### Protected Endpoints (JWT Token Required)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| GET | `/api/protected/user-info` | Returns authenticated user information | None | JSON with user info, username, timestamp, and authorities |
| GET | `/api/protected/data` | Returns sensitive data for authenticated users | None | JSON with data, user, timestamp, and status |

---

## 2. AuthController
**Base Path:** `/api/auth`

### Public Endpoints (No Authentication Required)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| POST | `/api/auth/login` | Authenticates user and returns JWT token | `{"username": "string", "password": "string"}` | JSON with token, username, and role (or error) |
| POST | `/api/auth/register` | Registers a new user | `{"username": "string", "password": "string", "email": "string"}` | JSON with success message and username (or error) |

---

## 3. UserController
**Base Path:** `/api/users`

### Public Endpoints (No Authentication Required)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| GET | `/api/users/all` | Returns list of all users (passwords hidden) | None | JSON array of User objects |

### Protected Endpoints (JWT Token Required)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| GET | `/api/users/{username}` | Returns user by username (password hidden) | None | JSON User object or 404 |

---

## 4. SimpleController
**Base Path:** `/api`

### Public Endpoints (No Authentication Required)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| POST | `/api/wrap-aes` | Wraps AES key with RSA-OAEP encryption | `{"clientSpki": "base64string"}` | JSON with wrappedKeyB64 and status |

---

## Summary by Security Level

### Public Endpoints (No Authentication)
- `GET /api/public/hello`
- `GET /api/users/all`
- `POST /api/auth/login`
- `POST /api/auth/register`
- `POST /api/wrap-aes`
- `GET /`, `/index.html`, `/*.html`, `/*.js`, `/*.css`, `/*.ico` (Static files)
- `GET /h2-console/**` (H2 Database Console)

### Protected Endpoints (Require JWT Token)
- `GET /api/protected/user-info`
- `GET /api/protected/data`
- `GET /api/users/{username}`

---

## Authentication

Protected endpoints require a JWT token in the Authorization header:
```
Authorization: Bearer <JWT_TOKEN>
```

To get a JWT token, use the login endpoint:
```bash
POST /api/auth/login
Body: {"username": "admin", "password": "admin123"}
```

---

## Default Users (Created on Application Start)

1. **admin** / admin123 (ADMIN role)
2. **user** / password (USER role)
3. **demo** / demo123 (USER role)
4. **test** / test123 (USER role)

---

## Example Requests

### Public Hello
```bash
curl http://localhost:8080/api/public/hello
```

### Login
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "admin123"}'
```

### Get All Users
```bash
curl http://localhost:8080/api/users/all
```

### Protected Endpoint
```bash
curl -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  http://localhost:8080/api/protected/user-info
```

### Wrap AES Key
```bash
curl -X POST http://localhost:8080/api/wrap-aes \
  -H "Content-Type: application/json" \
  -d '{"clientSpki": "BASE64_ENCODED_PUBLIC_KEY"}'
```

---

Generated for: SpringMvcControllerApplication

