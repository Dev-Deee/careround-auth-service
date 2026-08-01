package com.dayo.careround.authservice.grpc;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.data.redis.core.StringRedisTemplate;

@GrpcService
@RequiredArgsConstructor
public class ActivationTokenGrpcService extends ActivationTokenServiceGrpc.ActivationTokenServiceImplBase {

    private final StringRedisTemplate redisTemplate;

    @Override
    public void getActivationLink(ActivationLinkRequest request, StreamObserver<ActivationLinkResponse> responseObserver) {
        String rawToken = redisTemplate.opsForValue().get("activation-token:" + request.getUserId());

        String activationUrl = rawToken != null
                ? "http://localhost:8081/api/v1/auth/activate-account?token=" + rawToken
                : "";

        ActivationLinkResponse response = ActivationLinkResponse.newBuilder()
                .setActivationUrl(activationUrl)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}