package com.moneyx.event.api.dto;

import com.moneyx.event.service.EventPage;

import java.util.List;

/** Envelope da listagem: {"data": [...], "hasMore": true}. */
public record EventListResponse(List<EventResponse> data, boolean hasMore) {

    public static EventListResponse from(EventPage page) {
        return new EventListResponse(page.events().stream().map(EventResponse::from).toList(), page.hasMore());
    }
}
