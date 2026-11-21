package com.moneyx.shared.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Recusa a partida da aplicação quando uma configuração obrigatória não foi fornecida.
 *
 * Por que existe: {@code spring.datasource.password=${POSTGRES_PASSWORD}} sem a variável de ambiente NÃO falha como
 * "placeholder não resolvido". O Spring Boot usa o texto literal {@code ${POSTGRES_PASSWORD}} como senha, e o erro que
 * aparece é "password authentication failed", que leva a procurar o problema no lugar errado.
 *
 * Roda como {@link EnvironmentPostProcessor} (registrado em META-INF/spring.factories): depois de carregados os
 * arquivos de propriedades e ANTES de qualquer bean existir. Nenhuma conexão com o banco é tentada.
 *
 * O valor de uma configuração nunca entra na mensagem de erro: poderia ser uma senha colada no lugar errado.
 */
public class RequiredSettingsCheck implements EnvironmentPostProcessor, Ordered {

    /** Propriedade obrigatória → variável de ambiente que a fornece (para a mensagem dizer o que fazer). */
    static final Map<String, String> REQUIRED_SETTINGS = requiredSettings();

    private static Map<String, String> requiredSettings() {
        Map<String, String> settings = new LinkedHashMap<>();
        settings.put("spring.datasource.password", "POSTGRES_PASSWORD");
        return settings;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        List<MissingRequiredSettingsException.MissingSetting> missing = new ArrayList<>();
        REQUIRED_SETTINGS.forEach((property, environmentVariable) -> {
            if (!isDefined(environment, property)) {
                missing.add(new MissingRequiredSettingsException.MissingSetting(property, environmentVariable));
            }
        });
        if (!missing.isEmpty()) {
            throw new MissingRequiredSettingsException(missing);
        }
    }

    static boolean isDefined(ConfigurableEnvironment environment, String property) {
        String value;
        try {
            value = environment.getProperty(property);
        } catch (IllegalArgumentException unresolvablePlaceholder) {
            return false;
        }
        // Se o resolvedor estiver no modo tolerante, o placeholder não resolvido volta como texto: também é ausência.
        return value != null && !value.isBlank() && !value.contains("${");
    }

    /** Depois de todos os outros processadores: as propriedades já estão completas (arquivos, variáveis, argumentos). */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
