package com.lms.authservice.domain.model;

/**
 * Role is a first-class domain concept — not just a Spring Security authority.
 * Kept as a pure enum so the domain layer has zero framework coupling.
 * Infrastructure maps this to Spring's GrantedAuthority.
 */
public enum Role {
    ADMIN,
    INSTRUCTOR,
    STUDENT
}
