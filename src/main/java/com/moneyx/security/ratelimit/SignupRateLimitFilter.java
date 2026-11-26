package com.moneyx.security.ratelimit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;

/**
 * Limita o cadastro público (POST /v1/merchants) por IP.
 *
 * Toda TENTATIVA conta, inclusive as que terminariam em 400 ou 409: é justamente o 409 ("documento já cadastrado")
 * que permite a um robô descobrir quem é cliente. Por isso o filtro decide ANTES de a requisição chegar ao controller.
 *
 * Não é @Component: é instanciado no SecurityConfig (como o filtro de API Key), para não ser registrado duas vezes.
 * A recusa é entregue ao HandlerExceptionResolver do MVC, que a transforma em Problem Details (mesmo formato da API).
 */
public class SignupRateLimitFilter extends OncePerRequestFilter {

    private static final RequestMatcher SIGNUP = PathPatternRequestMatcher.withDefaults()
            .matcher(HttpMethod.POST, "/v1/merchants");

    private final RateLimiter limiter;
    private final HandlerExceptionResolver exceptionResolver;
    private final Counter rejections;

    public SignupRateLimitFilter(RateLimiter limiter, HandlerExceptionResolver exceptionResolver,
                                 MeterRegistry meterRegistry) {
        this.limiter = limiter;
        this.exceptionResolver = exceptionResolver;
        // moneyx.rate_limit.rejections{route}: um pico aqui indica abuso (ou um limite apertado demais).
        this.rejections = Counter.builder("moneyx.rate_limit.rejections")
                .description("Requisições recusadas pelo limite de tentativas")
                .tag("route", "signup")
                .register(meterRegistry);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !SIGNUP.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        // remoteAddr = o endereço da CONEXÃO. X-Forwarded-For é ignorado de propósito (ver ClientKey).
        RateLimiter.Decision decision = limiter.tryAcquire(ClientKey.of(request.getRemoteAddr()));
        if (decision.allowed()) {
            chain.doFilter(request, response);
            return;
        }
        rejections.increment();
        RateLimitExceededException exception = new RateLimitExceededException(decision.retryAfter());
        if (exceptionResolver.resolveException(request, response, null, exception) == null) {
            response.sendError(429); // nunca deixar passar se nenhum handler tratar
        }
    }
}
