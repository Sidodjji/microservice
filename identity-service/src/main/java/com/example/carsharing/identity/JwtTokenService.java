package com.example.carsharing.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

@Service
class JwtTokenService {
    private final JwtEncoder jwtEncoder;
    private final Clock clock;
    private final long expirationMinutes;

    @Autowired
    JwtTokenService(JwtEncoder jwtEncoder, @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
        this(jwtEncoder, Clock.systemUTC(), expirationMinutes);
    }

    JwtTokenService(JwtEncoder jwtEncoder, Clock clock, long expirationMinutes) {
        this.jwtEncoder = jwtEncoder;
        this.clock = clock;
        this.expirationMinutes = expirationMinutes;
    }

    TokenResponse issueToken(String email) {
        Instant issuedAt = Instant.now(clock);
        Instant expiresAt = issuedAt.plusSeconds(expirationMinutes * 60);
        JwtClaimsSet claims = JwtClaimsSet.builder().subject(email).issuedAt(issuedAt).expiresAt(expiresAt).build();
        JwsHeader headers = JwsHeader.with(MacAlgorithm.HS256).build();
        return new TokenResponse(jwtEncoder.encode(JwtEncoderParameters.from(headers, claims)).getTokenValue(), "Bearer",
                expirationMinutes * 60);
    }
}
