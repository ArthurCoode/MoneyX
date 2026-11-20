package com.moneyx.event.service;

import com.moneyx.event.domain.Event;

import java.util.List;

/** Uma página da listagem de eventos, mais recentes primeiro. */
public record EventPage(List<Event> events, boolean hasMore) {
}
