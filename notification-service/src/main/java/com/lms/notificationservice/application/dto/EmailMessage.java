package com.lms.notificationservice.application.dto;

import java.util.Map;

/**
 * Value object describing an email to send.
 * The {@code templateName} maps to a Thymeleaf template under
 * {@code templates/email/<templateName>.html}.
 */
public record EmailMessage(
        String              to,
        String              subject,
        String              templateName,
        Map<String, Object> variables
) {}
