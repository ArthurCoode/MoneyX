package com.moneyx.merchant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Teste de integração da camada de persistência.
 *
 * @DataJpaTest sobe apenas JPA, Flyway e repositórios (sem controllers/serviços).
 * Cada teste roda dentro de uma transação que sofre rollback ao final,
 * então nenhum dado permanece no banco.
 *
 * replace = NONE: usa o PostgreSQL real em vez de tentar um banco em memória,
 * garantindo que as constraints da migration V1 sejam de fato testadas.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MerchantRepositoryTest {

    @Autowired
    private MerchantRepository repository;

    @Test
    void savesAndFindsMerchantById() {
        Merchant merchant = new Merchant("Loja Exemplo LTDA", "98.765.432/0001-10", "repo-test@moneyx.dev");

        repository.saveAndFlush(merchant);

        Merchant found = repository.findById(merchant.getId()).orElseThrow();
        assertThat(found.getDocument()).isEqualTo("98765432000110");
        assertThat(found.getStatus()).isEqualTo(MerchantStatus.ACTIVE);
    }

    @Test
    void existsByDocumentAndEmail() {
        repository.saveAndFlush(new Merchant("Loja A", "11122233344", "exists-test@moneyx.dev"));

        assertThat(repository.existsByDocument("11122233344")).isTrue();
        assertThat(repository.existsByDocument("99988877766")).isFalse();
        assertThat(repository.existsByEmail("exists-test@moneyx.dev")).isTrue();
        assertThat(repository.existsByEmail("other@moneyx.dev")).isFalse();
    }

    @Test
    void databaseRejectsDuplicateDocument() {
        repository.saveAndFlush(new Merchant("Loja A", "55566677788", "dup-a@moneyx.dev"));

        Merchant duplicate = new Merchant("Loja B", "555.666.777-88", "dup-b@moneyx.dev");

        assertThatThrownBy(() -> repository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsDuplicateEmail() {
        repository.saveAndFlush(new Merchant("Loja A", "12312312312", "same@moneyx.dev"));

        Merchant duplicate = new Merchant("Loja B", "32132132132", "SAME@moneyx.dev");

        assertThatThrownBy(() -> repository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
