package com.lms.authservice.infrastructure.security.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class DeviceInfoExtractor {

    public String extractIp(HttpServletRequest request) {

        String forwarded = request.getHeader("X-Forwarded-For");

        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0];
        }

        return request.getRemoteAddr();
    }

    public String extractUserAgent(HttpServletRequest request) {
        return request.getHeader("User-Agent");
    }

    public String extractDeviceName(HttpServletRequest request) {

        String agent = request.getHeader("User-Agent");

        if (agent == null) return "Unknown Device";

        if (agent.contains("Chrome")) return "Chrome Browser";
        if (agent.contains("Firefox")) return "Firefox Browser";
        if (agent.contains("Mobile")) return "Mobile Device";

        return "Unknown Device";
    }
}