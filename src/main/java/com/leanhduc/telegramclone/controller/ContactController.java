package com.leanhduc.telegramclone.controller;

import com.leanhduc.telegramclone.dto.contact.AddContactRequest;
import com.leanhduc.telegramclone.dto.contact.ContactResponse;
import com.leanhduc.telegramclone.service.contact.IContactService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping ("/api/contacts")
@RequiredArgsConstructor
public class ContactController {
    private final IContactService contactService;

    @PostMapping
    public ResponseEntity<Void> addContact(
            Principal principal,
            @RequestBody AddContactRequest request) {
        String userIdFromToken = principal.getName();
        UUID ownerId = UUID.fromString(userIdFromToken);
        contactService.addContact(ownerId, request);
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<Page<ContactResponse>> getContacts(
            Principal principal,
            Pageable pageable) {
        String userIdFromToken = principal.getName();
        UUID ownerId = UUID.fromString(userIdFromToken);
        return ResponseEntity.ok(contactService.getContacts(ownerId, pageable));
    }
}
