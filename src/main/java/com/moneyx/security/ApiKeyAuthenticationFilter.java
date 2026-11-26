package com.moneyx.security;

import com.moneyx.apikey.domain.ApiKey;
import com.moneyx.apikey.service.ApiKeyService;
import com.moneyx.merchant.domain.Merchant;
import com.moneyx.merchant.service.MerchantService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Autentica requisições pelo header "Authorization: Bearer <api key>".
 *
 * - Sem header Authorization: segue sem autenticar. Se a rota exigir autenticação,
 *   o Spring Security responde 401 pelo AuthenticationEntryPoint.
 * - Header presente mas inválido (formato errado, chave inexistente ou revogada):
 *   responde 401 imediatamente, mesmo em rotas públicas. Uma credencial errada é
 *   sempre um erro do cliente e não deve ser ignorada em silêncio.
 * - Chave válida: coloca um AuthenticatedMerchant no SecurityContext. Se o comerciante
 *   estiver ACTIVE, recebe a autoridade ACTIVE_MERCHANT; se estiver SUSPENDED/CLOSED,
 *   fica autenticado SEM autoridades e as regras do SecurityConfig resultam em 403.
 *
 * NÃO anotar com @Component: o Spring Boot registraria o filtro também na cadeia de
 * servlets, e ele rodaria duas vezes. Ele é criado dentro do SecurityConfig.
 *
 * Nunca logar o header Authorization: ele contém o segredo.
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final ApiKeyService apiKeyService;
    private final MerchantService merchantService;
    private final AuthenticationEntryPoint authenticationEntryPoint;
    private final SecurityContextHolderStrategy securityContextHolder =
            SecurityContextHolder.getContextHolderStrategy();

    public ApiKeyAuthenticationFilter(ApiKeyService apiKeyService,
                                      MerchantService merchantService,
                                      AuthenticationEntryPoint authenticationEntryPoint) {
        this.apiKeyService = apiKeyService;
        this.merchantService = merchantService;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<Authentication> authentication = authenticate(header);
        if (authentication.isEmpty()) {
            securityContextHolder.clearContext();
            // Mensagem genérica: não revelamos se a chave não existe ou se foi revogada.
            authenticationEntryPoint.commence(request, response, new BadCredentialsException("Invalid API key"));
            return;
        }

        // Um contexto novo por requisição (e não getContext().setAuthentication(...))
        // evita condições de corrida entre threads que compartilhem o mesmo contexto.
        SecurityContext context = securityContextHolder.createEmptyContext();
        context.setAuthentication(authentication.get());
        securityContextHolder.setContext(context);

        filterChain.doFilter(request, response);
    }

    private Optional<Authentication> authenticate(String header) {
        // O esquema "Bearer" não diferencia maiúsculas de minúsculas (RFC 7235).
        if (!header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return Optional.empty();
        }
        String plainTextKey = header.substring(BEARER_PREFIX.length()).trim();

        return apiKeyService.findActiveByPlainText(plainTextKey)
                .map(this::toAuthentication);
    }

    private Authentication toAuthentication(ApiKey apiKey) {
        // A FK api_keys -> merchants garante que o comerciante existe.
        Merchant merchant = merchantService.getById(apiKey.getMerchantId());

        List<GrantedAuthority> authorities = merchant.isActive()
                ? List.of(new SimpleGrantedAuthority(SecurityConfig.ACTIVE_MERCHANT))
                : List.of();

        AuthenticatedMerchant principal = new AuthenticatedMerchant(merchant.getId(), apiKey.getId(), apiKey.isLive());

        // Apesar do nome, UsernamePasswordAuthenticationToken é a implementação genérica de
        // Authentication do Spring Security. "authenticated(...)" marca a identidade como já
        // verificada. Credenciais = null: o segredo não precisa (nem deve) ficar na memória da requisição.
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
    }
}
