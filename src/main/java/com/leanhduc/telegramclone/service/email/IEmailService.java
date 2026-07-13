package com.leanhduc.telegramclone.service.email;

public interface IEmailService {
    void sendPasswordResetEmail(String toEmail, String resetLink);
}
