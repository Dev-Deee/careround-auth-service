package com.dayo.careround.authservice.controllers;

import com.dayo.careround.authservice.data.models.AuthUser;
import com.dayo.careround.authservice.data.models.enums.OutboxStatus;
import com.dayo.careround.authservice.data.repositories.AuthUserRepository;
import com.dayo.careround.authservice.data.repositories.OutboxEventRepository;
import com.dayo.careround.authservice.dto.request.*;
import com.dayo.careround.authservice.dto.response.AuthResponse;
import com.dayo.careround.authservice.dto.response.OutboxDeadLetterResponse;
import com.dayo.careround.authservice.dto.response.WardAssignmentResponse;
import com.dayo.careround.authservice.dto.response.WardProblemsResponse;
import com.dayo.careround.authservice.exception.InvalidCredentialsException;
import com.dayo.careround.authservice.security.CareRoundPrincipal;
import com.dayo.careround.authservice.services.AuthService;
import com.dayo.careround.authservice.services.OutboxRecoveryService;
import com.dayo.careround.authservice.services.WardAssignmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthUserRepository authUserRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final OutboxRecoveryService outboxRecoveryService;
    private final WardAssignmentService wardAssignmentService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/admin/create-user")
    @PreAuthorize("hasAuthority('PERM_CREATE_USER') or hasAuthority('PERM_GRANT_PERMISSIONS')")
    public ResponseEntity<Map<String, String>> createUser(
            @Valid @RequestBody RegisterRequest request,
            @AuthenticationPrincipal CareRoundPrincipal principal) {
        AuthUser creator = authUserRepository.findById(principal.userId())
                .orElseThrow(InvalidCredentialsException::new);
        authService.register(request, creator);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Account created. Activation email sent.",
                "status", "PENDING_VERIFICATION"
        ));
    }

    @PostMapping("/activate-account")
    public ResponseEntity<Void> activateAccount(@Valid @RequestBody ActivateAccountRequest request) {
        authService.activateAccount(request.token(), request.password());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/admin/resend-activation")
    @PreAuthorize("hasAuthority('PERM_CREATE_USER')")
    public ResponseEntity<Void> resendActivation(@Valid @RequestBody ResendActivationRequest request) {
        authService.resendActivation(request.email());
        return ResponseEntity.noContent().build();
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

    @PutMapping("/change-email")
    public ResponseEntity<Void> changeEmail(
            @Valid @RequestBody ChangeEmailRequest request,
            @AuthenticationPrincipal CareRoundPrincipal principal) {
        authService.changeEmail(principal.userId(), request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/confirm-email-change")
    public ResponseEntity<Void> confirmEmailChange(@Valid @RequestBody ConfirmEmailChangeRequest request) {
        authService.confirmEmailChange(request.token());
        return ResponseEntity.noContent().build();
    }
    // No payload in the response: it carries personal data (names, emails, licence numbers).
    @GetMapping("/admin/outbox/dead-letters")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('PERM_VIEW_AUDIT_LOGS')")
    public ResponseEntity<List<OutboxDeadLetterResponse>> listDeadLetters() {
        return ResponseEntity.ok(outboxEventRepository.findByStatus(OutboxStatus.DEAD_LETTER).stream()
                .map(OutboxDeadLetterResponse::from)
                .toList());
    }

    @PostMapping("/admin/outbox/{id}/retry")
    @PreAuthorize("hasAuthority('PERM_MANAGE_SYSTEM_CONFIG')")
    public ResponseEntity<Void> retryDeadLetter(@PathVariable UUID id) {
        outboxRecoveryService.retryDeadLetter(id);
        return ResponseEntity.noContent().build();
    }

    // Existing access tokens keep the old ward claim until they expire (<= 15 min); login/refresh read the new one.
    @PutMapping("/admin/users/{userId}/ward")
    @PreAuthorize("hasAuthority('PERM_ASSIGN_WARD')")
    public ResponseEntity<WardAssignmentResponse> assignWard(
            @PathVariable UUID userId,
            @Valid @RequestBody AssignWardRequest request,
            @AuthenticationPrincipal CareRoundPrincipal principal) {
        return ResponseEntity.ok(wardAssignmentService.assignWard(userId, request.wardId(), principal.userId()));
    }

    // DOCTOR/NURSE accounts with no ward or a ward ward-service does not know; capped at WARD_PROBLEMS_CAP.
    @GetMapping("/admin/users/ward-problems")
    @PreAuthorize("hasAuthority('PERM_ASSIGN_WARD')")
    public ResponseEntity<WardProblemsResponse> wardProblems() {
        return ResponseEntity.ok(wardAssignmentService.findWardProblems());
    }

    @DeleteMapping("/admin/pending-account/{userId}")
    @PreAuthorize("hasAuthority('PERM_DEACTIVATE_USER')")
    public ResponseEntity<Void> cancelPendingAccount(@PathVariable UUID userId) {
        authService.cancelPendingAccount(userId);
        return ResponseEntity.noContent().build();
    }
}