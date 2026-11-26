package com.moneyx.security.ratelimit;

import com.moneyx.merchant.service.LoginRateLimits;

import java.time.Duration;
import java.util.Optional;

/**
 * Limites do login e da redefinição de senha (Etapa 8b), por IP E por e-mail:
 * - login por IP: 20 em rajada, +1 a cada 30 s (um escritório atrás do mesmo IP ainda consegue entrar);
 * - login por e-mail: 10 em rajada, +1 a cada 90 s (= 10 tentativas a cada 15 min). Protege UMA conta contra quem tem
 *   muitos IPs. Conta toda tentativa, certa ou errada: o dono legítimo raramente erra 10 vezes;
 * - redefinição por IP: 5, +1 a cada 2 min; por e-mail: 3, +1 a cada 20 min (cada pedido manda um e-mail).
 *
 * O balde por e-mail existe para qualquer texto digitado, exista a conta ou não: o bloqueio não revela quem tem conta.
 * Os dois baldes são sempre consumidos (o IP primeiro), e a espera devolvida é a maior das duas.
 */
public class LoginRateLimiter implements LoginRateLimits {

    private final RateLimiter loginByIp;
    private final RateLimiter loginByEmail;
    private final RateLimiter resetByIp;
    private final RateLimiter resetByEmail;

    public LoginRateLimiter(RateLimiterFactory factory) {
        this.loginByIp = factory.create("login-ip", 20, Duration.ofSeconds(30));
        this.loginByEmail = factory.create("login-email", 10, Duration.ofSeconds(90));
        this.resetByIp = factory.create("reset-ip", 5, Duration.ofMinutes(2));
        this.resetByEmail = factory.create("reset-email", 3, Duration.ofMinutes(20));
    }

    @Override
    public Optional<Duration> acquireLogin(String clientIp, String email) {
        return acquire(loginByIp, loginByEmail, clientIp, email);
    }

    @Override
    public Optional<Duration> acquirePasswordReset(String clientIp, String email) {
        return acquire(resetByIp, resetByEmail, clientIp, email);
    }

    private static Optional<Duration> acquire(RateLimiter byIp, RateLimiter byEmail, String clientIp, String email) {
        RateLimiter.Decision ip = byIp.tryAcquire(ClientKey.of(clientIp));
        if (!ip.allowed()) {
            return Optional.of(ip.retryAfter());
        }
        RateLimiter.Decision account = byEmail.tryAcquire(email == null ? "" : email);
        return account.allowed() ? Optional.empty() : Optional.of(account.retryAfter());
    }
}
