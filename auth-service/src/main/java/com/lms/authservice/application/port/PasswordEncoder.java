package com.lms.authservice.application.port;

/**
 * Port for password encoding — keeps BCrypt out of the application layer.
 */
public interface PasswordEncoder {

    String encode(String rawPassword);

    boolean matches(String rawPassword, String encodedPassword);
}
