package ru.mephi.hotelbooking.hotel;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Instant;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** Тестовый помощник: подписывает JWT так же, как это делает Booking Service. */
final class TestTokens {

    static final String SECRET = "test-secret-test-secret-test-secret-0123456789";

    private TestTokens() {
    }

    static String bearer(String subject, String role) {
        return "Bearer " + token(SECRET, subject, role, Instant.now().plusSeconds(3600));
    }

    static String admin() {
        return bearer("admin", "ADMIN");
    }

    static String user() {
        return bearer("alice", "USER");
    }

    static String internal() {
        return bearer("booking-service", "INTERNAL");
    }

    static String token(String secret, String subject, String role, Instant expiresAt) {
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(secret.getBytes(UTF_8), "HmacSHA256")));
        Instant issuedAt = expiresAt.minusSeconds(3600);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(subject)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("roles", List.of(role))
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
