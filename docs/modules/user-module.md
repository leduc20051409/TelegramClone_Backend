# User Module

## Responsibilities
- Retrieve current authenticated user's profile (`getCurrentUser`)
- Fetch public profile of another user by ID (`getUserById`)
- Update own profile: display name, bio, avatar (`updateProfile`)
- Search for users by keyword (excluding self) with pagination (`searchUsers`)

## Rules
- Users can only access and modify their own profile (enforced via principal from SecurityContext).
- Profile updates are optional fields; only provided fields are changed.
- Search results exclude the requesting user.
- User accounts cannot be deleted via this module (deletion handled elsewhere if needed).