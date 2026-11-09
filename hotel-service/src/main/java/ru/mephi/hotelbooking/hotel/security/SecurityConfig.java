package ru.mephi.hotelbooking.hotel.security;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.fasterxml.jackson.databind.ObjectMapper;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Сервис - OAuth2 Resource Server и сам проверяет JWT (общий секрет HS256).
 * Роли берутся из claim {@code roles}: USER, ADMIN и INTERNAL (вызовы между сервисами).
 * Ответы 401 и 403 возвращаются в формате RFC 7807 на русском языке.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
                                "/actuator/health/**", "/actuator/info", "/error").permitAll()
                        // внутренние эндпойнты шага согласования: только для вызовов между сервисами
                        .requestMatchers(HttpMethod.POST, "/api/rooms/*/confirm-availability", "/api/rooms/*/release")
                        .hasRole("INTERNAL")
                        .requestMatchers(HttpMethod.GET, "/api/rooms/stats").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/rooms/recommend").hasAnyRole("USER", "ADMIN", "INTERNAL")
                        .requestMatchers(HttpMethod.GET, "/api/hotels", "/api/hotels/**", "/api/rooms", "/api/rooms/**")
                        .hasAnyRole("USER", "ADMIN")
                        .requestMatchers("/api/hotels", "/api/hotels/**", "/api/rooms", "/api/rooms/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(new ProblemAuthenticationEntryPoint(objectMapper))
                        .accessDeniedHandler(new ProblemAccessDeniedHandler(objectMapper))
                        .jwt(Customizer.withDefaults()));
        return http.build();
    }

    @Bean
    JwtDecoder jwtDecoder(@Value("${app.jwt.secret}") String secret) {
        SecretKey key = new SecretKeySpec(secret.getBytes(UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    /** Преобразует claim {@code roles} (["USER"], ["ADMIN"], ["INTERNAL"]) в полномочия ROLE_*. */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
