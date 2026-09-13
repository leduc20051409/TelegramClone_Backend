package com.leanhduc.telegramclone.service.contact;

import com.leanhduc.telegramclone.dto.contact.ContactResponse;
import com.leanhduc.telegramclone.mapper.ContactMapper;
import com.leanhduc.telegramclone.model.Contact;
import com.leanhduc.telegramclone.model.ContactId;
import com.leanhduc.telegramclone.model.User;
import com.leanhduc.telegramclone.repository.ContactRepository;
import com.leanhduc.telegramclone.repository.MediaRepository;
import com.leanhduc.telegramclone.repository.UserRepository;
import com.leanhduc.telegramclone.service.Presence.IPresenceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContactServiceBlockedTest {

    @Mock
    private ContactRepository contactRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ContactMapper contactMapper;
    @Mock
    private IPresenceService presenceService;
    @Mock
    private MediaRepository mediaRepository;

    @InjectMocks
    private ContactService contactService;

    @Test
    @DisplayName("Should retrieve only blocked contacts")
    void getBlockedContacts_shouldReturnBlockedContactsPage() {
        UUID ownerId = UUID.randomUUID();
        UUID blockedUserId = UUID.randomUUID();

        User owner = User.builder().id(ownerId).build();
        User blockedUser = User.builder().id(blockedUserId).username("blocked_user").displayName("Blocked User").build();

        Contact blockedContact = Contact.builder()
                .id(new ContactId(ownerId, blockedUserId))
                .owner(owner)
                .contact(blockedUser)
                .blocked(true)
                .build();

        PageRequest pageRequest = PageRequest.of(0, 10);
        when(contactRepository.findByIdOwnerIdAndBlockedTrue(ownerId, pageRequest))
                .thenReturn(new PageImpl<>(List.of(blockedContact)));

        ContactResponse mockResponse = new ContactResponse(
                blockedUserId, blockedUserId, "blocked_user", "Blocked User", null,
                Instant.now(), null, false, null
        );
        when(contactMapper.toResponse(blockedContact)).thenReturn(mockResponse);
        when(presenceService.isUserOnline(blockedUserId)).thenReturn(false);

        Page<ContactResponse> result = contactService.getBlockedContacts(ownerId, pageRequest);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("blocked_user", result.getContent().get(0).username());
    }
}
