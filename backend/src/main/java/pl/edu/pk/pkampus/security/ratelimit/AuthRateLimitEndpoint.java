package pl.edu.pk.pkampus.security.ratelimit;

import lombok.Getter;

public enum AuthRateLimitEndpoint {
    LOGIN("/api/v1/auth/login"),
    REGISTER("/api/v1/auth/register"),
    REFRESH("/api/v1/auth/refresh"),
    FORGOT_PASSWORD("/api/v1/auth/forgot-password");

    @Getter
    private final String path;

    AuthRateLimitEndpoint(String path) {
        this.path = path;
    }

    public static AuthRateLimitEndpoint fromRequest(String method, String requestUri) {
        if (!"POST".equalsIgnoreCase(method) || requestUri == null) {
            return null;
        }
        for (AuthRateLimitEndpoint endpoint : values()) {
            if (endpoint.path.equals(requestUri)) {
                return endpoint;
            }
        }
        return null;
    }
}
