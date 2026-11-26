package com.moneyx.merchant.api;

import com.moneyx.merchant.exception.InvalidCredentialsException;
import com.moneyx.merchant.exception.PasswordResetException;
import com.moneyx.merchant.exception.TooManyAttemptsException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Erros de conta (cadastro, login, senha) em Problem Details. Restrito aos controllers de conta (assignableTypes): o
 * IllegalArgumentException daqui (senha fora da regra) não pode mascarar bugs de outros módulos.
 */
@RestControllerAdvice(assignableTypes = {MerchantController.class, SessionController.class, PasswordResetController.class})
public class AccountExceptionHandler {

    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentials(InvalidCredentialsException ex) {
        return withCode(HttpStatus.UNAUTHORIZED, ex.getMessage(), "invalid_credentials");
    }

    @ExceptionHandler(TooManyAttemptsException.class)
    public ResponseEntity<ProblemDetail> handleTooManyAttempts(TooManyAttemptsException ex) {
        long seconds = Math.max(1, (ex.getRetryAfter().toMillis() + 999) / 1000);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(seconds))
                .body(withCode(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), "rate_limit_exceeded"));
    }

    @ExceptionHandler(PasswordResetException.class)
    public ProblemDetail handlePasswordReset(PasswordResetException ex) {
        return withCode(HttpStatus.BAD_REQUEST, ex.getMessage(), ex.getCode());
    }

    /** Regra de senha (PasswordPolicy) e dados que o domínio recusa: 400 com a mensagem da regra. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleInvalidArgument(IllegalArgumentException ex) {
        return withCode(HttpStatus.BAD_REQUEST, ex.getMessage(), "invalid_request");
    }

    private static ProblemDetail withCode(HttpStatus status, String detail, String code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }
}
