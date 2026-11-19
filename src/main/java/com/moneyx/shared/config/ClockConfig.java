package com.moneyx.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Relógio da aplicação, sempre em UTC.
 *
 * Os serviços obtêm "agora" deste bean em vez de chamar Instant.now() diretamente.
 * Em testes, ele pode ser substituído por um relógio controlado (ex.: avançar 24 h sem esperar).
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
