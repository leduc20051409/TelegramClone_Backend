# Contact Module

## Responsibilities
- Add a new contact for the owner (`addContact`)
- List all contacts (excluding blocked contacts) with pagination (`getContacts`)
- Get a specific contact's details (`getContact`)
- Update contact status: alias, mute, block (`updateContactStatus`)
- Remove a contact from the list (`deleteContact`)

## Rules
- Cannot add yourself as a contact.
- Contact relationship must be unique (owner + contact composite key).
- Adding a contact requires the target user to exist.
- When blocking a contact, they are automatically excluded from contact lists.
- Only the owner can modify or delete their own contacts.