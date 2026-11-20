package com.moneyx.event.repository;

import com.moneyx.event.domain.Event;
import com.moneyx.event.domain.EventType;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.UUID;

/**
 * Condições da listagem de eventos. Mesmo modelo de PaymentSpecifications (cursor keyset + desempate por id).
 */
public final class EventSpecifications {

    public static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt", "id");

    private EventSpecifications() {
    }

    public static Specification<Event> belongsTo(UUID merchantId) {
        return (root, query, cb) -> cb.equal(root.get("merchantId"), merchantId);
    }

    public static Specification<Event> hasType(EventType type) {
        return (root, query, cb) -> cb.equal(root.get("type"), type);
    }

    /** Tudo o que vem DEPOIS de (createdAt, id) na ordem NEWEST_FIRST. */
    public static Specification<Event> after(Instant createdAt, UUID id) {
        return (root, query, cb) -> cb.or(
                cb.lessThan(root.get("createdAt"), createdAt),
                cb.and(
                        cb.equal(root.get("createdAt"), createdAt),
                        cb.lessThan(root.<UUID>get("id"), id)));
    }
}
