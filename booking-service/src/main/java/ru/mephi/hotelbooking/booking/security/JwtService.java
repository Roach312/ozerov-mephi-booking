package ru.mephi.hotelbooking.booking.security;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Instant;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;
import ru.mephi.hotelbooking.booking.entity.User;

/**
 * Выпускает JWT с подписью HS256. Booking Service - единственный издатель токенов, все сервисы проверяют их
 * одним и тем же секретом.
 */
@Service
public class JwtService {

    public static final String ROLES_CLAIM = "roles";
    public static final String USER_ID_CLAIM = "userId";
    public static final String INTERNAL_ROLE = "INTERNAL";

    private final JwtProperties properties;
    private final JwtEncoder encoder;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        SecretKey key = new SecretKeySpec(properties.getSecret().getBytes(UTF_8), "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    /** Токен пользователя: по умолчанию действует один час. */
    public String generateUserToken(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.getIssuer())
                .subject(user.getUsername())
                .issuedAt(now)
                .expiresAt(now.plus(properties.getExpiration()))
                .claim(USER_ID_CLAIM, user.getId())
                .claim(ROLES_CLAIM, List.of(user.getRole().name()))
                .build();
        return encode(claims);
    }

    /** Короткоживущий токен с ролью INTERNAL для вызовов сервиса отелей. */
    public String generateServiceToken() {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.getIssuer())
                .subject("booking-service")
                .issuedAt(now)
                .expiresAt(now.plus(properties.getServiceTokenTtl()))
                .claim(ROLES_CLAIM, List.of(INTERNAL_ROLE))
                .build();
        return encode(claims);
    }

    public long userTokenTtlSeconds() {
        return properties.getExpiration().toSeconds();
    }

    private String encode(JwtClaimsSet claims) {
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
