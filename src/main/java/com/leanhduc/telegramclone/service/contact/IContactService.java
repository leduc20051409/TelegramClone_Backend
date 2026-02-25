package com.leanhduc.telegramclone.service.contact;

import com.leanhduc.telegramclone.dto.contact.AddContactRequest;
import com.leanhduc.telegramclone.dto.contact.ContactResponse;
import com.leanhduc.telegramclone.dto.contact.UpdateContactRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface IContactService {
    void addContact(UUID ownerId, AddContactRequest request);

    Page<ContactResponse> getContacts(UUID ownerId, Pageable pageable);

    ContactResponse getContact(UUID ownerId, UUID contactId);

    void updateContactStatus(UUID ownerId, UUID contactId, UpdateContactRequest request);

    void deleteContact(UUID ownerId, UUID contactId);
}
