# Contact Module

## Responsibilities
- Manage contact lists between users (`ContactController`, `ContactService`).
- Add target user to contacts (`POST /api/contacts/{contactId}`).
- List owner's contacts with search and pagination (`GET /api/contacts`).
- Update contact customization: alias, mute notifications, and block status (`PUT /api/contacts/{contactId}`).
- Remove contact relationship (`DELETE /api/contacts/{contactId}`).

## Key Rules & Constraints
1. **Self-Addition Guard:** Users cannot add themselves as a contact.
2. **Composite Primary Key:** Relationship is uniquely identified by `(owner_id, contact_id)` in the `contacts` table.
3. **Mute & Block Logic:**
   - `is_muted`: Mutes notification alerts for messages received from this contact.
   - `is_blocked`: Blocks communication from the contact and automatically filters them out from standard contact lists.
4. **Authorization:** Contact entries can only be created, modified, or deleted by their respective `owner_id`.