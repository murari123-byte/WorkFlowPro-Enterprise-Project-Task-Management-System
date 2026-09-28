# Authentication, JWT and Roles (RBAC)

**Authentication** (who are you?) is done by auth-service. **Authorization** (what may you do?) is done by
every service itself, using the roles inside the signed access token plus its own business rules.

## Authentication flow

```
1. Register / Login
   React ── POST /api/auth/login {email, password} ──► gateway ──► auth-service
            auth-service: find user by lower-cased email, BCrypt-check the password,
                          check the account is enabled
   React ◄── { accessToken (JWT, 15 min), refreshToken (random, 7 days), tokenType, expiresIn, user }

2. Every API call
   React ── GET /api/projects   Authorization: Bearer <accessToken> ──► gateway ──► project-service
            project-service verifies the token itself (signature, expiry, issuer) — no call to auth-service

3. Access token expired → 401
   React ── POST /api/auth/refresh {refreshToken} ──► auth-service
            the refresh token is marked used (revoked) and a NEW pair is returned (rotation)
   React repeats the failed request with the new access token

4. Logout
   React ── POST /api/auth/logout {refreshToken} ──► auth-service revokes it; React deletes both tokens
```

The React side (interceptors, where tokens are kept, session restore on reload) is described in
[frontend.md](frontend.md#how-the-frontend-talks-to-the-backend).

## JWT implementation

| Item | Value / class |
|---|---|
| Library | Spring Security OAuth2 Resource Server (Nimbus JOSE) — `spring-boot-starter-security-oauth2-resource-server` |
| Algorithm | **HS256** (HMAC-SHA256) with the shared secret `JWT_SECRET` (min 32 characters, checked at startup) |
| Created by | `auth-service` `JwtService` using a `NimbusJwtEncoder` (`SecurityConfig.jwtEncoder`) |
| Verified by | every service, `common` `JwtVerificationConfig` → `NimbusJwtDecoder` with issuer check |
| Lifetime | `JWT_ACCESS_TOKEN_TTL` (default `15m`) |
| Issuer | `JWT_ISSUER` (default `workflowpro-auth`); tokens with another issuer are rejected |

Claims:

| Claim | Example | Meaning |
|---|---|---|
| `iss` | `workflowpro-auth` | issuer |
| `sub` | `7e7b334d-…` | user id (UUID) |
| `email` | `jane@example.com` | email |
| `roles` | `["EMPLOYEE"]` | roles read from the database when the token was created |
| `iat`, `exp` | epoch seconds | issued at / expires at |

`JwtAuthenticationConverter` maps `roles` to Spring authorities with the `ROLE_` prefix
(`TEAM_LEAD` → `ROLE_TEAM_LEAD`), so `@PreAuthorize("hasRole('ADMIN')")` works. In the service layer the caller
is `AuthenticatedUser.from(jwt)` — id, email and roles **only from the verified token**.

Security configuration (each service's `SecurityConfig`): stateless (no HTTP session), CSRF disabled
(no cookies are used), `oauth2ResourceServer().jwt()`, and `SecurityErrorHandler` writes 401/403 as the same
JSON `ErrorResponse` as every other error. `@EnableMethodSecurity` turns on `@PreAuthorize`.

## Refresh tokens

| Property | Value |
|---|---|
| Format | 32 random bytes (`SecureRandom`), Base64URL — **not** a JWT |
| Lifetime | `JWT_REFRESH_TOKEN_TTL` (default `7d`) |
| Storage | table `refresh_tokens`, **only the SHA-256 hash** (`RefreshTokenService.hash`) |
| Single use | `/refresh` revokes the used token and issues a new pair (rotation) |
| Reuse detection | presenting an already-revoked token revokes **all** active tokens of that user (possible theft) |
| Revoked also when | logout (that token), password change (all), account disabled (all) |

## Passwords

- BCrypt (`BCryptPasswordEncoder`, strength 10). The hash is stored in `users.password_hash`.
- Length 8–72 characters (BCrypt ignores anything after 72 bytes, so longer input is rejected).
- Never logged, never returned: `UserResponse` has no password field.
- Login gives the **same message and does the same work** for "unknown email" and "wrong password"
  (an unknown email is checked against a dummy hash), so neither the answer nor its timing reveals which
  emails have accounts.

## Roles

| Role | Typical person | Given by |
|---|---|---|
| `ADMIN` | system administrator | the bootstrap admin (`BOOTSTRAP_ADMIN_*`), or another ADMIN |
| `PROJECT_MANAGER` | runs projects | an ADMIN |
| `TEAM_LEAD` | leads work inside projects | an ADMIN |
| `EMPLOYEE` | does the tasks | **everyone who registers** |

- A user can have several roles (`user_roles` many-to-many).
- **Roles are never taken from the client.** `RegisterRequest` has no role field; an extra `"roles"` in the
  JSON is ignored (tested). Only `PUT /api/users/{id}/roles` (ADMIN) changes roles.
- New roles appear in the user's **next** access token (after refresh or login).
- An ADMIN cannot remove their own ADMIN role or disable their own account (no lock-out).

### First admin (bootstrap)

On startup `AdminBootstrap` creates an ADMIN from `BOOTSTRAP_ADMIN_EMAIL` / `BOOTSTRAP_ADMIN_PASSWORD`
(+ optional `BOOTSTRAP_ADMIN_FIRST_NAME`, `BOOTSTRAP_ADMIN_LAST_NAME`) **only if no user has that email**.
An existing user is never changed. Leave the variables empty to skip it.

## Permissions (RBAC matrix)

"Manager" = the project's manager. "Member" = a member of that project. "Lead" = ADMIN, the project's
manager, or a TEAM_LEAD who is a member. Checked where it says: **Role** = `@PreAuthorize` on the
controller; **Rule** = service-layer check.

### Users (auth-service)

| Action | Who | Check |
|---|---|---|
| Register, login, refresh, logout | anyone | public |
| View / edit own profile, change own password | any logged-in user | token |
| Search users `GET /api/users` | ADMIN, PROJECT_MANAGER, TEAM_LEAD (only ADMIN sees disabled users) | Role |
| Get user by id / batch | any logged-in user (used by other services) | token |
| Change roles, enable/disable | ADMIN (not on themselves for removing ADMIN / disabling) | Role + Rule |

### Projects (project-service)

| Action | Who | Check |
|---|---|---|
| Create | ADMIN, PROJECT_MANAGER (a PM can only manage their own new project) | Role + Rule |
| List / view / stats | members (ADMIN: all). Non-members get **404** | Rule |
| Edit details, change status, add/remove members, delete | manager or ADMIN | Rule |
| Assign a new manager | ADMIN | Role |
| Edit a COMPLETED/CANCELLED project | nobody (read-only) | Rule |

### Tasks (task-service)

| Action | Who | Check |
|---|---|---|
| View / list / history / stats | members of the task's project (ADMIN: all). Others get **404** | Rule (asks project-service) |
| Create, edit, assign | leads | Rule |
| Change status | leads: any allowed move. Assignee: only between TODO, IN_PROGRESS, IN_REVIEW | Rule (`TaskPermissions`) |
| Delete | manager or ADMIN | Rule |
| Change tasks of an ON_HOLD / COMPLETED / CANCELLED project | nobody | Rule |

The React app hides buttons using the same information (`canManage`, `allowedStatuses`, `permissions`),
but hiding is only for convenience — **every request is checked again on the server**.

## Public vs protected endpoints

| Public (no token) | Protected |
|---|---|
| `POST /api/auth/register`, `/login`, `/refresh`, `/logout` | everything else under `/api/**` |
| `GET /api/auth/ping`, `/api/projects/ping`, `/api/tasks/ping` | |
| `GET /actuator/health` (status only), `/actuator/info` | |
| Swagger: `/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs/**` | |

## Security checklist

| Check | Status |
|---|---|
| Passwords hashed with BCrypt, never returned | ✅ tested (`AuthControllerIntegrationTest`, DB check) |
| JWT signature, expiry and issuer verified by every service | ✅ tampered token → 401 (tested) |
| Roles only from the signed token, never from requests | ✅ `"roles":["ADMIN"]` on register ignored (tested) |
| Protected endpoints return 401 without token | ✅ tested in every service |
| Role checks (403) and data rules (403/404) | ✅ tested (users, projects, tasks) |
| No secrets in code; service refuses to start without `JWT_SECRET` / DB passwords | ✅ verified |
| No stack traces or SQL in error responses | ✅ `GlobalExceptionHandler` returns a generic 500 message |
| Health details hidden from anonymous callers | ✅ `show-details: when-authorized` |
| User enumeration via login answer or timing | ✅ same message, same BCrypt work |
| CORS only for configured origins | ✅ tested (`GatewayRoutingTest`) |

## Known limitations

- Logout does not cancel the access token; it stays valid until it expires (≤ 15 min).
- HS256 uses one shared secret — every service that verifies tokens could also create them. RS256 with a
  public key per service would be stricter (future improvement).
- The refresh token is kept in the browser's `localStorage` (readable by injected scripts). An `HttpOnly`
  cookie would be safer (future improvement).
- Expired and revoked refresh tokens are never deleted from the table (future: scheduled cleanup).
- No rate limiting on login.
