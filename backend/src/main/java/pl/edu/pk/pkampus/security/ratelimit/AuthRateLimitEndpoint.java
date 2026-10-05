package pl.edu.pk.pkampus.security.ratelimit;

public enum AuthRateLimitEndpoint {
    LOGIN("/api/v1/auth/login"),
    REGISTER("/api/v1/auth/register"),
    REFRESH("/api/v1/auth/refresh"),
    FORGOT_PASSWORD("/api/v1/auth/forgot-password");

    private final String path;

    AuthRateLimitEndpoint(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
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
