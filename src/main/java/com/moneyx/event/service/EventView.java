package com.moneyx.event.service;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.moneyx.event.domain.Event;

import java.time.Instant;
import java.util.UUID;

/**
 * Representação pública de um evento: {id, type, createdAt, data} (o CONTRATO com o comerciante).
 *
 * Usada pela API (GET /v1/events) e como CORPO dos webhooks, por isso fica no service e não na api:
 * uma única classe garante que o comerciante recebe por webhook exatamente o que a API devolveria.
 *
 * data já está gravado em JSON; @JsonRawValue o escreve como está (um objeto), em vez de uma string com aspas.
 * O comerciante deve usar o "id" para descartar eventos repetidos (a entrega será "pelo menos uma vez").
 */
public record EventView(UUID id, String type, Instant createdAt, @io.swagger.v3.oas.annotations.media.Schema(types = {"object"}, description = "Retrato do recurso no momento do evento (objeto JSON).") @JsonRawValue String data) {

    public static EventView from(Event event) {
        return new EventView(event.getId(), event.getType().value(), event.getCreatedAt(), event.getData());
    }
}
