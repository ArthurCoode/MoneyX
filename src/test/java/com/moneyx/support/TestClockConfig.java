package com.moneyx.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Instant;

/**
 * Relógio controlado COMPARTILHADO pelos testes de integração (@Import(TestClockConfig.class)).
 *
 * Por que compartilhado: o Spring guarda em cache um contexto por "configuração de teste" diferente, e cada contexto
 * mantém um pool de conexões aberto. Quando cada classe declarava a sua própria ClockTestConfig aninhada, cada uma
 * ganhava um contexto (e 10 conexões), e a suíte esbarrou no limite do PostgreSQL ("too many clients already").
 * Com uma única configuração, essas classes reutilizam o MESMO contexto.
 *
 * O instante inicial não importa: cada classe posiciona o relógio no seu @BeforeEach (clock.set(START)).
 */
@TestConfiguration
public class TestClockConfig {

    @Bean
    @Primary
    public MutableClock testClock() {
        return new MutableClock(Instant.parse("2020-01-01T00:00:00Z"));
    }
}
