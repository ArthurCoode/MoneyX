package com.moneyx.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

/**
 * Atalho para testes de controller que só precisam de "um comerciante autenticado".
 *
 * Injeta o Authentication direto no SecurityContext da requisição simulada, sem passar
 * pelo filtro de API Key. O próprio filtro é testado em ApiKeyAuthenticationTest.
 */
public final class TestAuthentication {

    private TestAuthentication() {
    }

    public static RequestPostProcessor activeMerchant(UUID merchantId) {
        AuthenticatedMerchant principal = new AuthenticatedMerchant(merchantId, UUID.randomUUID());
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority(SecurityConfig.ACTIVE_MERCHANT))));
    }
}
