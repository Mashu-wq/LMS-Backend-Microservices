package com.lms.notificationservice.application.service;

import com.lms.notificationservice.application.dto.EmailMessage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.IContext;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock private JavaMailSender  mailSender;
    @Mock private TemplateEngine  templateEngine;
    @Mock private MimeMessage     mimeMessage;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(mailSender, templateEngine, new SimpleMeterRegistry());
    }

    @Test
    @DisplayName("send: renders template and dispatches MIME message")
    void send_successfulDelivery() throws Exception {
        given(templateEngine.process(anyString(), any(IContext.class)))
                .willReturn("<html><body>Hello John</body></html>");
        given(mailSender.createMimeMessage()).willReturn(mimeMessage);
        given(mimeMessage.getAllRecipients()).willReturn(null);
        doNothing().when(mailSender).send(any(MimeMessage.class));

        emailService.send(new EmailMessage(
                "john@example.com",
                "Welcome!",
                "welcome",
                Map.of("firstName", "John")
        ));

        verify(templateEngine).process(eq("email/welcome"), any(IContext.class));
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("send: mail server error → EmailDeliveryException")
    void send_mailServerError_throwsDeliveryException() throws Exception {
        given(templateEngine.process(anyString(), any(IContext.class)))
                .willReturn("<html>test</html>");
        given(mailSender.createMimeMessage()).willReturn(mimeMessage);
        given(mimeMessage.getAllRecipients()).willReturn(null);
        doThrow(new org.springframework.mail.MailSendException("Connection refused"))
                .when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> emailService.send(new EmailMessage(
                "user@example.com", "Subject", "welcome", Map.of())))
                .isInstanceOf(EmailService.EmailDeliveryException.class)
                .hasMessageContaining("Email delivery failed");
    }
}
