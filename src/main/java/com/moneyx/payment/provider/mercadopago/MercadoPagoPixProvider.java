package com.moneyx.payment.provider.mercadopago;

import com.moneyx.payment.domain.PixBrCode;
import com.moneyx.payment.domain.PixCharge;
import com.moneyx.payment.provider.PixChargeRequest;
import com.moneyx.payment.provider.PixProvider;
import com.moneyx.payment.provider.PixProviderException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cobranças Pix emitidas pelo Mercado Pago (API de Orders). Ativo com moneyx.pix.provider=mercadopago.
 *
 * - Chave de idempotência derivada do pagamento (MercadoPagoClient.creationKeyFor): repetir a criação (cliente ou
 *   recuperação) devolve a MESMA order, comprovado no sandbox.
 * - external_reference = id do pagamento no MoneyX (conciliação).
 * - Descrição: o MP aceita no máximo 150 caracteres (o MoneyX, 255). Vão os 150 primeiros; o MoneyX guarda a completa.
 * - txid: o do próprio BR Code (campo 62.05); se não houver, o id da order.
 * - O vencimento devolvido pelo MP passa a valer (é ele quem aceita ou recusa o Pix).
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
    public PixCharge createCharge(PixChargeRequest request) {
        MercadoPagoOrder order;
        try {
            order = client.createPixOrder(new CreatePixOrder(
                    MercadoPagoClient.creationKeyFor(request.paymentId()),
                    request.paymentId().toString(),
                    request.amount(),
                    truncate(request.description()),
                    request.expiresIn()));
        } catch (MercadoPagoException e) {
            // 4xx normalmente quer dizer "nada foi criado" (o MoneyX pode descartar o pagamento). EXCEÇÃO:
            // idempotency_key_already_used diz o contrário: JÁ EXISTE uma order com esta chave, mas o corpo mudou
            // (ex.: a configuração do MP mudou entre a tentativa original e a repetição). Descartar o pagamento deixaria
            // uma order (talvez paga) sem dono no MoneyX: visto no teste ponta a ponta de 2026-11-24. Fica CREATING e a
            // conciliação (7d) resolve.
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
            // uma order que o MP já começou a aprovar (processing/in_process, com o pagador de teste APRO) não traz mais o
            // qr_code. Fica CREATING; a 7d (conciliação) não pode deixar este pagamento virar EXPIRED se a order for paga.
            throw new PixProviderException(true, "missing_qr_code", null,
                    "Order " + order.id() + " has no qr_code (status " + order.status() + "/" + order.statusDetail() + ")", null);
        }

        String txid;
        try {
            txid = PixBrCode.txidOf(order.qrCode()).orElse(order.id());
        } catch (IllegalArgumentException e) {
            throw new PixProviderException(false, "invalid_qr_code", null, "Order " + order.id() + " has an invalid BR Code", e);
        }
        return new PixCharge(NAME, txid, order.qrCode(), order.id(), order.paymentId(), order.expiresAt());
    }

    private static String truncate(String description) {
        if (description == null || description.length() <= DESCRIPTION_MAX_LENGTH) {
            return description;
        }
        return description.substring(0, DESCRIPTION_MAX_LENGTH);
    }
}
