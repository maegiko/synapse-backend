package com.synapse.backend.auth;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.synapse.backend.security.jwt.JwtProperties;

/**
 * Builds the refresh token cookie.
 *
 * <p>Login, refresh, logout, password change, and a confirmed registration link
 * all set or clear the same cookie, so its name, path, and attributes are
 * defined once here instead of in each controller that answers with one.</p>
 */
@Component
public class RefreshCookieFactory {

    private static final String COOKIE_NAME = "refreshToken";
    private static final String COOKIE_PATH = "/api/auth";

    private final RefreshCookieProperties refreshCookieProperties;
    private final JwtProperties jwtProperties;

    public RefreshCookieFactory(RefreshCookieProperties refreshCookieProperties, JwtProperties jwtProperties) {
        this.refreshCookieProperties = refreshCookieProperties;
        this.jwtProperties = jwtProperties;
    }

    /**
     * Builds the cookie carrying a newly issued refresh token.
     *
     * @param refreshToken the raw refresh token to store on the client.
     * @return the cookie, which lives for the configured refresh token lifetime.
     */
    public ResponseCookie issued(String refreshToken) {
        return cookie(refreshToken).maxAge(jwtProperties.refreshTokenTtl()).build();
    }

    /**
     * Builds the cookie that removes a refresh token from the client.
     *
     * @return an empty cookie that expires immediately.
     */
    public ResponseCookie cleared() {
        return cookie("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder cookie(String refreshToken) {
        return ResponseCookie.from(COOKIE_NAME, refreshToken)
            .httpOnly(true)
            .secure(refreshCookieProperties.secure())
            .sameSite(refreshCookieProperties.sameSite())
            .path(COOKIE_PATH);
    }

}
