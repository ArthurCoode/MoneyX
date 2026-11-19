package com.moneyx.security;

import com.moneyx.apikey.service.ApiKeyService;
import com.moneyx.merchant.service.MerchantService;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
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
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;

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
public class SecurityConfig {

    /** Autoridade concedida apenas a comerciantes ACTIVE. */
    public static final String ACTIVE_MERCHANT = "ACTIVE_MERCHANT";

    @Bean
    public SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http,
            ApiKeyService apiKeyService,
            MerchantService merchantService,
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
                .authorizeHttpRequests(auth -> auth
                        // Despacho interno para /error (exceção não tratada). Sem isso, um 500
                        // seria mascarado como 401, pois o contexto de segurança não é guardado em sessão.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/merchants").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        // Todo o resto exige comerciante autenticado E ativo.
                        // Sem autenticação -> 401 (entry point). Autenticado sem a autoridade -> 403.
                        .anyRequest().hasAuthority(ACTIVE_MERCHANT))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
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
