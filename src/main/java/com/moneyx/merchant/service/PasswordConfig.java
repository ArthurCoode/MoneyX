package com.moneyx.merchant.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

/**
 * Hash das senhas do dashboard: Argon2id (primeira escolha do OWASP), com os parâmetros mínimos recomendados pelo
 * OWASP Password Storage Cheat Sheet: 19 MiB de memória, 2 iterações, paralelismo 1, sal de 16 bytes, hash de 32 bytes.
 * A memória exigida por tentativa é o que torna caro testar bilhões de senhas em GPU.
 *
 * DelegatingPasswordEncoder: o hash guardado começa com "{argon2}". Se um dia o algoritmo ou os parâmetros mudarem,
 * os hashes antigos continuam sendo verificados pelo prefixo, e o login pode regravá-los no formato novo.
 */
@Configuration
class PasswordConfig {

    static final String ARGON2 = "argon2";

    @Bean
    PasswordEncoder passwordEncoder() {
        Argon2PasswordEncoder argon2 = new Argon2PasswordEncoder(16, 32, 1, 19_456, 2);
        return new DelegatingPasswordEncoder(ARGON2, Map.of(ARGON2, argon2));
    }
}
