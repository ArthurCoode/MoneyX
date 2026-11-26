package com.moneyx.merchant.service;

import java.time.Duration;
import java.util.Optional;

/**
 * Limites de tentativas de login e de redefinição de senha, por IP e por e-mail. Declarada AQUI e implementada pelo
 * módulo security (que tem os limitadores em memória/Redis): o merchant não importa o security (inversão de
 * dependência, mesmo padrão do EventReferences).
 *
 * Cada método consome uma tentativa dos DOIS baldes (IP e e-mail) e devolve a espera se algum estiver vazio.
 */
public interface LoginRateLimits {

    Optional<Duration> acquireLogin(String clientIp, String email);

    Optional<Duration> acquirePasswordReset(String clientIp, String email);
}
