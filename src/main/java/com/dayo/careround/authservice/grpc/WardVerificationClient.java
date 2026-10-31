package com.dayo.careround.authservice.grpc;

import com.dayo.careround.authservice.exception.DownstreamServiceUnavailableException;
import com.dayo.careround.wardservice.grpc.WardExistsRequest;
import com.dayo.careround.wardservice.grpc.WardVerificationServiceGrpc;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class WardVerificationClient {

    @GrpcClient("ward-service")
    private WardVerificationServiceGrpc.WardVerificationServiceBlockingStub wardStub;

    @Value("${app.grpc.deadline-ms:5000}")
    private long deadlineMs;

    // Fails closed: assignedWardId becomes the JWT ward claim that authorises realtime ward subscriptions.
    public boolean wardExists(UUID wardId) {
        WardExistsRequest request = WardExistsRequest.newBuilder()
                .setWardId(wardId.toString())
                .build();
        try {
            return wardStub.withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS).wardExists(request).getExists();
        } catch (StatusRuntimeException e) {
            throw new DownstreamServiceUnavailableException(
                    "Could not verify ward (ward-service: " + e.getStatus().getCode() + ")", e);
        }
    }
}
