package com.lms.notificationservice.infrastructure.messaging;

import com.lms.notificationservice.application.dto.EmailMessage;
import com.lms.notificationservice.application.service.EmailService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserRegisteredEventConsumerTest {

    @Mock private EmailService emailService;
    @Mock private Channel      channel;

    @InjectMocks
    private UserRegisteredEventConsumer consumer;

    @Test
    @DisplayName("handleUserRegistered: sends welcome email and acks")
    void handleUserRegistered_sendsEmailAndAcks() throws Exception {
        var event = new UserRegisteredEventConsumer.UserRegisteredEvent(
                UUID.randomUUID(), UUID.randomUUID(),
                "jane@example.com", "Jane", "Doe", "STUDENT", Instant.now());

        consumer.handleUserRegistered(event, channel, 1L);

        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailService).send(captor.capture());
        assertThat(captor.getValue().to()).isEqualTo("jane@example.com");
        assertThat(captor.getValue().templateName()).isEqualTo("welcome");
        verify(channel).basicAck(1L, false);
    }

    @Test
    @DisplayName("handleUserRegistered: SMTP failure → nack with requeue=true")
    void handleUserRegistered_smtpFailure_nackRequeue() throws Exception {
        var event = new UserRegisteredEventConsumer.UserRegisteredEvent(
                UUID.randomUUID(), UUID.randomUUID(),
                "error@example.com", "Error", "User", "STUDENT", Instant.now());

        doThrow(new EmailService.EmailDeliveryException("SMTP down", new RuntimeException()))
                .when(emailService).send(any());

        consumer.handleUserRegistered(event, channel, 2L);

        verify(channel).basicNack(2L, false, true);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    @DisplayName("handleUserRegistered: permanent error → nack to DLQ (requeue=false)")
    void handleUserRegistered_permanentError_nackToDlq() throws Exception {
        var event = new UserRegisteredEventConsumer.UserRegisteredEvent(
                UUID.randomUUID(), UUID.randomUUID(),
                "bad@example.com", null, null, "STUDENT", Instant.now()); // null name → NPE in template

        doThrow(new RuntimeException("Template NPE"))
                .when(emailService).send(any());

        consumer.handleUserRegistered(event, channel, 3L);

        verify(channel).basicNack(3L, false, false);
    }
}
