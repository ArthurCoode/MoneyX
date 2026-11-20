package com.moneyx.security;

import com.moneyx.security.ratelimit.RateLimitExceededException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduz erros de segurança em Problem Details (RFC 9457).
 *
 * Recebe as exceções repassadas pelo SecurityConfig (entry point e access denied handler).
 * As mensagens são genéricas de propósito: nunca indicar se a chave existe, foi revogada
 * ou pertence a outro comerciante.
 */
@RestControllerAdvice
public class SecurityExceptionHandler {

    /** 401: não sabemos quem é o cliente (sem chave, chave inválida ou revogada). */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, "A valid API key is required");

        // WWW-Authenticate informa ao cliente qual esquema de autenticação usar (RFC 6750).
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .body(body);
    }

    /** 403: sabemos quem é, mas o comerciante não pode operar (SUSPENDED ou CLOSED). */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, "Merchant account is not allowed to access this resource");
    }

    /** 429: limite de tentativas atingido. Retry-After (RFC 9110) diz em quantos segundos tentar de novo. */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ProblemDetail> handleRateLimit(RateLimitExceededException ex) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
        body.setProperty("code", "rate_limit_exceeded");
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(ex.retryAfterSeconds()))
                .body(body);
    }
}
