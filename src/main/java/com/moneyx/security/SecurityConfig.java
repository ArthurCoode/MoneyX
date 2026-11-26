package com.moneyx.security;

import com.moneyx.apikey.service.ApiKeyService;
import com.moneyx.merchant.service.MerchantService;
import com.moneyx.security.ratelimit.SignupRateLimitFilter;
import com.moneyx.security.ratelimit.SignupRateLimitProperties;
import com.moneyx.security.ratelimit.RateLimiter;
import com.moneyx.security.ratelimit.RedisTokenBucketRateLimiter;
import com.moneyx.security.ratelimit.TokenBucketRateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.time.Clock;

/**
 * Configuração da cadeia de segurança (SecurityFilterChain) da API.
 *
 * - Stateless: sem sessão HTTP. Cada requisição prova quem é enviando a API Key.
 * - CSRF desabilitado: o ataque CSRF explora cookies enviados automaticamente pelo navegador.
 *   Esta API não usa cookies, a credencial vai num header que o navegador não envia sozinho.
 * - httpBasic/formLogin/logout desabilitados: não há usuário/senha nem páginas de login.
 *
 * Erros de segurança (401/403) acontecem nos filtros, ANTES de chegar a um controller,
 * portanto os @RestControllerAdvice não os veriam. O entry point e o access denied handler
 * repassam a exceção ao HandlerExceptionResolver do Spring MVC, que a entrega ao
 * SecurityExceptionHandler. Assim, os erros de segurança saem no mesmo formato Problem
 * Details do resto da API.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(SignupRateLimitProperties.class)
public class SecurityConfig {

    /** Autoridade concedida apenas a comerciantes ACTIVE. */
    public static final String ACTIVE_MERCHANT = "ACTIVE_MERCHANT";

    /**
     * Content-Security-Policy das respostas da API (JSON): nada pode ser carregado nem executado, e a resposta não pode
     * ser embutida em outra página. Se um navegador abrir um JSON da API, ele não vira página com scripts.
     * Recomendação do OWASP REST Security Cheat Sheet.
     */
    static final String API_CONTENT_SECURITY_POLICY = "default-src 'none'; frame-ancestors 'none'";

    /**
     * Content-Security-Policy da Swagger UI, que é uma PÁGINA de verdade: scripts, estilos, imagens e chamadas só da
     * própria origem. 'unsafe-inline' apenas em estilos (a Swagger UI aplica estilos inline; scripts inline continuam
     * proibidos). Conferida com a página aberta no navegador, sem violações no console.
     */
    static final String DOCS_CONTENT_SECURITY_POLICY = "default-src 'self'; script-src 'self'; "
            + "style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self' data:; connect-src 'self'; "
            + "object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'";

    private static final RequestMatcher DOCS_PAGES = new OrRequestMatcher(
            PathPatternRequestMatcher.withDefaults().matcher("/swagger-ui.html"),
            PathPatternRequestMatcher.withDefaults().matcher("/swagger-ui/**"));

    /**
     * Limitador do cadastro público. Usa o Clock da aplicação quando existe (testes podem controlá-lo); nos testes
     * de fatia web (@WebMvcTest), em que o ClockConfig não é carregado, usa o relógio do sistema.
     */
    @Bean
    public RateLimiter signupRateLimiter(SignupRateLimitProperties properties, ObjectProvider<Clock> clock,
                                         @Value("${moneyx.rate-limit.store:memory}") String store,
                                         ObjectProvider<StringRedisTemplate> redis) {
        Clock time = clock.getIfAvailable(Clock::systemUTC);
        return switch (store) {
            // Compartilhado entre instâncias (Redis do Compose). Recomendado com mais de uma instância da app.
            case "redis" -> new RedisTokenBucketRateLimiter(redis.getObject(), "moneyx:ratelimit:signup:",
                    properties.capacity(), properties.refillPeriod(), time);
            case "memory" -> new TokenBucketRateLimiter(properties.capacity(), properties.refillPeriod(),
                    properties.maxKeys(), time);
            default -> throw new IllegalArgumentException(
                    "moneyx.rate-limit.store must be memory or redis, got: " + store);
        };
    }

    @Bean
    public SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http,
            ApiKeyService apiKeyService,
            MerchantService merchantService,
            RateLimiter signupRateLimiter,
            // Nos testes de fatia web (@WebMvcTest) não há métricas configuradas: usa um registro em memória.
            ObjectProvider<MeterRegistry> meterRegistry,
            // Existem vários HandlerExceptionResolver no contexto; este é o principal do Spring MVC.
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) throws Exception {

        AuthenticationEntryPoint authenticationEntryPoint = (request, response, exception) ->
                resolve(exceptionResolver, request, response, exception, HttpStatus.UNAUTHORIZED);

        AccessDeniedHandler accessDeniedHandler = (request, response, exception) ->
                resolve(exceptionResolver, request, response, exception, HttpStatus.FORBIDDEN);

        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Headers de segurança. Os padrões do Spring Security continuam: X-Content-Type-Options: nosniff,
                // X-Frame-Options: DENY, Cache-Control: no-store, X-XSS-Protection: 0, e Strict-Transport-Security
                // (só em HTTPS; atrás de um proxy com TLS, exige configurar os forwarded headers, Fase 7).
                .headers(headers -> headers
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER))
                        .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(DOCS_PAGES,
                                new StaticHeadersWriter("Content-Security-Policy", DOCS_CONTENT_SECURITY_POLICY)))
                        .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(new NegatedRequestMatcher(DOCS_PAGES),
                                new StaticHeadersWriter("Content-Security-Policy", API_CONTENT_SECURITY_POLICY))))
                .authorizeHttpRequests(auth -> auth
                        // Despacho interno para /error (exceção não tratada). Sem isso, um 500
                        // seria mascarado como 401, pois o contexto de segurança não é guardado em sessão.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/merchants").permitAll()
                        // Link do e-mail de verificação: quem abre ainda não tem a chave à mão. O token é o segredo.
                        .requestMatchers(HttpMethod.GET, "/v1/merchants/verify-email").permitAll()
                        // Avisos do PSP (Mercado Pago): sem API Key; autenticados pela assinatura x-signature no controller.
                        .requestMatchers(HttpMethod.POST, "/v1/provider-webhooks/mercadopago").permitAll()
                        // Actuator (health, metrics, prometheus): só existe na PORTA DE GERENCIAMENTO (8081, ouvindo
                        // apenas em 127.0.0.1). Na porta da API (8080) essas rotas nem existem (404).
                        .requestMatchers(HttpMethod.GET, "/actuator/**").permitAll()
                        // Documentação da API (contrato OpenAPI + Swagger UI): pública, como a do Stripe. Não contém
                        // segredos. Desligável com springdoc.api-docs.enabled=false / springdoc.swagger-ui.enabled=false.
                        .requestMatchers(HttpMethod.GET, "/v3/api-docs", "/v3/api-docs/**",
                                "/swagger-ui.html", "/swagger-ui/**").permitAll()
                        // Todo o resto exige comerciante autenticado E ativo.
                        // Sem autenticação -> 401 (entry point). Autenticado sem a autoridade -> 403.
                        .anyRequest().hasAuthority(ACTIVE_MERCHANT))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                // Limite do cadastro público: é o PRIMEIRO dos nossos filtros (mesma posição, registrado antes),
                // para recusar o abuso o mais cedo e barato possível.
                .addFilterBefore(
                        new SignupRateLimitFilter(signupRateLimiter, exceptionResolver,
                                meterRegistry.getIfAvailable(SimpleMeterRegistry::new)),
                        AnonymousAuthenticationFilter.class)
                // Nosso filtro roda antes do filtro "anônimo": se a chave for válida,
                // a requisição já chega autenticada às regras de autorização.
                .addFilterBefore(
                        new ApiKeyAuthenticationFilter(apiKeyService, merchantService, authenticationEntryPoint),
                        AnonymousAuthenticationFilter.class);

        return http.build();
    }

    private static void resolve(HandlerExceptionResolver resolver,
                                HttpServletRequest request,
                                HttpServletResponse response,
                                Exception exception,
                                HttpStatus fallbackStatus) throws IOException {
        // handler = null: o erro ocorreu antes de qualquer controller ser escolhido.
        if (resolver.resolveException(request, response, null, exception) == null) {
            // Proteção: se nenhum handler tratar a exceção, nunca deixar a requisição passar com 200.
            response.sendError(fallbackStatus.value());
        }
    }
}
