package com.dayo.careround.authservice.controllers;

import com.dayo.careround.authservice.dto.request.ChangePasswordRequest;
import com.dayo.careround.authservice.dto.request.LoginRequest;
import com.dayo.careround.authservice.dto.request.RefreshRequest;
import com.dayo.careround.authservice.dto.request.RegisterRequest;
import com.dayo.careround.authservice.dto.response.AuthResponse;
import com.dayo.careround.authservice.security.CareRoundPrincipal;
import com.dayo.careround.authservice.services.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Registration successful. Profile setup may take a moment.",
                "status", "PENDING_PROFILE_SETUP"
        ));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal CareRoundPrincipal principal) {
        authService.changePassword(principal.userId(), request);
        return ResponseEntity.noContent().build();
    }
}