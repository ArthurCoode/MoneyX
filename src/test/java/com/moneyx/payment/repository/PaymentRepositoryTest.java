package com.moneyx.payment.repository;

import com.moneyx.merchant.domain.Merchant;
import com.moneyx.merchant.repository.MerchantRepository;
import com.moneyx.payment.domain.Payment;
import com.moneyx.payment.domain.PaymentCurrency;
import com.moneyx.payment.domain.PaymentFixtures;
import com.moneyx.payment.domain.PaymentStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Persistência de Payment contra o PostgreSQL real (rollback ao final de cada teste).
 *
 * Além do mapeamento JPA, prova que as constraints do banco protegem as invariantes mesmo
 * quando alguém escreve direto via SQL, contornando as regras do domínio.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PaymentRepositoryTest {

    // O PostgreSQL guarda microssegundos; truncar evita diferenças de precisão na comparação.
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    private Merchant merchant;

    @BeforeEach
    void setUp() {
        merchant = merchantRepository.saveAndFlush(
                new Merchant("Loja Pagamentos LTDA", "55566677788", "payment-test@moneyx.dev"));
    }

    private Payment savedPayment() {
        Payment payment = paymentRepository.saveAndFlush(
                PaymentFixtures.pending(merchant.getId(), 4990, "Pedido 123", "order-123", null, NOW));
        entityManager.clear(); // força a próxima leitura a vir do banco, e não do cache do JPA
        return payment;
    }

    @Test
    void persistsAndReadsAllFields() {
        Payment saved = savedPayment();

        Payment found = paymentRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getMerchantId()).isEqualTo(merchant.getId());
        assertThat(found.getAmount()).isEqualTo(4990);
        assertThat(found.getCurrency()).isEqualTo(PaymentCurrency.BRL);
        assertThat(found.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(found.getDescription()).isEqualTo("Pedido 123");
        assertThat(found.getExternalReference()).isEqualTo("order-123");
        assertThat(found.getExpiresAt()).isEqualTo(saved.getExpiresAt());
        assertThat(found.getCreatedAt()).isEqualTo(NOW);
        assertThat(found.getPixTxid()).isEqualTo(saved.getPixTxid());
        assertThat(found.getPixCopyPaste()).isEqualTo(saved.getPixCopyPaste());
    }

    @Test
    void persistsStateTransition() {
        Payment payment = paymentRepository.findById(savedPayment().getId()).orElseThrow();

        payment.markPaid(NOW.plusSeconds(60));
        paymentRepository.saveAndFlush(payment);
        entityManager.clear();

        Payment found = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(found.getPaidAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void findsPaymentOnlyForItsOwner() {
        Merchant other = merchantRepository.saveAndFlush(
                new Merchant("Outra Loja", "88877766655", "payment-other@moneyx.dev"));
        Payment payment = savedPayment();

        assertThat(paymentRepository.findByIdAndMerchantId(payment.getId(), merchant.getId())).isPresent();
        assertThat(paymentRepository.findByIdAndMerchantId(payment.getId(), other.getId())).isEmpty();
    }

    // ---------- invariantes garantidas pelo banco ----------

    @Test
    void databaseRejectsPaymentForNonexistentMerchant() {
        Payment orphan = PaymentFixtures.pending(UUID.randomUUID(), 4990, NOW);

        assertThatThrownBy(() -> paymentRepository.saveAndFlush(orphan))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsNonPositiveAmount() {
        UUID id = savedPayment().getId();

        assertThatThrownBy(() -> jdbc.update("UPDATE payments SET amount = 0 WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_payments_amount");
    }

    @Test
    void databaseRejectsPaidStatusWithoutPaidAt() {
        UUID id = savedPayment().getId();

        assertThatThrownBy(() -> jdbc.update("UPDATE payments SET status = 'PAID' WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_payments_paid_at");
    }

    // Um teste por violação: no PostgreSQL, depois que um comando falha, a transação fica "abortada"
    // e todos os comandos seguintes são recusados até o rollback, então a segunda checagem nunca rodaria.

    @Test
    void databaseRejectsUnknownStatus() {
        UUID id = savedPayment().getId();

        assertThatThrownBy(() -> jdbc.update("UPDATE payments SET status = 'REFUNDED' WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_payments_status");
    }

    @Test
    void databaseRejectsUnsupportedCurrency() {
        UUID id = savedPayment().getId();

        assertThatThrownBy(() -> jdbc.update("UPDATE payments SET currency = 'USD' WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_payments_currency");
    }

    @Test
    void databaseRejectsDuplicateTxid() {
        Payment first = savedPayment();
        Payment second = savedPayment();

        assertThatThrownBy(() -> jdbc.update("UPDATE payments SET pix_txid = ? WHERE id = ?",
                first.getPixTxid(), second.getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_payments_pix_txid");
    }

    @Test
    void databaseRejectsMalformedBrCode() {
        UUID id = savedPayment().getId();

        assertThatThrownBy(() -> jdbc.update("UPDATE payments SET pix_copy_paste = 'not a br code' WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_payments_pix_copy_paste");
    }
}
