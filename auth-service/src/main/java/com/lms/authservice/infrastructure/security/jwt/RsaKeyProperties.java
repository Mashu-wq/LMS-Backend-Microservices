package com.lms.authservice.infrastructure.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RSA key pair configuration.
 * Keys are loaded from environment variables (or k8s secrets in production).
 *
 * <p>In local dev: keys are auto-generated at startup if not provided.
 * In production: inject from secrets manager (AWS Secrets Manager, Vault, k8s Secret).
 *
 * <p>PEM format, Base64-encoded:
 *   RSA_PRIVATE_KEY = Base64(PEM private key)
 *   RSA_PUBLIC_KEY  = Base64(PEM public key)
 */
@ConfigurationProperties(prefix = "jwt.rsa")
public record RsaKeyProperties(
        String privateKey,
        String publicKey
) {}
