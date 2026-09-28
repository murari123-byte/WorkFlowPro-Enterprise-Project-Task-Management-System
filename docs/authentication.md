# Authentication & Authorization

Owned by **auth-service**. Status: Phase 2 / Step 1 — registration, login, JWT access + refresh
tokens, logout, current user. (Profile, user management, orgs/teams come in later steps.)

## How it works

```
1. POST /api/auth/register or /login  ──►  auth-service checks password (BCrypt)
                                      ◄──  { accessToken (JWT, 15 min), refreshToken (random, 7 days) }
2. Every API call:   Authorization: Bearer <accessToken>
                     └─ Spring Security verifies signature + expiry + issuer, reads roles from the token
3. Access token expired → POST /api/auth/refresh { refreshToken }
                     ◄── new accessToken + NEW refreshToken (the old one is now dead)
4. Logout → POST /api/auth/logout { refreshToken }  → refresh token revoked
```

## Access token (JWT)

| Property | Value |
|---|---|
| Format | JWT, signed with **HS256** (HMAC-SHA256) using `JWT_SECRET` |
| Lifetime | `JWT_ACCESS_TOKEN_TTL` (default `15m`) |
| Stored by server? | No — it is stateless and verified by signature only |

Claims:

| Claim | Example | Meaning |
|---|---|---|
| `iss` | `workflowpro-auth` | Issuer; tokens with another issuer are rejected |
| `sub` | `7e7b334d-...` | User id (UUID) |
| `email` | `jane@example.com` | User email |
| `roles` | `["EMPLOYEE"]` | Roles, read from the database when the token is created |
| `iat` / `exp` | epoch seconds | Issued at / expires at |

Spring maps `roles` to authorities with a `ROLE_` prefix (`EMPLOYEE` → `ROLE_EMPLOYEE`), so
`hasRole('ADMIN')` / `@PreAuthorize("hasRole('ADMIN')")` work.

## Refresh token

| Property | Value |
|---|---|
| Format | 32 random bytes, Base64URL (not a JWT) |
| Lifetime | `JWT_REFRESH_TOKEN_TTL` (default `7d`) |
| Storage | Table `refresh_tokens` — **only the SHA-256 hash** is stored |
| Single use | Yes. `/refresh` revokes the used token and returns a new one (rotation) |
| Reuse detection | Presenting an already-revoked token revokes **all** active refresh tokens of that user |

## Passwords

- Hashed with **BCrypt** (strength 10) via Spring Security's `BCryptPasswordEncoder`.
- Length 8–72 characters (BCrypt ignores bytes after 72, so longer input is rejected).
- Plain passwords are never stored or logged. `UserResponse` never contains the hash.

## Security rules

1. **Roles are never taken from the client.** `RegisterRequest` has no role field; extra JSON fields
   (e.g. `"roles":["ADMIN"]`) are ignored. New users always get `EMPLOYEE`.
   Role changes will only be possible through admin-only endpoints (later step).
2. **The user id comes from the verified token** (`sub`), never from a request parameter (`/me`).
3. Login returns the same error for unknown email and wrong password (no account discovery).
4. Emails are stored in lower case; login and uniqueness are case-insensitive.
5. Disabled accounts cannot log in (403) or refresh.
6. Stateless: no HTTP session, no cookies → CSRF protection is disabled on purpose.
7. The service will not start without `JWT_SECRET`, or if it is shorter than 32 characters.

## Public vs protected endpoints (auth-service)

| Public (no token) | Protected (Bearer token required) |
|---|---|
| `POST /api/auth/register`, `/login`, `/refresh`, `/logout` | `GET /api/auth/me` |
| `GET /api/auth/ping`, `/actuator/health`, `/actuator/info` | everything else |
| `GET /swagger-ui.html`, `/v3/api-docs` | |

## Roles

| Role | Id | Intended use |
|---|---|---|
| `ADMIN` | 1 | Full system administration, user management |
| `PROJECT_MANAGER` | 2 | Manages projects and their teams |
| `TEAM_LEAD` | 3 | Leads a team |
| `EMPLOYEE` | 4 | Default role for every new account |

## Known limitations / planned

- **Logout does not kill the access token** — it stays valid until it expires (max 15 min).
  This is the normal trade-off of stateless JWTs; keep the access-token TTL short.
- HS256 uses one shared secret. Other services will need the same `JWT_SECRET` to verify tokens.
  A future improvement is RS256 (auth-service keeps a private key, others only get the public key).
- The refresh token is returned in the JSON body. The React app must store it carefully
  (decided in the frontend step).
