package com.moneyx.event.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Grava o EventType pelo valor público ("payment.paid") e não pelo nome Java ("PAYMENT_PAID"),
 * que é o que @Enumerated(STRING) faria. Assim o banco guarda exatamente o que a API mostra.
 */
@Converter
public class EventTypeConverter implements AttributeConverter<EventType, String> {

    @Override
    public String convertToDatabaseColumn(EventType type) {
        return type == null ? null : type.value();
    }

    @Override
    public EventType convertToEntityAttribute(String value) {
        return value == null ? null : EventType.fromValue(value);
    }
}
