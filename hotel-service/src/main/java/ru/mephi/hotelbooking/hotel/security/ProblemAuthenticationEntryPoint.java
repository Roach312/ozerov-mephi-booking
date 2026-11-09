package ru.mephi.hotelbooking.hotel.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;

/** 401: запрос без токена или с недействительным токеном. Заголовок WWW-Authenticate выставляет стандартный обработчик. */
class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final BearerTokenAuthenticationEntryPoint bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();
    private final ObjectMapper objectMapper;

    ProblemAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        bearerEntryPoint.commence(request, response, authException);
        HttpStatus status = HttpStatus.resolve(response.getStatus());
        if (status == null) {
            status = HttpStatus.UNAUTHORIZED;
        }
        String detail = request.getHeader(HttpHeaders.AUTHORIZATION) == null
                ? "Требуется JWT-токен: передайте его в заголовке Authorization: Bearer <токен>"
                : "Токен недействителен или просрочен";
        ProblemJsonWriter.write(response, objectMapper, status, detail);
    }
}
