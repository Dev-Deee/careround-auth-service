package com.dayo.careround.authservice.services;

import com.dayo.careround.authservice.data.models.AuthUser;
import com.dayo.careround.authservice.data.models.enums.UserRole;
import com.dayo.careround.authservice.data.models.enums.UserStatus;
import com.dayo.careround.authservice.data.repositories.AuthUserRepository;
import com.dayo.careround.authservice.dto.response.AdminUserResponse;
import com.dayo.careround.authservice.dto.response.PageResponse;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminUserQueryService {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private final AuthUserRepository authUserRepository;

    // Accounts filtered by role/status; q matches part of the email (case-insensitive) or the start of the phone
    // number. Newest first.
    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> list(UserRole role, UserStatus status, String q, Integer page, Integer size) {
        String term = q == null || q.isBlank() ? null : escape(q.trim().toLowerCase());
        Specification<AuthUser> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (role != null) p.add(cb.equal(root.get("role"), role));
            if (status != null) p.add(cb.equal(root.get("status"), status));
            if (term != null) p.add(cb.or(
                    cb.like(cb.lower(root.get("email")), "%" + term + "%", '\\'),
                    cb.like(root.get("phoneNumber"), term + "%", '\\')));
            return cb.and(p.toArray(Predicate[]::new));
        };
        var pageable = PageRequest.of(page == null ? 0 : Math.max(page, 0),
                size == null ? DEFAULT_SIZE : Math.min(Math.max(size, 1), MAX_SIZE),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
        return PageResponse.of(authUserRepository.findAll(spec, pageable), u -> new AdminUserResponse(
                u.getId(), u.getPublicId(), u.getEmail(), u.getPhoneNumber(), u.getRole(), u.getGrade(), u.getStatus(),
                u.getAssignedWardId(), Set.copyOf(u.getPermissions()), u.getCreatedAt()));
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
