package com.moneyx.merchant.domain;

/**
 * Regra de senha do dashboard, no estilo do NIST SP 800-63B: comprimento importa, composição forçada ("1 maiúscula, 1
 * símbolo...") não. Mínimo 12 caracteres; máximo 128 (o Argon2 aceita mais, mas um teto evita abuso de CPU/memória com
 * senhas gigantes). Não pode ser igual ao e-mail.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;

    private PasswordPolicy() {
    }

    /** @throws IllegalArgumentException com a regra violada (vira 400 na API) */
    public static void validate(String password, String email) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("password must not be blank");
        }
        int length = password.codePointCount(0, password.length());
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "password must have between " + MIN_LENGTH + " and " + MAX_LENGTH + " characters");
        }
        if (email != null && password.trim().equalsIgnoreCase(email.trim())) {
            throw new IllegalArgumentException("password must not be the email");
        }
    }
}
