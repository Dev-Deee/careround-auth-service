package com.dayo.careround.authservice.grpc;

import com.dayo.careround.userservice.grpc.LicenseCheckRequest;
import com.dayo.careround.userservice.grpc.LicenseCheckResponse;
import com.dayo.careround.userservice.grpc.LicenseVerificationServiceGrpc;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

@Service
public class LicenseVerificationClient {

    @GrpcClient("user-service")
    private LicenseVerificationServiceGrpc.LicenseVerificationServiceBlockingStub licenseStub;

    public boolean licenseNumberExists(String licenseNumber) {
        LicenseCheckRequest request = LicenseCheckRequest.newBuilder()
                .setLicenseNumber(licenseNumber)
                .build();
        return licenseStub.licenseNumberExists(request).getExists();
    }
}