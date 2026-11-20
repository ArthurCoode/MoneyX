package com.moneyx.event.api.dto;

import com.moneyx.event.service.EventPage;
import com.moneyx.event.service.EventView;

import java.util.List;

/** Envelope da listagem: {"data": [...], "hasMore": true}. */
public record EventListResponse(List<EventView> data, boolean hasMore) {

    public static EventListResponse from(EventPage page) {
        return new EventListResponse(page.events().stream().map(EventView::from).toList(), page.hasMore());
    }
}
