package ru.mephi.hotelbooking.booking.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import ru.mephi.hotelbooking.booking.exception.ProblemTitles;

/** Пишет в ответ ошибку безопасности в формате RFC 7807 на русском языке (фильтры безопасности вне MVC). */
final class ProblemJsonWriter {

    private ProblemJsonWriter() {
    }

    static void write(HttpServletResponse response, ObjectMapper objectMapper, HttpStatus status, String detail)
            throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(ProblemTitles.titleFor(status.value()));
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
