package ru.mephi.hotelbooking.booking.client;

import java.net.http.HttpClient;
import org.springframework.boot.autoconfigure.web.client.RestClientBuilderConfigurer;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;

@Configuration
public class HotelClientConfig {

    /** Клиентская балансировка через Eureka: "http://hotel-service" разрешается в живой экземпляр сервиса. */
    @Bean
    @LoadBalanced
    RestClient.Builder loadBalancedRestClientBuilder(RestClientBuilderConfigurer configurer) {
        return configurer.configure(RestClient.builder());
    }

    @Bean
    RestClient hotelRestClient(@LoadBalanced RestClient.Builder builder, HotelClientProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getReadTimeout());
        return builder
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    /** Ограниченное число попыток с экспоненциально растущими паузами; повторяются только неопределённые сбои. */
    @Bean
    RetryTemplate hotelRetryTemplate(HotelClientProperties properties) {
        return RetryTemplate.builder()
                .maxAttempts(properties.getMaxAttempts())
                .exponentialBackoff(
                        properties.getInitialBackoff().toMillis(),
                        properties.getBackoffMultiplier(),
                        properties.getMaxBackoff().toMillis())
                .retryOn(HotelCallFailedException.class)
                .build();
    }
}
