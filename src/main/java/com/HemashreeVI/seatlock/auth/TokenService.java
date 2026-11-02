package com.HemashreeVI.seatlock.auth;

import com.HemashreeVI.seatlock.config.AuthProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtEncoder encoder;
    private final AuthProperties properties;

    public record IssuedToken(String token, Instant expiresAt) {
    }

    public IssuedToken issue(String subject, String name, String email, List<String> roles) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.tokenTtl());
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(JwtConfig.ISSUER)
                .subject(subject)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("name", name)
                .claim("roles", roles);
        if (email != null) {
            claims.claim("email", email);
        }
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()))
                .getTokenValue();
        return new IssuedToken(token, expiresAt);
    }
}
