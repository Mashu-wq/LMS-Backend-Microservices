package com.lms.notificationservice.application.service;

import com.lms.notificationservice.application.dto.EmailMessage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Core email sending service.
 *
 * <p>Renders a Thymeleaf HTML template, wraps it in a MIME message, and
 * dispatches via JavaMailSender. All email types funnel through here,
 * keeping delivery logic in one place.
 *
 * <p>Failures propagate to the caller (event consumer), which then decides
 * whether to nack-and-requeue (transient) or nack-to-DLQ (permanent).
 */
@Slf4j
@Service
public class EmailService {

    private static final String TEMPLATE_PREFIX = "email/";

    private final JavaMailSender  mailSender;
    private final TemplateEngine  templateEngine;

    @Value("${notification.email.from:noreply@lms-platform.com}")
    private String fromAddress;

    @Value("${notification.email.from-name:LMS Platform}")
    private String fromName;

    private final Counter emailsSentCounter;
    private final Counter emailsFailedCounter;

    public EmailService(JavaMailSender mailSender,
                        TemplateEngine templateEngine,
                        MeterRegistry meterRegistry) {
        this.mailSender     = mailSender;
        this.templateEngine = templateEngine;

        this.emailsSentCounter = Counter.builder("notification.emails.sent")
                .description("Total emails dispatched successfully")
                .register(meterRegistry);
        this.emailsFailedCounter = Counter.builder("notification.emails.failed")
                .description("Total emails that failed to send")
                .register(meterRegistry);
    }

    /**
     * Renders the template and sends the email.
     *
     * @throws EmailDeliveryException if the mail server rejects the message
     */
    public void send(EmailMessage message) {
        log.info("Sending '{}' email to {}", message.templateName(), message.to());

        String html = renderTemplate(message.templateName(), message.variables());

        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(fromAddress, fromName);
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            helper.setText(html, true);   // true = isHtml

            mailSender.send(mime);
            emailsSentCounter.increment();
            log.info("Email '{}' sent successfully to {}", message.templateName(), message.to());

        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            emailsFailedCounter.increment();
            log.error("Failed to send '{}' email to {}: {}",
                    message.templateName(), message.to(), e.getMessage(), e);
            throw new EmailDeliveryException("Email delivery failed: " + e.getMessage(), e);
        }
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private String renderTemplate(String templateName, java.util.Map<String, Object> variables) {
        Context ctx = new Context();
        if (variables != null) {
            ctx.setVariables(variables);
        }
        return templateEngine.process(TEMPLATE_PREFIX + templateName, ctx);
    }

    // ── Exception ────────────────────────────────────────────────────────────

    public static class EmailDeliveryException extends RuntimeException {
        public EmailDeliveryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
