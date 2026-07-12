package com.dayo.careround.authservice.services;

import com.dayo.careround.authservice.config.JwtProperties;
import com.dayo.careround.authservice.data.models.enums.AdminPermission;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final RSAPrivateKey rsaPrivateKey;
    private final RSAPublicKey rsaPublicKey;
    private final JwtProperties jwtProperties;

    public String generateAccessToken(
            UUID userId,
            String role,
            String grade,
            UUID assignedWardId,
            Set<AdminPermission> permissions
    ) {
        long now = System.currentTimeMillis();

        List<String> permissionNames = permissions == null
                ? List.of()
                : permissions.stream().map(Enum::name).toList();

        return Jwts.builder()
                .subject(userId.toString())
                .claim("role", role)
                .claim("grade", grade)
                .claim("assignedWardId", assignedWardId != null ? assignedWardId.toString() : null)
                .claim("permissions", permissionNames)
                .claim("jti", UUID.randomUUID().toString())
                .issuedAt(new Date(now))
                .expiration(new Date(now + jwtProperties.getAccessTokenExpiryMs()))
                .signWith(rsaPrivateKey, Jwts.SIG.RS256)
                .header().keyId("careround-key-v1").and()
                .compact();
    }

    public Map<String, Object> getPublicKeyAsJwks() {
        byte[] encoded = rsaPublicKey.getEncoded();
        String base64 = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(encoded);

        return Map.of(
                "keys", new Object[]{
                        Map.of(
                                "kty", "RSA",
                                "use", "sig",
                                "alg", "RS256",
                                "kid", "careround-key-v1",
                                "key", base64
                        )
                }
        );
    }

    public Claims verifyToken(String token) {
        return Jwts.parser()
                .verifyWith(rsaPublicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}