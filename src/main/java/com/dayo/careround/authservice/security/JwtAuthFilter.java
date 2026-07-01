package com.dayo.careround.authservice.security;

import com.dayo.careround.authservice.services.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        try {
            Claims claims = jwtService.verifyToken(token);

            UUID userId = UUID.fromString(claims.getSubject());
            String role = claims.get("role", String.class);
            String grade = claims.get("grade", String.class);
            String wardIdStr = claims.get("assignedWardId", String.class);
            UUID assignedWardId = wardIdStr != null ? UUID.fromString(wardIdStr) : null;

            CareRoundPrincipal principal = new CareRoundPrincipal(userId, role, grade, assignedWardId);

            List<GrantedAuthority> authorities = List.of(
                    new SimpleGrantedAuthority("ROLE_" + role)
            );
            // grade authority added only if present (not all roles carry one the same way)
            if (grade != null) {
                authorities = List.of(
                        new SimpleGrantedAuthority("ROLE_" + role),
                        new SimpleGrantedAuthority("GRADE_" + grade)
                );
            }

            JwtAuthenticationToken authToken = new JwtAuthenticationToken(principal, authorities);
            SecurityContextHolder.getContext().setAuthentication(authToken);

        } catch (Exception e) {
            // invalid/expired token — leave context unauthenticated, let downstream authorization reject
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}