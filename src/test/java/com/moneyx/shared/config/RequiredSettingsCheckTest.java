package com.moneyx.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.mock.env.MockEnvironment;

/**
 * Cada forma de "senha do banco não fornecida" é recusada, e nenhuma mensagem carrega o valor.
 * MockEnvironment não inclui as variáveis de ambiente da máquina: o resultado não depende de quem roda o teste.
 */
class RequiredSettingsCheckTest {

    private static final String PROPERTY = "spring.datasource.password";

    private final RequiredSettingsCheck check = new RequiredSettingsCheck();

    @Test
    void unresolvedPlaceholderIsRejectedInsteadOfBecomingTheLiteralPassword() {
        // O caso real: application.properties tem ${POSTGRES_PASSWORD} e a variável não existe.
        MockEnvironment environment = new MockEnvironment()
                .withProperty(PROPERTY, "${MONEYX_TEST_VARIABLE_THAT_IS_NOT_DEFINED}");

        assertThatThrownBy(() -> check.postProcessEnvironment(environment, null))
                .isInstanceOf(MissingRequiredSettingsException.class)
                .hasMessage("Required settings are missing: "
                        + "spring.datasource.password (environment variable POSTGRES_PASSWORD)")
                .message().doesNotContain("MONEYX_TEST_VARIABLE_THAT_IS_NOT_DEFINED");
    }

    @Test
    void unresolvedPlaceholderIsRejectedAlsoWhenTheResolverIsTolerant() {
        // No modo tolerante o Environment não lança exceção: devolve o texto "${...}". Também é ausência.
        MockEnvironment environment = new MockEnvironment()
                .withProperty(PROPERTY, "${MONEYX_TEST_VARIABLE_THAT_IS_NOT_DEFINED}");
        environment.setIgnoreUnresolvableNestedPlaceholders(true);

        assertThatThrownBy(() -> check.postProcessEnvironment(environment, null))
                .isInstanceOf(MissingRequiredSettingsException.class);
    }

    @Test
    void absentPropertyIsRejected() {
        assertThatThrownBy(() -> check.postProcessEnvironment(new MockEnvironment(), null))
                .isInstanceOf(MissingRequiredSettingsException.class);
    }

    @Test
    void blankValueIsRejected() {
        MockEnvironment environment = new MockEnvironment().withProperty(PROPERTY, "   ");

        assertThatThrownBy(() -> check.postProcessEnvironment(environment, null))
                .isInstanceOf(MissingRequiredSettingsException.class);
    }

    @Test
    void placeholderResolvedFromAnotherPropertyIsAccepted() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(PROPERTY, "${POSTGRES_PASSWORD}")
                .withProperty("POSTGRES_PASSWORD", "s3cr3t-value");

        check.postProcessEnvironment(environment, null);
    }

    @Test
    void plainValueIsAccepted() {
        check.postProcessEnvironment(new MockEnvironment().withProperty(PROPERTY, "test"), null);
    }

    @Test
    void failureAnalysisNamesTheVariableAndTheFixWithoutAnyValue() {
        MissingRequiredSettingsException exception = new MissingRequiredSettingsException(java.util.List.of(
                new MissingRequiredSettingsException.MissingSetting(PROPERTY, "POSTGRES_PASSWORD")));

        FailureAnalysis analysis = new MissingRequiredSettingsFailureAnalyzer()
                .analyze(new IllegalStateException("wrapper", exception));

        assertThat(analysis).isNotNull();
        assertThat(analysis.getDescription())
                .contains("required settings have no value")
                .contains("spring.datasource.password (environment variable POSTGRES_PASSWORD)");
        assertThat(analysis.getAction()).contains(".env").contains("docker compose up -d --build");
    }
}
