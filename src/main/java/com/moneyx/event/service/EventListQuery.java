package com.moneyx.event.service;

import com.moneyx.event.domain.EventType;

import java.util.UUID;

/**
 * Parâmetros da listagem de eventos (paginação por cursor, mesmo contrato da listagem de pagamentos).
 *
 * @param limit         tamanho da página, de 1 a 100 (null usa 10)
 * @param startingAfter id do último evento da página anterior (null = primeira página)
 * @param type          filtro opcional
 */
public record EventListQuery(Integer limit, UUID startingAfter, EventType type) {

    public static final int DEFAULT_LIMIT = 10;
    public static final int MAX_LIMIT = 100;

    public EventListQuery {
        if (limit == null) {
            limit = DEFAULT_LIMIT;
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
    }

    public static EventListQuery firstPage() {
        return new EventListQuery(null, null, null);
    }
}
