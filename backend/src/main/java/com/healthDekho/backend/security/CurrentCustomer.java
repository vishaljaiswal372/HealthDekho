package com.healthDekho.backend.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/** Resolves the logged-in customer id from the verified JWT. Never trust a customer id sent by the client. */
@Component
public class CurrentCustomer {

    public static final String CUSTOMER_ID_CLAIM = "cid";

    public long id() {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token
                && token.getToken().getClaim(CUSTOMER_ID_CLAIM) instanceof Number id) {
            return id.longValue();
        }
        throw new AuthenticationCredentialsNotFoundException("No authenticated customer");
    }
}
