package com.moneyx.payment.repository;

import com.moneyx.payment.domain.Payment;
import com.moneyx.payment.domain.PaymentStatus;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.UUID;

/**
 * Pedaços reutilizáveis da cláusula WHERE da listagem de pagamentos.
 *
 * Cada método devolve UMA condição; o serviço junta apenas as que o cliente pediu (filtros opcionais).
 * Isso evita escrever uma consulta para cada combinação de filtros.
 */
public final class PaymentSpecifications {

    /**
     * Ordem da listagem: mais recentes primeiro; empate de created_at desempatado pelo id.
     * O desempate é obrigatório para a paginação por cursor: sem ele, dois pagamentos criados no mesmo
     * instante poderiam trocar de lugar entre uma página e outra (aparecer duas vezes ou nenhuma).
     */
    public static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt", "id");

    private PaymentSpecifications() {
    }

    /** Isolamento entre comerciantes: SEMPRE presente na listagem. */
    public static Specification<Payment> belongsTo(UUID merchantId) {
        return (root, query, cb) -> cb.equal(root.get("merchantId"), merchantId);
    }

    public static Specification<Payment> hasStatus(PaymentStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Payment> hasExternalReference(String externalReference) {
        return (root, query, cb) -> cb.equal(root.get("externalReference"), externalReference);
    }

    /**
     * Cursor: tudo o que vem DEPOIS do pagamento (createdAt, id) na ordem NEWEST_FIRST.
     *
     * created_at < cursor.createdAt OR (created_at = cursor.createdAt AND id < cursor.id)
     *
     * A comparação de UUID é feita pelo PostgreSQL (mesma regra usada no ORDER BY), por isso a ordem é consistente.
     */
    public static Specification<Payment> after(Instant createdAt, UUID id) {
        return (root, query, cb) -> cb.or(
                cb.lessThan(root.get("createdAt"), createdAt),
                cb.and(
                        cb.equal(root.get("createdAt"), createdAt),
                        cb.lessThan(root.<UUID>get("id"), id)));
    }
}
