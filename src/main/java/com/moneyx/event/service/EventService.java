package com.moneyx.event.service;

import com.moneyx.event.domain.Event;
import com.moneyx.event.domain.EventType;
import com.moneyx.event.exception.EventNotFoundException;
import com.moneyx.event.exception.InvalidEventCursorException;
import com.moneyx.event.repository.EventRepository;
import com.moneyx.event.repository.EventSpecifications;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Registro e consulta de eventos.
 *
 * Não conhece os outros módulos: quem chama informa o tipo, o recurso e o retrato (data) a gravar.
 */
@Service
public class EventService {

    private final EventRepository repository;
    private final JsonMapper jsonMapper;

    public EventService(EventRepository repository, JsonMapper jsonMapper) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
    }

    /**
     * Registra um evento DENTRO da transação de quem chama (padrão Outbox).
     *
     * Propagation.MANDATORY: se não houver transação aberta, lança exceção em vez de abrir uma nova.
     * Isso garante que o evento só é gravado junto com a mudança de estado que ele descreve: se a mudança
     * for desfeita, o evento também é; se o evento falhar, a mudança também não acontece.
     *
     * @param data retrato do recurso, serializado com o MESMO JsonMapper da API (o JSON do evento é
     *             idêntico ao que a API devolveria naquele momento)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Event record(UUID merchantId, EventType type, UUID resourceId, Object data, Instant occurredAt) {
        String json = jsonMapper.writeValueAsString(data);
        return repository.save(Event.of(merchantId, type, resourceId, json, occurredAt));
    }

    /** @throws EventNotFoundException evento inexistente ou de outro comerciante */
    @Transactional(readOnly = true)
    public Event get(UUID merchantId, UUID eventId) {
        return repository.findByIdAndMerchantId(eventId, merchantId).orElseThrow(EventNotFoundException::new);
    }

    /**
     * Lista os eventos do comerciante, mais recentes primeiro, com paginação por cursor (limit + 1 para hasMore).
     *
     * @throws InvalidEventCursorException startingAfter não é um evento deste comerciante
     */
    @Transactional(readOnly = true)
    public EventPage list(UUID merchantId, EventListQuery query) {
        Specification<Event> spec = EventSpecifications.belongsTo(merchantId);

        if (query.startingAfter() != null) {
            Event cursor = repository.findByIdAndMerchantId(query.startingAfter(), merchantId)
                    .orElseThrow(InvalidEventCursorException::new);
            spec = spec.and(EventSpecifications.after(cursor.getCreatedAt(), cursor.getId()));
        }
        if (query.type() != null) {
            spec = spec.and(EventSpecifications.hasType(query.type()));
        }

        int limit = query.limit();
        List<Event> rows = repository.findBy(spec, q -> q
                .sortBy(EventSpecifications.NEWEST_FIRST)
                .limit(limit + 1)
                .all());

        boolean hasMore = rows.size() > limit;
        return new EventPage(hasMore ? rows.subList(0, limit) : rows, hasMore);
    }
}
