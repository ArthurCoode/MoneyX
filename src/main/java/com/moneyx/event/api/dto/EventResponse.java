package com.moneyx.event.api.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.moneyx.event.domain.Event;

import java.time.Instant;
import java.util.UUID;

/**
 * Representação pública de um evento: {id, type, createdAt, data}.
 *
 * data já está gravado em JSON; @JsonRawValue o escreve como está (um objeto), em vez de uma string com aspas.
 * O comerciante deve usar o "id" para descartar eventos repetidos (a entrega será "pelo menos uma vez").
 */
public record EventResponse(UUID id, String type, Instant createdAt, @JsonRawValue String data) {

    public static EventResponse from(Event event) {
        return new EventResponse(event.getId(), event.getType().value(), event.getCreatedAt(), event.getData());
    }
}
