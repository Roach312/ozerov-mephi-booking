package ru.mephi.hotelbooking.gateway.error;

import java.util.Map;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.reactive.error.DefaultErrorAttributes;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;

/**
 * Ошибки самого шлюза (нет маршрута, сервис недоступен, тайм-аут) возвращаются со стандартным JSON Spring Boot,
 * но поле {@code error} содержит русский текст.
 */
@Component
public class RussianErrorAttributes extends DefaultErrorAttributes {

    @Override
    public Map<String, Object> getErrorAttributes(ServerRequest request, ErrorAttributeOptions options) {
        Map<String, Object> attributes = super.getErrorAttributes(request, options);
        if (attributes.get("status") instanceof Integer status) {
            attributes.put("error", ProblemTitles.titleFor(status));
        }
        return attributes;
    }
}
