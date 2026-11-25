package com.moneyx.payment.provider.mercadopago;

import com.moneyx.payment.domain.PixBrCode;
import com.moneyx.payment.domain.PixCharge;
import com.moneyx.payment.provider.PixChargeRequest;
import com.moneyx.payment.provider.PixProvider;
import com.moneyx.payment.provider.PixProviderException;
import com.moneyx.payment.provider.ProviderChargeStatus;
import com.moneyx.payment.provider.ProviderChargeStatus.State;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.Locale;
import java.util.UUID;

/**
 * Cobranças Pix emitidas pelo Mercado Pago (API de Orders). Ativo com moneyx.pix.provider=mercadopago.
 *
 * - Chave de idempotência derivada do pagamento (MercadoPagoClient.creationKeyFor): repetir a criação (cliente ou
 *   recuperação) devolve a MESMA order, comprovado no sandbox.
 * - O corpo é montado UMA vez (prepareCharge), gravado no pagamento e reenviado igual: o MP compara a repetição byte a
 *   byte, e uma mudança de configuração no meio (ex.: o pagador de teste) gerava 409 idempotency_key_already_used.
 * - external_reference = id do pagamento no MoneyX (conciliação).
 * - Descrição: o MP aceita no máximo 150 caracteres (o MoneyX, 255). Vão os 150 primeiros; o MoneyX guarda a completa.
 * - txid: o do próprio BR Code (campo 62.05); se não houver, o id da order.
 * - O vencimento devolvido pelo MP passa a valer (é ele quem aceita ou recusa o Pix).
 *
 * Status da order → estado para o MoneyX (statusOf): processed = PAID; action_required/created = OPEN;
 * processing = IN_PROGRESS; canceled/failed = CLOSED (EXPIRED se status_detail = expired); qualquer outro
 * (ex.: refunded) = UNKNOWN.
 *
 * Sem o token (MERCADOPAGO_ACCESS_TOKEN) a aplicação não sobe com este provedor ligado.
 */
@Component
@ConditionalOnProperty(name = "moneyx.pix.provider", havingValue = "mercadopago")
public class MercadoPagoPixProvider implements PixProvider {

    public static final String NAME = "mercadopago";
    static final int DESCRIPTION_MAX_LENGTH = 150;
    static final String IDEMPOTENCY_CONFLICT = "idempotency_key_already_used";

    private final MercadoPagoClient client;

    /** O construtor que o Spring usa (o outro é só para testes; com dois, o Spring exige a indicação). */
    @Autowired
    public MercadoPagoPixProvider(MercadoPagoProperties properties, JsonMapper json) {
        this(new MercadoPagoClient(properties, json));
    }

    MercadoPagoPixProvider(MercadoPagoClient client) {
        this.client = client;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String prepareCharge(PixChargeRequest request) {
        return client.orderBody(orderOf(request));
    }

    @Override
    public PixCharge createCharge(PixChargeRequest request) {
        MercadoPagoOrder order;
        try {
            String key = MercadoPagoClient.creationKeyFor(request.paymentId());
            order = request.preparedRequest() != null
                    ? client.createPixOrder(key, request.preparedRequest())
                    // pagamento criado antes da V14 (sem o corpo gravado): monta agora
                    : client.createPixOrder(orderOf(request));
        } catch (MercadoPagoException e) {
            // 4xx normalmente quer dizer "nada foi criado" (o MoneyX pode descartar o pagamento). EXCEÇÃO:
            // idempotency_key_already_used diz o contrário: JÁ EXISTE uma order com esta chave, mas o corpo mudou.
            // Descartar o pagamento deixaria uma order (talvez paga) sem dono no MoneyX: visto no teste ponta a ponta de
            // 2026-11-24. Com o corpo gravado (V14) não deveria mais acontecer; se acontecer, fica CREATING.
            boolean orderMayExist = IDEMPOTENCY_CONFLICT.equals(e.getCode());
            throw new PixProviderException(e.isRetryable() || orderMayExist, e.getCode(), e.getRetryAfter(), e.getMessage(), e);
        }

        // Conferências de dinheiro: a order tem de ser EXATAMENTE a que pedimos.
        if (order.totalAmount() != request.amount()) {
            throw new PixProviderException(false, "amount_mismatch", null,
                    "Order " + order.id() + " has amount " + order.totalAmount() + ", expected " + request.amount(), null);
        }
        if (!request.paymentId().toString().equals(order.externalReference())) {
            throw new PixProviderException(false, "reference_mismatch", null,
                    "Order " + order.id() + " belongs to another external reference", null);
        }
        if (order.qrCode() == null || order.qrCode().isBlank()) {
            // A order existe, mas sem BR Code. Visto no sandbox (2026-11-24): a REPETIÇÃO devolve o estado ATUAL da order, e
            // uma order que o MP já começou a aprovar (processing, com o pagador de teste APRO) não traz mais o
            // qr_code. Fica CREATING, mas com o estado da order: se ela foi paga, o MoneyX marca PAID (nunca EXPIRED).
            throw new PixProviderException(true, "missing_qr_code", null,
                    "Order " + order.id() + " has no qr_code (status " + order.status() + "/" + order.statusDetail() + ")",
                    null, statusOf(order));
        }

        String txid;
        try {
            txid = PixBrCode.txidOf(order.qrCode()).orElse(order.id());
        } catch (IllegalArgumentException e) {
            throw new PixProviderException(false, "invalid_qr_code", null, "Order " + order.id() + " has an invalid BR Code", e);
        }
        return new PixCharge(NAME, txid, order.qrCode(), order.id(), order.paymentId(), order.expiresAt());
    }

    @Override
    public ProviderChargeStatus fetchCharge(String providerOrderId) {
        try {
            return statusOf(client.getOrder(providerOrderId));
        } catch (MercadoPagoException e) {
            throw translate(e);
        }
    }

    /**
     * Cancela no MP. Se o MP recusar com 409 (já cancelada, expirada, paga ou sendo paga), consulta a order e devolve o
     * estado real: é ele que decide, não o código do erro.
     */
    @Override
    public ProviderChargeStatus cancelCharge(UUID paymentId, String providerOrderId) {
        try {
            return statusOf(client.cancelOrder(providerOrderId, cancelKeyFor(paymentId)));
        } catch (MercadoPagoException e) {
            if (e.getHttpStatus() != 409) {
                throw translate(e);
            }
        }
        return fetchCharge(providerOrderId);
    }

    /** Chave de idempotência do cancelamento: repetir o cancelamento do mesmo pagamento é a mesma operação. */
    static String cancelKeyFor(UUID paymentId) {
        return "moneyx-cancel-" + paymentId;
    }

    static ProviderChargeStatus statusOf(MercadoPagoOrder order) {
        String status = order.status() == null ? "" : order.status().toLowerCase(Locale.ROOT);
        boolean expired = "expired".equalsIgnoreCase(order.statusDetail());
        State state = switch (status) {
            case "processed" -> State.PAID;
            case "action_required", "created" -> State.OPEN;
            case "processing" -> State.IN_PROGRESS;
            // O MP expira a order com status canceled (ou failed) e status_detail expired. Visto no sandbox em 2026-11-25:
            // uma order com prazo de 5 min ficou canceled/expired ~5 s depois de criada.
            case "expired" -> State.EXPIRED;
            case "canceled", "failed" -> expired ? State.EXPIRED : State.CLOSED;
            default -> State.UNKNOWN;
        };
        return new ProviderChargeStatus(order.id(), order.paymentId(), state, order.totalAmount(),
                order.externalReference(), order.status() + "/" + order.statusDetail());
    }

    private static PixProviderException translate(MercadoPagoException e) {
        return new PixProviderException(e.isRetryable(), e.getCode(), e.getRetryAfter(), e.getMessage(), e);
    }

    private static CreatePixOrder orderOf(PixChargeRequest request) {
        return new CreatePixOrder(
                MercadoPagoClient.creationKeyFor(request.paymentId()),
                request.paymentId().toString(),
                request.amount(),
                truncate(request.description()),
                request.expiresIn());
    }

    private static String truncate(String description) {
        if (description == null || description.length() <= DESCRIPTION_MAX_LENGTH) {
            return description;
        }
        return description.substring(0, DESCRIPTION_MAX_LENGTH);
    }
}
