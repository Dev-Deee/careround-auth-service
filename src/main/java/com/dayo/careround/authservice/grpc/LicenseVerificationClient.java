package com.dayo.careround.authservice.grpc;

import com.dayo.careround.authservice.exception.DownstreamServiceUnavailableException;
import com.dayo.careround.userservice.grpc.LicenseCheckRequest;
import com.dayo.careround.userservice.grpc.LicenseVerificationServiceGrpc;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class LicenseVerificationClient {

    @GrpcClient("user-service")
    private LicenseVerificationServiceGrpc.LicenseVerificationServiceBlockingStub licenseStub;

    @Value("${app.grpc.deadline-ms:5000}")
    private long deadlineMs;

    // Fails closed: an unverified license could be a duplicate, so the account is not created.
    public boolean licenseNumberExists(String licenseNumber) {
        LicenseCheckRequest request = LicenseCheckRequest.newBuilder()
                .setLicenseNumber(licenseNumber)
                .build();
        try {
            return licenseStub.withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS).licenseNumberExists(request).getExists();
        } catch (StatusRuntimeException e) {
            throw new DownstreamServiceUnavailableException(
                    "Could not verify license number (user-service: " + e.getStatus().getCode() + ")", e);
        }
    }
}
