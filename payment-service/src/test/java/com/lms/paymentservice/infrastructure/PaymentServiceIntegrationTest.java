package com.lms.paymentservice.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.paymentservice.application.dto.request.InitiatePaymentRequest;
import com.lms.paymentservice.domain.model.PaymentMethod;
import com.lms.paymentservice.domain.model.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("integration-test")
class PaymentServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("lms_payments_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Disable config server — tests are self-contained
        registry.add("spring.config.import", () -> "");
        // Disable Redis cache for tests
        registry.add("spring.cache.type", () -> "none");
        // Disable RabbitMQ — we don't want to publish events in unit tests
        registry.add("spring.autoconfigure.exclude",
                () -> "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration");
        // Disable gateway — mock payment gateway will be used
        registry.add("payment.gateway.simulate-failure-rate", () -> "0.0"); // always succeed
        registry.add("payment.gateway.processing-delay-ms",   () -> "0");
    }

    @Autowired private MockMvc       mockMvc;
    @Autowired private ObjectMapper  objectMapper;

    private static final UUID USER_ID   = UUID.randomUUID();
    private static final UUID COURSE_ID = UUID.randomUUID();

    @Test
    @DisplayName("POST /payments/v1 → 201 Created with COMPLETED payment")
    void initiatePayment_returns201() throws Exception {
        InitiatePaymentRequest request = new InitiatePaymentRequest(
                COURSE_ID, new BigDecimal("99.99"), "USD",
                PaymentMethod.CREDIT_CARD, UUID.randomUUID().toString());

        mockMvc.perform(post("/payments/v1")
                        .with(jwt().jwt(jwt -> jwt
                                .subject(USER_ID.toString())
                                .claim("role", "STUDENT")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.courseId").value(COURSE_ID.toString()))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.transactionId").isNotEmpty());
    }

    @Test
    @DisplayName("POST /payments/v1 idempotent — second call with same key returns same payment")
    void initiatePayment_idempotent() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        UUID courseId2 = UUID.randomUUID();

        InitiatePaymentRequest request = new InitiatePaymentRequest(
                courseId2, new BigDecimal("49.99"), "USD",
                PaymentMethod.DEBIT_CARD, idempotencyKey);

        // First call
        String firstResponse = mockMvc.perform(post("/payments/v1")
                        .with(jwt().jwt(jwt -> jwt
                                .subject(USER_ID.toString())
                                .claim("role", "STUDENT")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // Second call with same idempotency key
        String secondResponse = mockMvc.perform(post("/payments/v1")
                        .with(jwt().jwt(jwt -> jwt
                                .subject(USER_ID.toString())
                                .claim("role", "STUDENT")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // Same payment ID returned for both requests
        var first  = objectMapper.readTree(firstResponse);
        var second = objectMapper.readTree(secondResponse);
        org.assertj.core.api.Assertions
                .assertThat(first.get("paymentId").asText())
                .isEqualTo(second.get("paymentId").asText());
    }

    @Test
    @DisplayName("GET /payments/v1/{paymentId} → owner can retrieve own payment")
    void getPayment_ownerCanAccess() throws Exception {
        InitiatePaymentRequest request = new InitiatePaymentRequest(
                UUID.randomUUID(), new BigDecimal("29.99"), "USD",
                PaymentMethod.PAYPAL, UUID.randomUUID().toString());

        String created = mockMvc.perform(post("/payments/v1")
                        .with(jwt().jwt(jwt -> jwt
                                .subject(USER_ID.toString())
                                .claim("role", "STUDENT")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String paymentId = objectMapper.readTree(created).get("paymentId").asText();

        mockMvc.perform(get("/payments/v1/{id}", paymentId)
                        .with(jwt().jwt(jwt -> jwt
                                .subject(USER_ID.toString())
                                .claim("role", "STUDENT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(paymentId));
    }

    @Test
    @DisplayName("GET /payments/v1/{paymentId} → different user gets 403")
    void getPayment_otherUserForbidden() throws Exception {
        InitiatePaymentRequest request = new InitiatePaymentRequest(
                UUID.randomUUID(), new BigDecimal("29.99"), "USD",
                PaymentMethod.CREDIT_CARD, UUID.randomUUID().toString());

        String created = mockMvc.perform(post("/payments/v1")
                        .with(jwt().jwt(jwt -> jwt
                                .subject(USER_ID.toString())
                                .claim("role", "STUDENT")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String paymentId = objectMapper.readTree(created).get("paymentId").asText();
        UUID anotherUser = UUID.randomUUID();

        mockMvc.perform(get("/payments/v1/{id}", paymentId)
                        .with(jwt().jwt(jwt -> jwt
                                .subject(anotherUser.toString())
                                .claim("role", "STUDENT"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /payments/v1/{id}/refund → non-admin gets 403")
    void refund_nonAdminForbidden() throws Exception {
        mockMvc.perform(post("/payments/v1/{id}/refund", UUID.randomUUID())
                        .with(jwt().jwt(jwt -> jwt
                                .subject(USER_ID.toString())
                                .claim("role", "STUDENT"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /payments/v1/admin/payments → non-admin gets 403")
    void adminEndpoint_nonAdminForbidden() throws Exception {
        mockMvc.perform(get("/payments/v1/admin/payments")
                        .with(jwt().jwt(jwt -> jwt
                                .subject(USER_ID.toString())
                                .claim("role", "STUDENT"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /payments/v1 with missing courseId → 422 Unprocessable Entity")
    void initiatePayment_missingCourseId_returns422() throws Exception {
        String invalidBody = """
                {
                  "amount": 49.99,
                  "currency": "USD",
                  "paymentMethod": "CREDIT_CARD"
                }
                """;

        mockMvc.perform(post("/payments/v1")
                        .with(jwt().jwt(jwt -> jwt
                                .subject(USER_ID.toString())
                                .claim("role", "STUDENT")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Validation Error"));
    }
}
