package com.moneyx.shared.config;

import java.util.stream.Collectors;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * Transforma {@link MissingRequiredSettingsException} no quadro "APPLICATION FAILED TO START" do Spring Boot:
 * uma descrição curta do problema e a ação para resolvê-lo, em vez de um stack trace longo.
 * Registrado em META-INF/spring.factories.
 */
public class MissingRequiredSettingsFailureAnalyzer extends AbstractFailureAnalyzer<MissingRequiredSettingsException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, MissingRequiredSettingsException cause) {
        String settings = cause.missingSettings().stream()
                .map(setting -> "\t- " + setting.describe())
                .collect(Collectors.joining(System.lineSeparator()));
        String description = "The application cannot start because required settings have no value:"
                + System.lineSeparator() + System.lineSeparator() + settings;
        String action = "Define the environment variables listed above. In development they come from the .env file:"
                + " start everything with 'docker compose up -d --build', or load the .env into the shell before"
                + " running 'mvnw spring-boot:run'.";
        return new FailureAnalysis(description, action, cause);
    }
}
