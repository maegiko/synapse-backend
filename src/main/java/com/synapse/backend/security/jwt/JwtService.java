package com.synapse.backend.security.jwt;

import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import com.synapse.backend.user.User;

@Service
public class JwtService {
    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;

    public JwtService(
        JwtEncoder jwtEncoder,
        JwtProperties jwtProperties
    ) {
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
    }

    /**
     * Generates a signed JWT access token for the given user.
     *
     * <p>The token subject is the user's ID. The token also includes the user's
     * email and name, and expires after the configured access token TTL.</p>
     *
     * @param user the authenticated user to create a token for.
     * @return the encoded JWT access token.
     */
    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        JwsHeader headers = JwsHeader.with(MacAlgorithm.HS256).build();

        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(jwtProperties.issuer())
            .issuedAt(now)
            .expiresAt(now.plus(jwtProperties.accessTokenTtl()))
            .subject(user.getId().toString())
            .claim("email", user.getEmail())
            .claim("name", user.getName())
            .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(headers, claims)).getTokenValue();
    }
}
