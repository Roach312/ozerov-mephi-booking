package ru.mephi.hotelbooking.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Таблица маршрутов. Целевые адреса - логические имена сервисов, разрешаемые через Eureka ({@code lb://});
 * их можно переопределить свойствами {@code gateway.routes.*-uri} (так делают тесты).
 *
 * <pre>
 * /api/bookings/**  -> booking-service, префикс /api/bookings отбрасывается
 *                      (POST /api/bookings/booking -> POST /booking, GET /api/bookings/bookings -> GET /bookings)
 * /api/hotels/**    -> hotel-service, путь без изменений
 * /api/rooms/**     -> hotel-service, путь без изменений; INTERNAL-эндпойнты (confirm-availability, release)
 *                      НЕ публикуются: см. InternalEndpointBlockFilter
 * </pre>
 */
@Configuration
public class GatewayRoutesConfig {

    @Bean
    RouteLocator routes(RouteLocatorBuilder builder,
                        @Value("${gateway.routes.booking-uri:lb://booking-service}") String bookingUri,
                        @Value("${gateway.routes.hotel-uri:lb://hotel-service}") String hotelUri) {
        return builder.routes()
                .route("booking-service", route -> route
                        .path("/api/bookings/**")
                        .filters(filters -> filters.rewritePath("/api/bookings/?(?<segment>.*)", "/${segment}"))
                        .uri(bookingUri))
                .route("hotel-service", route -> route
                        .path("/api/hotels/**", "/api/rooms/**")
                        .uri(hotelUri))
                // OpenAPI-документы сервисов, которые собирает Swagger UI шлюза
                .route("booking-service-docs", route -> route
                        .path("/docs/booking/**")
                        .filters(filters -> filters.rewritePath("/docs/booking/?(?<segment>.*)", "/${segment}"))
                        .uri(bookingUri))
                .route("hotel-service-docs", route -> route
                        .path("/docs/hotel/**")
                        .filters(filters -> filters.rewritePath("/docs/hotel/?(?<segment>.*)", "/${segment}"))
                        .uri(hotelUri))
                .build();
    }
}
