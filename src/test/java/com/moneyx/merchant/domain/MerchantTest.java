package com.moneyx.merchant.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes unitários puros: não sobem o Spring nem acessam o banco.
 * Validam apenas as regras de negócio da entidade.
 */
class MerchantTest {

    private Merchant newMerchant() {
        return new Merchant("Loja Exemplo LTDA", "12.345.678/0001-90", "Contato@LojaExemplo.com");
    }

    @Test
    void newMerchantStartsActiveWithNormalizedData() {
        Merchant merchant = newMerchant();

        assertThat(merchant.getId()).isNotNull();
        assertThat(merchant.getStatus()).isEqualTo(MerchantStatus.ACTIVE);
        assertThat(merchant.getDocument()).isEqualTo("12345678000190");
        assertThat(merchant.getEmail()).isEqualTo("contato@lojaexemplo.com");
        assertThat(merchant.getCreatedAt()).isEqualTo(merchant.getUpdatedAt());
    }

    @Test
    void rejectsDocumentWithInvalidLength() {
        assertThatThrownBy(() -> new Merchant("Loja", "123.456", "a@b.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("document");
    }

    @Test
    void rejectsBlankFields() {
        assertThatThrownBy(() -> new Merchant(" ", "12345678901", "a@b.com"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Merchant("Loja", "12345678901", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void activeMerchantCanBeSuspendedAndReactivated() {
        Merchant merchant = newMerchant();

        merchant.suspend();
        assertThat(merchant.getStatus()).isEqualTo(MerchantStatus.SUSPENDED);

        merchant.activate();
        assertThat(merchant.getStatus()).isEqualTo(MerchantStatus.ACTIVE);
    }

    @Test
    void cannotActivateAlreadyActiveMerchant() {
        Merchant merchant = newMerchant();

        assertThatThrownBy(merchant::activate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void closedIsFinalState() {
        Merchant merchant = newMerchant();
        merchant.close();

        assertThat(merchant.getStatus()).isEqualTo(MerchantStatus.CLOSED);
        assertThatThrownBy(merchant::activate).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(merchant::suspend).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(merchant::close).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void onlyActiveMerchantIsActive() {
        Merchant merchant = newMerchant();
        assertThat(merchant.isActive()).isTrue();

        merchant.suspend();
        assertThat(merchant.isActive()).isFalse();

        merchant.close();
        assertThat(merchant.isActive()).isFalse();
    }
}
