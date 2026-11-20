package com.moneyx.webhook.repository;

import com.moneyx.webhook.domain.WebhookDelivery;
import com.moneyx.webhook.domain.WebhookDeliveryStatus;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.UUID;

/** Condições da listagem de entregas (mesmo modelo de PaymentSpecifications: cursor keyset + desempate por id). */
public final class WebhookDeliverySpecifications {

    public static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt", "id");

    private WebhookDeliverySpecifications() {
    }

    /** Isolamento: SEMPRE pelo comerciante E pelo endpoint. */
    public static Specification<WebhookDelivery> ofEndpoint(UUID merchantId, UUID endpointId) {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("merchantId"), merchantId),
                cb.equal(root.get("endpointId"), endpointId));
    }

    public static Specification<WebhookDelivery> hasStatus(WebhookDeliveryStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<WebhookDelivery> after(Instant createdAt, UUID id) {
        return (root, query, cb) -> cb.or(
                cb.lessThan(root.get("createdAt"), createdAt),
                cb.and(
                        cb.equal(root.get("createdAt"), createdAt),
                        cb.lessThan(root.<UUID>get("id"), id)));
    }
}
