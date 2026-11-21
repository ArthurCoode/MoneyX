package com.moneyx.webhook.service;

import com.moneyx.event.service.EventRetentionService;
import com.moneyx.merchant.domain.Merchant;
import com.moneyx.merchant.repository.MerchantRepository;
import com.moneyx.support.MutableClock;
import com.moneyx.support.TestClockConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Retenção de eventos e entregas de webhook (30 dias), com os serviços reais e o PostgreSQL.
 *
 * Relógio em 2020 (convenção dos testes de jobs): os jobs agem na tabela inteira; com o corte em 2020, só os dados
 * criados por este teste podem ser apagados. Eventos e entregas são inseridos por SQL para controlar created_at.
 */
@SpringBootTest
@Import(TestClockConfig.class)
// Um cursor que não avança faria o job girar para sempre: com o limite, isso vira FALHA em vez de travar a suíte.
// SEPARATE_THREAD é necessário: no modo padrão o JUnit só INTERROMPE a thread e espera o método voltar, e o laço
// (com chamadas JDBC) ignora a interrupção; a suíte travou assim. Aqui a falha é declarada quando o tempo estoura.
@Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class DataRetentionIntegrationTest {

    private static final Instant NOW = Instant.parse("2020-03-01T12:00:00Z");
    private static final Instant CUTOFF = NOW.minus(Duration.ofDays(30));

    @Autowired
    private EventRetentionService eventRetention;

    @Autowired
    private WebhookDeliveryRetentionService deliveryRetention;

    @Autowired
    private WebhookEndpointService endpointService;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private MutableClock clock;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID merchantId;
    private UUID endpointId;

    @BeforeEach
    void setUp() {
        clock.set(NOW);
        String document = String.valueOf(ThreadLocalRandom.current().nextLong(10_000_000_000_000L, 99_999_999_999_999L));
        merchantId = merchantRepository.saveAndFlush(
                new Merchant("Retention Test LTDA", document, "retention-" + UUID.randomUUID() + "@moneyx.dev")).getId();
        endpointId = endpointService.create(merchantId, UUID.randomUUID(), "https://loja.com/webhooks/moneyx", null)
                .endpoint().getId();
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM webhook_deliveries WHERE merchant_id = ?", merchantId);
        jdbc.update("DELETE FROM webhook_endpoint_history WHERE merchant_id = ?", merchantId);
        jdbc.update("DELETE FROM webhook_endpoints WHERE merchant_id = ?", merchantId);
        jdbc.update("DELETE FROM events WHERE merchant_id = ?", merchantId);
        jdbc.update("DELETE FROM merchants WHERE id = ?", merchantId);
    }

    // ---------- eventos ----------

    @Test
    void eventsOlderThanTheRetentionPeriodAreDeletedAndTheRestIsKept() {
        UUID justTooOld = event(CUTOFF.minusNanos(1_000));
        UUID exactlyAtTheCutoff = event(CUTOFF);
        UUID recent = event(NOW.minus(Duration.ofDays(1)));

        assertThat(eventRetention.purgeExpired()).isEqualTo(1);

        assertThat(existingEvents()).containsExactlyInAnyOrder(exactlyAtTheCutoff, recent).doesNotContain(justTooOld);
    }

    @Test
    void eventStillReferencedByADeliveryIsKeptUntilTheDeliveryIsGone() {
        UUID event = event(CUTOFF.minus(Duration.ofDays(5)));
        delivery(event, "SUCCEEDED", CUTOFF.minus(Duration.ofDays(5)));

        // Job de eventos ANTES do de entregas: o evento ainda é referenciado e fica.
        assertThat(eventRetention.purgeExpired()).isZero();
        assertThat(existingEvents()).containsExactly(event);

        // Entregas limpas, a próxima execução do job de eventos o apaga: os dois jobs se acertam sem ordem fixa.
        assertThat(deliveryRetention.purgeExpired()).isEqualTo(1);
        assertThat(eventRetention.purgeExpired()).isEqualTo(1);
        assertThat(existingEvents()).isEmpty();
    }

    @Test
    void eventWithAPendingDeliveryIsNeverDeleted() {
        UUID event = event(CUTOFF.minus(Duration.ofDays(60)));
        delivery(event, "PENDING", CUTOFF.minus(Duration.ofDays(60)));

        deliveryRetention.purgeExpired();
        eventRetention.purgeExpired();

        assertThat(existingEvents()).containsExactly(event);
        assertThat(deliveryStatuses()).containsExactly("PENDING");
    }

    @Test
    void referencedEventsAtTheFrontDoNotStopTheJobFromReachingNewerDeletableOnes() {
        // Mais candidatos referenciados do que cabem num lote, todos ANTES dos apagáveis. Sem cursor, o job releria
        // sempre os mesmos 500 e pararia sem apagar nada.
        int referenced = EventRetentionService.BATCH_SIZE + 1;
        Instant base = CUTOFF.minus(Duration.ofDays(20));
        jdbc.update("""
                INSERT INTO events (id, merchant_id, type, resource_id, data, created_at)
                SELECT gen_random_uuid(), ?, 'payment.created', gen_random_uuid(), '{}'::jsonb, ?::timestamptz + make_interval(secs => n)
                FROM generate_series(1, ?) AS n""", merchantId, Timestamp.from(base), referenced);
        jdbc.update("""
                INSERT INTO webhook_deliveries (id, event_id, endpoint_id, merchant_id, status, attempts, next_attempt_at,
                                                created_at, version)
                SELECT gen_random_uuid(), e.id, ?, e.merchant_id, 'PENDING', 0, e.created_at, e.created_at, 0
                FROM events e WHERE e.merchant_id = ?""", endpointId, merchantId);
        UUID deletable1 = event(CUTOFF.minus(Duration.ofDays(2)));
        UUID deletable2 = event(CUTOFF.minus(Duration.ofDays(1)));

        assertThat(eventRetention.purgeExpired()).isEqualTo(2);

        assertThat(existingEvents()).hasSize(referenced).doesNotContain(deletable1, deletable2);
    }

    @Test
    void manyDeletableEventsAreRemovedAcrossSeveralBatches() {
        int count = EventRetentionService.BATCH_SIZE * 2 + 7;
        jdbc.update("""
                INSERT INTO events (id, merchant_id, type, resource_id, data, created_at)
                SELECT gen_random_uuid(), ?, 'payment.created', gen_random_uuid(), '{}'::jsonb, ?::timestamptz - make_interval(secs => n)
                FROM generate_series(1, ?) AS n""", merchantId, Timestamp.from(CUTOFF), count);

        assertThat(eventRetention.purgeExpired()).isEqualTo(count);
        assertThat(existingEvents()).isEmpty();
    }

    // ---------- entregas ----------

    @Test
    void onlyFinishedDeliveriesOlderThanTheRetentionPeriodAreDeleted() {
        Instant old = CUTOFF.minus(Duration.ofDays(1));
        delivery(event(old), "SUCCEEDED", old);
        delivery(event(old), "FAILED", old);
        delivery(event(old), "CANCELED", old);
        delivery(event(old), "PENDING", old);                        // pendente: nunca
        delivery(event(CUTOFF), "SUCCEEDED", CUTOFF);                // exatamente no corte: fica
        delivery(event(NOW), "SUCCEEDED", NOW);                      // recente: fica

        assertThat(deliveryRetention.purgeExpired()).isEqualTo(3);

        assertThat(deliveryStatuses()).containsExactlyInAnyOrder("PENDING", "SUCCEEDED", "SUCCEEDED");
    }

    @Test
    void retentionShorterThanTheMinimumIsRejected() {
        assertThatThrownBy(() -> new EventRetentionService(null, List.of(), null, clock, Duration.ofHours(23)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 1 day");
    }

    // ---------- auxiliares ----------

    private UUID event(Instant createdAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO events (id, merchant_id, type, resource_id, data, created_at)
                VALUES (?, ?, 'payment.created', ?, '{}'::jsonb, ?)""",
                id, merchantId, UUID.randomUUID(), Timestamp.from(createdAt));
        return id;
    }

    private void delivery(UUID eventId, String status, Instant createdAt) {
        jdbc.update("""
                INSERT INTO webhook_deliveries (id, event_id, endpoint_id, merchant_id, status, attempts, next_attempt_at,
                                                delivered_at, created_at, version)
                VALUES (?, ?, ?, ?, ?, 1, ?, ?, ?, 0)""",
                UUID.randomUUID(), eventId, endpointId, merchantId, status, Timestamp.from(createdAt),
                status.equals("SUCCEEDED") ? Timestamp.from(createdAt) : null, Timestamp.from(createdAt));
    }

    private List<UUID> existingEvents() {
        return jdbc.queryForList("SELECT id FROM events WHERE merchant_id = ?", UUID.class, merchantId);
    }

    private List<String> deliveryStatuses() {
        return jdbc.queryForList("SELECT status FROM webhook_deliveries WHERE merchant_id = ?", String.class, merchantId);
    }
}
