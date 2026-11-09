package ru.mephi.hotelbooking.hotel.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandler;

/** 403: токен действителен, но роли недостаточно для операции. */
class ProblemAccessDeniedHandler implements AccessDeniedHandler {

    private final BearerTokenAccessDeniedHandler bearerHandler = new BearerTokenAccessDeniedHandler();
    private final ObjectMapper objectMapper;

    ProblemAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        bearerHandler.handle(request, response, accessDeniedException);
        ProblemJsonWriter.write(response, objectMapper, HttpStatus.FORBIDDEN,
                "Недостаточно прав для выполнения этой операции");
    }
}
