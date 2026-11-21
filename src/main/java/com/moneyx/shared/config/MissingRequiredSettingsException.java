package com.moneyx.shared.config;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Uma ou mais configurações obrigatórias não foram fornecidas. Lançada por {@link RequiredSettingsCheck} na partida.
 * A mensagem cita apenas NOMES (propriedade e variável de ambiente), nunca valores.
 */
public class MissingRequiredSettingsException extends IllegalStateException {

    public record MissingSetting(String property, String environmentVariable) {

        String describe() {
            return property + " (environment variable " + environmentVariable + ")";
        }
    }

    private final List<MissingSetting> missingSettings;

    public MissingRequiredSettingsException(List<MissingSetting> missingSettings) {
        super("Required settings are missing: " + missingSettings.stream()
                .map(MissingSetting::describe)
                .collect(Collectors.joining(", ")));
        this.missingSettings = List.copyOf(missingSettings);
    }

    public List<MissingSetting> missingSettings() {
        return missingSettings;
    }
}
