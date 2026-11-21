package com.moneyx.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.moneyx.MoneyxApplication;

/**
 * Sobe a APLICAÇÃO REAL sem a senha do banco: prova que o check está registrado (META-INF/spring.factories), que a
 * partida é recusada com o quadro "APPLICATION FAILED TO START" e que isso acontece ANTES de qualquer conexão.
 *
 * O argumento de linha de comando tem precedência sobre os arquivos de propriedades (inclusive os dos testes, que
 * definem uma senha) e sobre uma eventual variável POSTGRES_PASSWORD na máquina de quem roda o teste.
 */
@ExtendWith(OutputCaptureExtension.class)
class RequiredSettingsStartupTest {

    @Test
    void applicationRefusesToStartWithoutTheDatabasePassword(CapturedOutput output) {
        SpringApplication application = new SpringApplication(MoneyxApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);

        assertThatThrownBy(() -> application.run(
                "--spring.datasource.password=${MONEYX_TEST_VARIABLE_THAT_IS_NOT_DEFINED}"))
                .isInstanceOf(MissingRequiredSettingsException.class);

        assertThat(output)
                .contains("APPLICATION FAILED TO START")
                .contains("spring.datasource.password (environment variable POSTGRES_PASSWORD)")
                .contains("docker compose up -d --build")
                // Falhou antes de existir pool de conexões ou migrations.
                .doesNotContain("HikariPool")
                .doesNotContain("Flyway")
                .doesNotContain("password authentication failed");
    }
}
