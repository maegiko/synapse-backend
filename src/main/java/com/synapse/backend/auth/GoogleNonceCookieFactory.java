package com.synapse.backend.auth;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Builds the cookie that binds an issued Google sign-in nonce to one browser.
 *
 * <p>Separate from {@link RefreshCookieFactory} because it is a different cookie with a
 * different lifetime and path, but it takes its {@code Secure} and {@code SameSite}
 * attributes from the same {@code auth.refresh-cookie} settings, so the two cookies cannot
 * drift apart across environments. No {@code Domain} attribute is set, which leaves the
 * cookie host-only.</p>
 */
@Component
public class GoogleNonceCookieFactory {

    private static final String COOKIE_NAME = "googleNonce";
    private static final String COOKIE_PATH = "/api";

    private final RefreshCookieProperties refreshCookieProperties;
    private final GoogleAuthProperties googleAuthProperties;

    public GoogleNonceCookieFactory(
        RefreshCookieProperties refreshCookieProperties,
        GoogleAuthProperties googleAuthProperties
    ) {
        this.refreshCookieProperties = refreshCookieProperties;
        this.googleAuthProperties = googleAuthProperties;
    }

    /**
     * Builds the cookie carrying a newly issued nonce.
     *
     * @param nonce the raw nonce, which is also returned in the response body so the frontend can hand it
     *     to Google Identity Services.
     * @return the cookie, which expires with the nonce it carries.
     */
    public ResponseCookie issued(String nonce) {
        return cookie(nonce).maxAge(googleAuthProperties.nonceTtl()).build();
    }

    /**
     * Builds the cookie that removes a used nonce from the client.
     *
     * @return an empty cookie that expires immediately.
     */
    public ResponseCookie cleared() {
        return cookie("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder cookie(String nonce) {
        return ResponseCookie.from(COOKIE_NAME, nonce)
            .httpOnly(true)
            .secure(refreshCookieProperties.secure())
            .sameSite(refreshCookieProperties.sameSite())
            .path(COOKIE_PATH);
    }

}
