# Auth Module

## Responsibilities
- User Registration (`/api/auth/register`)
- User Authentication / Login (`/api/auth/login`)
- Token Refresh with Rotation (`/api/auth/refresh`)
- Logout and Refresh Token Revocation (`/api/auth/logout`)
- Forgot Password Request & Password Reset via Email OTP Token (`/api/auth/forgot-password`, `/api/auth/reset-password`)

## Key Components
- `AuthService`: Coordinates authentication workflows, password hashing, and user credential validation.
- `RefreshTokenService`: Manages refresh token generation, expiration tracking, database persistence, and revocation flags.
- `EmailService`: Dispatches OTP password reset emails asynchronously.
- `JwtUtils` / `JwtAuthenticationFilter`: Generates and extracts JWT Bearer access tokens for authenticated requests.

## Security & Architectural Rules
1. **Uniqueness Constraints:** Email, username, and phone numbers must be unique across all users.
2. **Password Security:** All user passwords must be hashed using BCrypt (`PasswordEncoder`) before storage. Plaintext passwords must never be logged or stored.
3. **JWT Access Tokens:** Short-lived JWTs (passed via `Authorization: Bearer <token>` header) carry user identity (`sub` claim containing User UUID) and roles (`role` claim).
4. **Refresh Token Lifecycle:**
   - Refresh tokens are stored in the `refresh_tokens` database table.
   - Tokens have an expiration timestamp and a `revoked` status flag.
   - Upon calling `/api/auth/logout`, the active refresh token is marked as `revoked = true` or deleted.
   - Token rotation is enforced during `/api/auth/refresh` to prevent replay attacks.
5. **Password Reset Flow:**
   - `/api/auth/forgot-password`: Generates a temporary `PasswordResetToken` linked to the user's email with a TTL (e.g. 15 minutes) and sends the reset link/token via email.
   - `/api/auth/reset-password`: Validates the token's existence and expiration before updating the user's BCrypt password hash and invalidating the token.