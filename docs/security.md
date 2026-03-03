# Security Documentation

## 1. Authentication Overview

The system uses **JWT (JSON Web Token)** for authentication with an **access token + refresh token** mechanism:

- **Access Token**: short-lived (15 minutes), used to authenticate API requests.
- **Refresh Token**: long-lived (7 days), stored in the database, used to obtain a new access token when the old one expires.
- **Refresh Token Rotation**: each time a refresh token is used, the system issues a new access/refresh token pair and invalidates the old refresh token (enhances security).

## 2. Core Components

### 2.1. `JwtTokenProvider`
- **Purpose**: Create, parse, and validate JWTs.
- **Secret key**: Configured in `application.properties` (`jwt.secretKey`), must be at least 32 characters for HS256.
- **Claims**:
    - `sub`: userId (as UUID string).
    - `email`: user's email.
    - `authorization`: role (e.g., `"ROLE_USER"`).
    - `iat`, `exp`: issued at and expiration times.
- **Main methods**:
    - `generateAccessToken(userId, email, authorities)`
    - `validateToken(token)`
    - `getUserIdFromToken(token)`
    - `getEmail(token)`
    - `getAuthorities(token)`

### 2.2. `JwtAuthenticationFilter` (OncePerRequestFilter)
- **Purpose**: Intercept every request, extract JWT from the `Authorization` header, validate it, and set the authentication into the `SecurityContextHolder`.
- **Processing flow**:
    1. Extract token from header (format `Bearer <token>`).
    2. If token is valid, extract userId and authorities from the token.
    3. Create a `UsernamePasswordAuthenticationToken` with principal = userId, credentials = null, authorities = list of `GrantedAuthority`.
    4. Set the authentication into `SecurityContextHolder`.
    5. If any error occurs (expired token, signature failure, etc.), log the error and **do not set authentication** – the request still proceeds (Spring Security will later return 401 if the endpoint requires authentication).
- **Note**: Do not throw exceptions out of the filter to avoid breaking the filter chain; ensure no critical errors slip through.

### 2.3. `CustomUserDetails` implements `UserDetails`
- **Purpose**: Adapter between the `User` entity and Spring Security.
- Contains: email (username), password hash, role.
- Used by `AuthenticationManager` during login.

### 2.4. `SecurityConfig`
- **Spring Security configuration**:
    - Disable CSRF (stateless JWT).
    - Configure public endpoints: `/api/auth/**`, `/ws/**`, Swagger UI.
    - All other endpoints require authentication.
    - Add `JwtAuthenticationFilter` before `BasicAuthenticationFilter`.
    - Configure CORS (allow credentials, but specify exact frontend origin instead of `*` in production).
    - Stateless session management.
    - Exception handling via `JwtAuthEntryPoint` (returns 401 JSON response).

## 3. Detailed Authentication Flow

### 3.1. Registration (`POST /api/auth/register`)
1. Receive `RegisterRequest` (email, username, password).
2. Check if email or username already exists.
3. Encode password using `BCryptPasswordEncoder`.
4. Save user to database.
5. Call `buildAuthResponse`:
    - Generate access token (via `JwtTokenProvider`).
    - Create refresh token (via `RefreshTokenService`) and store it in the database. The refresh token is a **random UUID** (or a secure random string) and stored hashed for security.
6. Return `AuthResponse` containing access token, refresh token, userId, email.

### 3.2. Login (`POST /api/auth/login`)
1. Receive `LoginRequest` (email, password).
2. `AuthenticationManager` authenticates using `CustomUserDetailsService` (loads user by email) and checks password.
3. On success, retrieve `CustomUserDetails` from the `Authentication` object.
4. Call `buildAuthResponse` (same as registration) to generate token pair.
5. Return `AuthResponse`.

### 3.3. Refresh Token (`POST /api/auth/refresh`)
1. Receive the old refresh token from the request body.
2. Call `RefreshTokenService.rotateRefreshToken(oldToken)`:
    - Validate the token: exists in DB, not expired, not revoked.
    - Delete the old token from DB.
    - Generate a **new UUID-based refresh token**, store it (hashed) in DB with the same user ID and a new expiry.
    - Return `RefreshTokenResponse` containing userId, email, role, and the **new plain-text refresh token**.
3. Use userId, email, and role to generate a new access token.
4. Return `AuthResponse` with the new access token and new refresh token.

### 3.4. Logout (`POST /api/auth/logout-all`)
- Requires a valid JWT.
- Deletes **all refresh tokens** associated with the authenticated user from the database (based on userId from `SecurityContext`).
- Optionally, you can implement an access token blacklist if immediate revocation is needed.

## 4. Authorization Mechanism

- **Role-Based Access Control (RBAC)**.
- Role is stored in the `User` entity (enum: `USER`, `ADMIN`, etc.).
- When creating a JWT, the role is placed in the `authorization` claim.
- In `JwtAuthenticationFilter`, the role is converted to `GrantedAuthority` and attached to the `Authentication`.
- Authorization checks can be done at:
    - **HttpSecurity level**: e.g., `.requestMatchers("/admin/**").hasRole("ADMIN")`.
    - **Method security**: use `@PreAuthorize("hasRole('USER')")` (requires `@EnableGlobalMethodSecurity`).

**Business rule**: Users can only access their own data. This is typically enforced in the service layer by comparing the userId from the token with the resource owner's ID.

## 5. Security Considerations & Best Practices

- **Secret key**: Store in environment variables, never commit to version control.
- **Refresh token storage**:
    - The token stored in DB is a **UUID** (or a cryptographically secure random string) – it is **hashed** (e.g., BCrypt) before saving to prevent leakage.
    - Each refresh token is bound to a specific user and has an expiration.
    - Rotation ensures that a stolen refresh token can be used only once (if the attacker uses it before the legitimate user, the latter will be forced to re-login).
- **CORS**: Specify exact frontend domain(s) instead of `*` when `allowCredentials = true`.
- **Logging**: Avoid logging email addresses or tokens at INFO level; use DEBUG only if necessary.
- **Error handling**: `JwtAuthEntryPoint` returns a consistent 401 JSON response.
- **Token validation**: Access tokens are validated on every request; if expired, the client uses the refresh token to obtain a new pair.

## 6. Possible Extensions

- **Audit logging**: Log important events (login, refresh, logout) for security monitoring.
- **Rate limiting**: Apply to login and refresh endpoints to prevent brute-force attacks.

## 7. Authentication Flow Diagram (Text)

Client] --(1) Login (email/pw)--> [AuthController]
--> (2) AuthenticationManager --> (3) CustomUserDetailsService
--> (4) Generate JWT + RefreshToken (UUID) --> (5) Store refresh token (hashed) in DB
<--(6) Return token pair--

[Client] --(7) API request with JWT--> [JwtAuthenticationFilter]
--> (8) Validate JWT --> (9) Set Authentication in SecurityContext
--> (10) Controller processes request --> (11) Return response

[Client] --(12) Refresh token request--> [AuthController]
--> (13) RefreshTokenService.rotateRefreshToken()
--> (14) Delete old token, create new UUID token in DB
--> (15) Generate new access token
<--(16) Return new token pair--
