package com.dayo.careround.authservice.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

public class JwtAuthenticationToken extends AbstractAuthenticationToken {

    private final CareRoundPrincipal principal;

    public JwtAuthenticationToken(CareRoundPrincipal principal, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null; // no credentials to expose — the token itself is not stored here
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }
}