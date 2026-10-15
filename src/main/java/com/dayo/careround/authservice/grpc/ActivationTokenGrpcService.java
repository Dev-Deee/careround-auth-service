package com.dayo.careround.authservice.grpc;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;

@GrpcService
@RequiredArgsConstructor
public class ActivationTokenGrpcService extends ActivationTokenServiceGrpc.ActivationTokenServiceImplBase {

    private final StringRedisTemplate redisTemplate;

    // Where the emailed link points; today auth's own set-password page, later the frontend.
    @Value("${app.activation.base-url}")
    private String activationBaseUrl;

    @Override
    public void getActivationLink(ActivationLinkRequest request, StreamObserver<ActivationLinkResponse> responseObserver) {
        String rawToken = redisTemplate.opsForValue().get("activation-token:" + request.getUserId());

        String activationUrl = rawToken != null
                ? activationBaseUrl + "?token=" + rawToken
                : "";

        ActivationLinkResponse response = ActivationLinkResponse.newBuilder()
                .setActivationUrl(activationUrl)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}