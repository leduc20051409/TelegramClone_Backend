package com.leanhduc.telegramclone.service.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService implements IEmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Async
    @Override
    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        log.info("Starting asynchronous email delivery for password reset to: {}", toEmail);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());

            String htmlContent = "<div style=\"font-family: Arial, sans-serif; max-width: 600px; margin: auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 8px;\">" +
                    "  <h2 style=\"color: #0088cc; text-align: center;\">Reset Your Password</h2>" +
                    "  <p>Hello,</p>" +
                    "  <p>We received a request to reset your password for your TelegramClone account. Click the button below to choose a new password. This link is valid for 15 minutes:</p>" +
                    "  <div style=\"text-align: center; margin: 30px 0;\">" +
                    "    <a href=\"" + resetLink + "\" style=\"background-color: #0088cc; color: white; padding: 12px 24px; text-decoration: none; border-radius: 5px; font-weight: bold;\">Reset Password</a>" +
                    "  </div>" +
                    "  <p>If the button doesn't work, you can also copy and paste the link below into your browser:</p>" +
                    "  <p style=\"word-break: break-all;\"><a href=\"" + resetLink + "\" style=\"color: #0088cc;\">" + resetLink + "</a></p>" +
                    "  <hr style=\"border: none; border-top: 1px solid #eeeeee; margin: 20px 0;\">" +
                    "  <p style=\"font-size: 12px; color: #888888;\">If you did not request this, you can safely ignore this email.</p>" +
                    "</div>";

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Reset Your Password - TelegramClone");
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Password reset email sent successfully to {}", toEmail);
        } catch (MessagingException e) {
            log.error("Failed to send password reset email to {}: {}", toEmail, e.getMessage());
        }
    }
}
