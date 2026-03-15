package com.lms.authservice.infrastructure.security.jwt;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigInteger;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * JWKS (JSON Web Key Set) endpoint.
 *
 * <p>The Spring Cloud Gateway is configured with:
 *   spring.security.oauth2.resourceserver.jwt.jwk-set-uri: http://auth-service:9000/auth/v1/.well-known/jwks.json
 *
 * <p>The gateway fetches this endpoint on startup and caches the public key.
 * It uses the public key to verify the RSA signature on every incoming JWT.
 * The private key NEVER leaves auth-service.
 *
 * <p>Key ID ("kid") is derived from the key's modulus hash — this allows key rotation
 * by serving multiple keys simultaneously during a transition period.
 */
@RestController
@RequestMapping("/auth/v1/.well-known")
@RequiredArgsConstructor
public class JwksController {

    private final JwtTokenService jwtTokenService;

    @GetMapping("/jwks.json")
    public Map<String, Object> jwks() {
        RSAPublicKey publicKey = jwtTokenService.getPublicKey();

        // Encode modulus and exponent as Base64URL (required by RFC 7517)
        String modulus = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(toUnsignedBytes(publicKey.getModulus()));
        String exponent = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(toUnsignedBytes(publicKey.getPublicExponent()));

        Map<String, Object> jwk = Map.of(
                "kty", "RSA",
                "use", "sig",
                "alg", "RS256",
                "kid", deriveKeyId(publicKey),
                "n", modulus,
                "e", exponent
        );

        return Map.of("keys", List.of(jwk));
    }

    private byte[] toUnsignedBytes(BigInteger value) {
        byte[] bytes = value.toByteArray();
        // BigInteger includes a sign byte; strip it for JWKS format
        if (bytes[0] == 0 && bytes.length > 1) {
            byte[] stripped = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, stripped, 0, stripped.length);
            return stripped;
        }
        return bytes;
    }

    private String deriveKeyId(RSAPublicKey publicKey) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(publicKey.getEncoded());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash).substring(0, 16);
        } catch (java.security.NoSuchAlgorithmException e) {
            return "lms-key-1";
        }
    }
}
