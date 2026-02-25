package com.leanhduc.telegramclone.service.contact;

import com.leanhduc.telegramclone.dto.contact.AddContactRequest;
import com.leanhduc.telegramclone.dto.contact.ContactResponse;
import com.leanhduc.telegramclone.dto.contact.UpdateContactRequest;
import com.leanhduc.telegramclone.exception.BusinessException;
import com.leanhduc.telegramclone.exception.ErrorCode;
import com.leanhduc.telegramclone.mapper.ContactMapper;
import com.leanhduc.telegramclone.model.Contact;
import com.leanhduc.telegramclone.model.ContactId;
import com.leanhduc.telegramclone.model.User;
import com.leanhduc.telegramclone.repository.ContactRepository;
import com.leanhduc.telegramclone.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContactService implements IContactService {
    private final ContactRepository contactRepository;
    private final UserRepository userRepository;
    private final ContactMapper contactMapper;

    @Override
    public void addContact(UUID ownerId, AddContactRequest request) {
        if (ownerId.equals(request.contactId())) {
            throw new BusinessException(ErrorCode.CANNOT_ADD_SELF);
        }

        ContactId contactId = new ContactId(ownerId, request.contactId());
        if (contactRepository.existsById(contactId)) {
            throw new BusinessException(ErrorCode.CONTACT_ALREADY_EXISTS);
        }
        User owner = userRepository.getReferenceById(ownerId);
        User friend = userRepository.findById(request.contactId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Contact newContact = Contact.builder()
                .id(contactId)
                .owner(owner)
                .contact(friend)
                .alias(request.alias())
                .build();

        contactRepository.save(newContact);
    }

    @Override
    public Page<ContactResponse> getContacts(UUID ownerId, Pageable pageable) {
        Page<Contact> contactPage = contactRepository.findByIdOwnerIdAndBlockedFalse(ownerId, pageable);
        return contactPage.map(contactMapper::toResponse);
    }

    @Override
    public ContactResponse getContact(UUID ownerId, UUID contactId) {
        Contact contact = contactRepository.findByIdOwnerIdAndIdContactId(ownerId, contactId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return contactMapper.toResponse(contact);
    }

    @Override
    @Transactional
    public void updateContactStatus(UUID ownerId, UUID contactId, UpdateContactRequest request) {
        Contact contact = contactRepository.findByIdOwnerIdAndIdContactId(ownerId, contactId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (request.alias() != null) {
            contact.setAlias(request.alias());
        }
        if (request.isMuted() != null) {
            contact.setMuted(request.isMuted());
        }
        if (request.isBlocked() != null) {
            contact.setBlocked(request.isBlocked());
        }
        contactRepository.save(contact);
    }

    @Override
    public void deleteContact(UUID ownerId, UUID contactId) {
         Contact contact = contactRepository.findByIdOwnerIdAndIdContactId(ownerId, contactId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        contactRepository.delete(contact);
    }
}
