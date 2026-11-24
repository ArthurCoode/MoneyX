package com.moneyx.payment.provider.mercadopago;

import com.moneyx.payment.domain.PixBrCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Teste contra o SANDBOX REAL do Mercado Pago. DESLIGADO por padrão (o CI nunca chama serviços externos).
 *
 * Rodar à mão (PowerShell, na raiz do projeto):
 * <pre>
 * $env:MERCADOPAGO_ACCESS_TOKEN = ((Get-Content .env | Where-Object { $_ -match '^MERCADOPAGO_ACCESS_TOKEN=' }) -split '=', 2)[1]
 * .\mvnw.cmd test -Dtest=MercadoPagoSandboxSmokeTest -Dmercadopago.smoke=true
 * </pre>
 * Cria orders de teste de poucos reais (dinheiro fictício) e cancela as que ficam abertas.
 */
@EnabledIfSystemProperty(named = "mercadopago.smoke", matches = "true")
class MercadoPagoSandboxSmokeTest {

    private static MercadoPagoClient client(String testPayerFirstName) {
        String token = System.getenv("MERCADOPAGO_ACCESS_TOKEN");
        return new MercadoPagoClient(new MercadoPagoProperties(URI.create("https://api.mercadopago.com"), token,
                Duration.ofSeconds(5), Duration.ofSeconds(20), "test@testuser.com", testPayerFirstName),
                JsonMapper.builder().build());
    }

    @Test
    void approvedOrderLifecycle() throws InterruptedException {
        MercadoPagoClient client = client("APRO");
        UUID paymentId = UUID.randomUUID();
        CreatePixOrder request = new CreatePixOrder(MercadoPagoClient.creationKeyFor(paymentId), paymentId.toString(),
                499, "MoneyX smoke", Duration.ofMinutes(30));

        Instant before = Instant.now();
        MercadoPagoOrder created = client.createPixOrder(request);
        assertThat(created.status()).isEqualTo("action_required");
        assertThat(created.totalAmount()).isEqualTo(499);
        assertThat(created.externalReference()).isEqualTo(paymentId.toString());
        // O MP calcula o vencimento a partir do momento em que RECEBEU o pedido: um pouco depois do nosso.
        assertThat(created.expiresAt()).isAfterOrEqualTo(before.plus(Duration.ofMinutes(30)).minusSeconds(5));
        assertThat(PixBrCode.txidOf(created.qrCode())).isPresent();

        // Repetição com a MESMA chave e o MESMO corpo: a MESMA order (base da recuperação na 7c).
        MercadoPagoOrder replay = client.createPixOrder(request);
        assertThat(replay.id()).isEqualTo(created.id());

        // APRO: aprovada sozinha, mas o atraso VARIA (medido em 2026-11-24: de 0 s a 46 s). Espera até 2 min.
        MercadoPagoOrder current = created;
        for (int i = 0; i < 40 && !"processed".equals(current.status()); i++) {
            Thread.sleep(3_000);
            current = client.getOrder(created.id());
        }
        assertThat(current.status()).isEqualTo("processed");
        assertThat(current.paymentStatusDetail()).isEqualTo("accredited");
    }

    @Test
    void cancelUnknownAndSameKeyWithAnotherBody() {
        MercadoPagoClient client = client("");
        UUID paymentId = UUID.randomUUID();
        String key = MercadoPagoClient.creationKeyFor(paymentId);
        MercadoPagoOrder created = client.createPixOrder(
                new CreatePixOrder(key, paymentId.toString(), 300, null, Duration.ofMinutes(5)));

        assertThatThrownBy(() -> client.createPixOrder(
                new CreatePixOrder(key, paymentId.toString(), 301, null, Duration.ofMinutes(5))))
                .isInstanceOfSatisfying(MercadoPagoException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("idempotency_key_already_used"));

        MercadoPagoOrder canceled = client.cancelOrder(created.id(), "moneyx-cancel-" + paymentId);
        assertThat(canceled.status()).isEqualTo("canceled");
        assertThatThrownBy(() -> client.cancelOrder(created.id(), "moneyx-cancel-again-" + paymentId))
                .isInstanceOfSatisfying(MercadoPagoException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("order_already_canceled"));

        assertThatThrownBy(() -> client.getOrder("ORD00000000000000000000000000"))
                .isInstanceOfSatisfying(MercadoPagoException.class, ex -> {
                    assertThat(ex.getHttpStatus()).isEqualTo(404);
                    assertThat(ex.getCode()).isEqualTo("order_not_found");
                });
    }
}
