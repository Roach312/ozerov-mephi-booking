package ru.mephi.hotelbooking.gateway.security;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import ru.mephi.hotelbooking.gateway.error.ProblemTitles;

/** 401 в формате RFC 7807 на русском языке: запрос без токена или с недействительным токеном. */
class ProblemAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

    private static final byte[] NO_TOKEN = body("Требуется JWT-токен: передайте его в заголовке Authorization: Bearer <токен>");
    private static final byte[] BAD_TOKEN = body("Токен недействителен или просрочен");

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        ServerHttpResponse response = exchange.getResponse();
        boolean tokenSent = exchange.getRequest().getHeaders().containsKey(HttpHeaders.AUTHORIZATION);
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        byte[] payload = tokenSent ? BAD_TOKEN : NO_TOKEN;
        return response.writeWith(Mono.just(response.bufferFactory().wrap(payload)));
    }

    private static byte[] body(String detail) {
        return ("{\"type\":\"about:blank\",\"title\":\"" + ProblemTitles.titleFor(401)
                + "\",\"status\":401,\"detail\":\"" + detail + "\"}").getBytes(UTF_8);
    }
}
