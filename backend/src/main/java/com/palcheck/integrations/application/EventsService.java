package com.palcheck.integrations.application;

import com.palcheck.access.api.Actor;
import com.palcheck.access.api.Authorization;
import com.palcheck.access.api.Permission;
import com.palcheck.messaging.api.OutboxEventReader;
import com.palcheck.messaging.api.OutboxEventView;
import com.palcheck.shared.NotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** One outbox event's envelope and publication status for the Event Explorer (docs/PHASE_3_IMPLEMENTATION_PLAN.md section 7). */
@Service
public class EventsService {

    private final Authorization authorization;
    private final OutboxEventReader events;

    EventsService(Authorization authorization, OutboxEventReader events) {
        this.authorization = authorization;
        this.events = events;
    }

    public OutboxEventView get(Actor actor, UUID eventId) {
        authorization.require(actor, Permission.VIEW_EVENTS);
        return events.find(actor.tenantId(), eventId).orElseThrow(() -> new NotFoundException("Unknown event."));
    }
}
