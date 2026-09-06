package com.synapse.backend.auth;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Issues and consumes the one-time nonce a Google sign-in has to carry.
 *
 * <p>The nonce is what stops an ID token minted for one browser being replayed from
 * another: the frontend asks for one, hands it to Google Identity Services, and Google
 * copies it into the {@code nonce} claim of the ID token it signs. Synapse only accepts a
 * credential whose claim matches a nonce it issued and has not seen used.</p>
 *
 * <p>Outstanding nonces live in a bounded Caffeine cache that expires each entry after
 * {@code auth.google.nonce-ttl}, following the rate limit counters rather than adding a
 * table for a value that is worthless a few minutes after it is issued. Like those
 * counters they are per instance and lost on restart, which costs a signing-in user one
 * retry.</p>
 */
@Service
public class GoogleNonceService {

    private static final int NONCE_BYTES = 32;
    private static final int MAX_OUTSTANDING_NONCES = 100000;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final Cache<String, Instant> nonces;

    public GoogleNonceService(GoogleAuthProperties googleAuthProperties) {
        this.nonces = Caffeine.newBuilder()
            .maximumSize(MAX_OUTSTANDING_NONCES)
            .expireAfterWrite(googleAuthProperties.nonceTtl())
            .build();
    }

    /**
     * Issues a nonce for one sign-in attempt.
     *
     * @return the raw nonce, which the caller returns to the frontend and binds to the browser with a cookie.
     */
    public String issueNonce() {
        byte[] nonceBytes = new byte[NONCE_BYTES];

        SECURE_RANDOM.nextBytes(nonceBytes);

        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes);

        nonces.put(nonce, Instant.now());

        return nonce;
    }

    /**
     * Consumes a nonce, so the same one can never authenticate twice.
     *
     * <p>Removal is atomic, so two requests presenting one nonce race for a single cache
     * entry and only the first is allowed to continue.</p>
     *
     * @param nonce the nonce the client presented, or null if its cookie was absent.
     * @return true if the nonce was outstanding and has now been used up.
     */
    public boolean consumeNonce(String nonce) {
        return nonce != null && nonces.asMap().remove(nonce) != null;
    }

}
