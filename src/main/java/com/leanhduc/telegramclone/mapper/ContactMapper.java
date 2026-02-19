package com.leanhduc.telegramclone.mapper;


import com.leanhduc.telegramclone.dto.contact.ContactResponse;
import com.leanhduc.telegramclone.model.Contact;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper (componentModel = "spring")
public interface ContactMapper {

    @Mapping(source = "contact.id", target = "contactId")
    @Mapping(source = "contact.username", target = "username")
    @Mapping(source = "contact.displayName", target = "displayName")
    @Mapping(source = "createdAt", target = "addedAt")
    ContactResponse toResponse(Contact contact);
}