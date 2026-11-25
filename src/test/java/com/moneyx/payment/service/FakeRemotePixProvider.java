package com.moneyx.payment.service;

import com.moneyx.payment.domain.PixBrCode;
import com.moneyx.payment.domain.PixCharge;
import com.moneyx.payment.provider.PixChargeRequest;
import com.moneyx.payment.provider.PixProvider;
import com.moneyx.payment.provider.PixProviderException;
import com.moneyx.payment.provider.ProviderChargeStatus;
import com.moneyx.payment.provider.ProviderChargeStatus.State;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * PSP externo FALSO e controlável (com o nome "mercadopago", aceito pelas constraints do banco), para testar a
 * conciliação sem rede. Imita o que foi visto no sandbox do MP: a mesma chave devolve a mesma order; cancelar uma
 * order paga é recusado e devolve o estado real.
 */
public class FakeRemotePixProvider implements PixProvider {

    /** Uma order no PSP. amount pode ser alterado para simular divergência. */
    public static final class Order {
        public final String id;
        public final UUID paymentId;
        public volatile long amount;
        public volatile State state = State.OPEN;

        Order(String id, UUID paymentId, long amount) {
            this.id = id;
            this.paymentId = paymentId;
            this.amount = amount;
        }
    }

    private final AtomicInteger sequence = new AtomicInteger();
    public final Map<UUID, Order> ordersByPayment = new ConcurrentHashMap<>();
    public final List<String> sentRequests = new CopyOnWriteArrayList<>();
    public final List<String> calls = new CopyOnWriteArrayList<>();
    /** Configuração do "PSP" que entra no corpo (mudar simula a troca de configuração entre tentativas). */
    public final AtomicReference<String> config = new AtomicReference<>("v1");
    /** Se preenchido, toda chamada falha com esta exceção (PSP fora do ar). */
    public final AtomicReference<PixProviderException> failure = new AtomicReference<>();
    /** Se true, a criação devolve a order sem BR Code (já sendo paga). */
    public volatile boolean createWithoutQrCode;

    public void reset() {
        ordersByPayment.clear();
        sentRequests.clear();
        calls.clear();
        config.set("v1");
        failure.set(null);
        createWithoutQrCode = false;
    }

    @Override
    public String name() {
        return "mercadopago";
    }

    @Override
    public String prepareCharge(PixChargeRequest request) {
        return "{\"ref\":\"" + request.paymentId() + "\",\"amount\":" + request.amount() + ",\"config\":\"" + config.get() + "\"}";
    }

    @Override
    public PixCharge createCharge(PixChargeRequest request) {
        calls.add("create");
        sentRequests.add(request.preparedRequest()); // registra mesmo quando a chamada "falha" (o corpo saiu)
        failIfDown();
        Order order = ordersByPayment.computeIfAbsent(request.paymentId(),
                id -> new Order("ORDTEST" + sequence.incrementAndGet(), id, request.amount()));
        if (createWithoutQrCode || order.state != State.OPEN) {
            throw new PixProviderException(true, "missing_qr_code", null, "no qr", null, statusOf(order));
        }
        String txid = "mpqr" + order.id.toLowerCase();
        return new PixCharge("mercadopago", txid,
                PixBrCode.build("b76aa9c2-2ec4-4110-954e-ebfe34f05b61", "TESTUSER", "Osasco", request.amount(), txid),
                order.id, "PAY" + order.id, null);
    }

    @Override
    public ProviderChargeStatus fetchCharge(String providerOrderId) {
        calls.add("fetch");
        failIfDown();
        return statusOf(byId(providerOrderId));
    }

    @Override
    public ProviderChargeStatus cancelCharge(UUID paymentId, String providerOrderId) {
        calls.add("cancel");
        failIfDown();
        Order order = byId(providerOrderId);
        synchronized (order) {
            if (order.state == State.OPEN) {
                order.state = State.CLOSED;
            }
        }
        return statusOf(order);
    }

    public Order orderOf(UUID paymentId) {
        return ordersByPayment.get(paymentId);
    }

    private Order byId(String id) {
        return ordersByPayment.values().stream().filter(o -> o.id.equals(id)).findFirst()
                .orElseThrow(() -> new PixProviderException(false, "order_not_found", null, "not found", null));
    }

    private void failIfDown() {
        PixProviderException e = failure.get();
        if (e != null) {
            throw e;
        }
    }

    private static ProviderChargeStatus statusOf(Order order) {
        return new ProviderChargeStatus(order.id, "PAY" + order.id, order.state, order.amount,
                order.paymentId.toString(), order.state.name());
    }
}
