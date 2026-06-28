package com.dayo.careround.authservice.services;

import com.dayo.careround.authservice.dto.response.UserCredentialResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class UserServiceClient {

    private final RestClient restClient;

    public UserServiceClient(@Value("${services.user-service-url}") String userServiceUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(userServiceUrl)
                .build();
    }

    public UserCredentialResponse findByIdentifier(String identifier) {
        return restClient.get()
                .uri("/api/v1/users/credentials?identifier={identifier}", identifier)
                .retrieve()
                .body(UserCredentialResponse.class);
    }
}