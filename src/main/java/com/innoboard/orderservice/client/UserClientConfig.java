package com.innoboard.orderservice.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class UserClientConfig {

    public static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    @Bean
    public RestClient userRestClient(
            @Value("${app.services.user-url}") String userUrl,
            @Value("${app.internal.token}") String internalToken,
            @Value("${app.services.connect-timeout}") Duration connectTimeout,
            @Value("${app.services.read-timeout}") Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder()
                .baseUrl(userUrl)
                .defaultHeader(INTERNAL_TOKEN_HEADER, internalToken)
                .requestFactory(requestFactory)
                .build();
    }
}
