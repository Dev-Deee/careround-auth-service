package com.dayo.careround.authservice.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
public class JwtProperties {
    @Value("${app.jwt.private-key}")
    private String privateKey;

    @Value("${app.jwt.public-key}")
    private String publicKey;

    @Value("${app.jwt.access-token-expiry-ms}")
    private Long accessTokenExpiryMs;

    @Value("${app.jwt.refresh-token-expiry-ms}")
    private Long refreshTokenExpiryMs;
}
