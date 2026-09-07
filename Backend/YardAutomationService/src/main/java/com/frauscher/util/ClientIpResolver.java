package com.frauscher.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the originating client IP for a request, accounting for the
 * nginx reverse proxy used in production (see Frontend/nginx.conf, which
 * sets X-Forwarded-For/X-Real-IP on every proxied request). Falls back to
 * the raw socket address when no proxy headers are present, which is the
 * case in local dev where the backend is called directly.
 */
public final class ClientIpResolver {

    private static final String[] HEADER_CANDIDATES = { "X-Forwarded-For", "X-Real-IP" };

    private ClientIpResolver() {
    }

    public static String resolve(HttpServletRequest request) {
        for (String header : HEADER_CANDIDATES) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank()) {
                return value.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
