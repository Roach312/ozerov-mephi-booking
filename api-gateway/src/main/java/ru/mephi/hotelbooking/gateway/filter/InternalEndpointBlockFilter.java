package ru.mephi.hotelbooking.gateway.filter;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;
import ru.mephi.hotelbooking.gateway.error.ProblemTitles;

/**
 * Внутренние эндпойнты сервиса отелей (шаг согласования, который вызывает Booking Service) никогда не публикуются
 * через шлюз: на них приходит 404, как будто маршрута не существует. (Сам сервис отелей дополнительно требует роль
 * INTERNAL, так что это эшелонированная защита.)
 */
@Component
public class InternalEndpointBlockFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(InternalEndpointBlockFilter.class);

    private static final byte[] NOT_FOUND_BODY = ("{\"type\":\"about:blank\",\"title\":\"" + ProblemTitles.titleFor(404)
            + "\",\"status\":404,\"detail\":\"Маршрут для этого пути не найден\"}").getBytes(UTF_8);

    private static final List<PathPattern> BLOCKED = patterns(
            "/api/rooms/*/confirm-availability",
            "/api/rooms/*/confirm-availability/**",
            "/api/rooms/*/release",
            "/api/rooms/*/release/**");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        PathContainer path = exchange.getRequest().getPath().pathWithinApplication();
        if (BLOCKED.stream().anyMatch(pattern -> pattern.matches(path))) {
            log.warn("Заблокирован запрос к внутреннему эндпойнту: {} {}", exchange.getRequest().getMethod(), path.value());
            var response = exchange.getResponse();
            response.setStatusCode(HttpStatus.NOT_FOUND);
            response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
            return response.writeWith(Mono.just(response.bufferFactory().wrap(NOT_FOUND_BODY)));
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private static List<PathPattern> patterns(String... patterns) {
        PathPatternParser parser = PathPatternParser.defaultInstance;
        return Arrays.stream(patterns).map(parser::parse).toList();
    }
}
