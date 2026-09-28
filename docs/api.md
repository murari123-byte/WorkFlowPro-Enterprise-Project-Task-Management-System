# API Reference

**Base URL for clients (React, curl, Postman): `http://localhost:9080`** — the API Gateway.
Direct service ports (9081–9084) are for debugging only.

Interactive docs (Swagger UI) per service while it runs:

| Service | Swagger UI | OpenAPI JSON |
|---|---|---|
| auth-service | http://localhost:9081/swagger-ui.html | http://localhost:9081/v3/api-docs |

In Swagger UI click **Authorize** and paste an access token (without `Bearer `) to call protected endpoints.
Turn Swagger off with `SWAGGER_ENABLED=false`.

## Error format (all endpoints)

```json
{
  "timestamp": "2026-09-28T11:35:25.634Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/auth/register",
  "fieldErrors": { "email": "must be a well-formed email address" }
}
```
`fieldErrors` only appears on validation errors. Unexpected errors return 500 with a generic
message; details are only in the server log.

---

## Auth service — `/api/auth`

### `POST /api/auth/register` — public
Creates an account with role `EMPLOYEE` and logs it in.

Request:
```json
{ "email": "jane@example.com", "password": "Secret123!", "firstName": "Jane", "lastName": "Doe" }
```
| Field | Rules |
|---|---|
| `email` | required, valid email, max 255, case-insensitive unique |
| `password` | required, 8–72 characters |
| `firstName`, `lastName` | required, max 100 |

Response `201 Created`:
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "q2Vb3...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "id": "7e7b334d-2878-4197-b934-5b3bcc9102f1",
    "email": "jane@example.com",
    "firstName": "Jane",
    "lastName": "Doe",
    "roles": ["EMPLOYEE"],
    "enabled": true,
    "createdAt": "2026-09-28T11:35:25.382712Z"
  }
}
```
Errors: `400` validation, `409` email already registered.

### `POST /api/auth/login` — public
Request: `{ "email": "jane@example.com", "password": "Secret123!" }`
Response `200 OK`: same body as register.
Errors: `400` validation, `401` invalid email or password, `403` account disabled.

### `POST /api/auth/refresh` — public
Request: `{ "refreshToken": "q2Vb3..." }`
Response `200 OK`: same body as register, with a **new** refresh token. The old one no longer works.
Errors: `400` validation, `401` token unknown / expired / already used.

### `POST /api/auth/logout` — public
Request: `{ "refreshToken": "q2Vb3..." }`
Response `204 No Content` (also for unknown tokens). Discard the access token on the client.

### `GET /api/auth/me` — Bearer token
Response `200 OK`: the `user` object shown above.
Errors: `401` missing / invalid / expired token.

### `GET /api/auth/ping` — public
Response `200 OK`: `{"service":"auth-service","status":"UP","timestamp":"..."}`

## curl examples

```bash
curl -s -X POST localhost:9081/api/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"jane@example.com","password":"Secret123!","firstName":"Jane","lastName":"Doe"}'

TOKEN=$(curl -s -X POST localhost:9081/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"jane@example.com","password":"Secret123!"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["accessToken"])')

curl -s localhost:9081/api/auth/me -H "Authorization: Bearer $TOKEN"
```
