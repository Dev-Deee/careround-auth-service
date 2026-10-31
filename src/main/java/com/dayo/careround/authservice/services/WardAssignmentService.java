package com.dayo.careround.authservice.services;

import com.dayo.careround.authservice.data.models.AuthUser;
import com.dayo.careround.authservice.data.models.enums.UserRole;
import com.dayo.careround.authservice.data.repositories.AuthUserRepository;
import com.dayo.careround.authservice.dto.response.WardAssignmentResponse;
import com.dayo.careround.authservice.dto.response.WardProblemResponse;
import com.dayo.careround.authservice.dto.response.WardProblemsResponse;
import com.dayo.careround.authservice.exception.InvalidWardAssignmentException;
import com.dayo.careround.authservice.exception.UserNotFoundException;
import com.dayo.careround.authservice.exception.WardNotFoundException;
import com.dayo.careround.authservice.grpc.WardVerificationClient;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Changing a clinical user's ward after registration. assignedWardId becomes the JWT ward claim that authorises
 * ward-scoped access (alert replay, realtime subscriptions), so the new ward is verified like at create-user.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WardAssignmentService {

    public static final int WARD_PROBLEMS_CAP = 500;
    private static final Set<UserRole> CLINICAL = Set.of(UserRole.DOCTOR, UserRole.NURSE);

    private final AuthUserRepository authUserRepository;
    private final WardVerificationClient wardVerificationClient;

    @Transactional
    public WardAssignmentResponse assignWard(UUID userId, UUID wardId, UUID changedBy) {
        AuthUser user = authUserRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("No user with that id"));
        if (!CLINICAL.contains(user.getRole())) {
            throw new InvalidWardAssignmentException("Only DOCTOR and NURSE accounts have a ward; this account is " + user.getRole());
        }
        if (!wardVerificationClient.wardExists(wardId)) {
            throw new WardNotFoundException("No ward with that id");
        }

        UUID previous = user.getAssignedWardId();
        user.setAssignedWardId(wardId);
        authUserRepository.save(user);
        // No audit table exists in auth-service; this line is the audit record.
        log.info("AUDIT ward reassignment: userId={} role={} from={} to={} by={}", user.getId(), user.getRole(), previous, wardId, changedBy);

        return new WardAssignmentResponse(user.getId(), user.getRole(), user.getEmail(), previous, wardId, WardAssignmentResponse.TOKEN_NOTE);
    }

    /**
     * DOCTOR/NURSE accounts whose ward is missing or unknown to ward-service. One WardExists call per distinct ward id,
     * so the cost is bounded by the number of wards, not users. Fails with 503 if ward-service cannot answer.
     */
    public WardProblemsResponse findWardProblems() {
        List<AuthUser> clinical = authUserRepository.findByRoleIn(CLINICAL);

        Map<UUID, Boolean> known = clinical.stream()
                .map(AuthUser::getAssignedWardId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toMap(Function.identity(), wardVerificationClient::wardExists));

        List<WardProblemResponse> problems = clinical.stream()
                .filter(u -> u.getAssignedWardId() == null || !known.get(u.getAssignedWardId()))
                .map(u -> new WardProblemResponse(u.getId(), u.getRole(), u.getEmail(), u.getAssignedWardId(),
                        u.getAssignedWardId() == null ? "MISSING" : "UNKNOWN_WARD"))
                .sorted(Comparator.comparing(WardProblemResponse::role).thenComparing(WardProblemResponse::email))
                .toList();

        return new WardProblemsResponse(problems.size(), WARD_PROBLEMS_CAP,
                problems.size() > WARD_PROBLEMS_CAP ? problems.subList(0, WARD_PROBLEMS_CAP) : problems);
    }
}
