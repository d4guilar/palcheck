package com.palcheck.integrations.web;

import com.palcheck.access.api.Actor;
import com.palcheck.integrations.application.EventsService;
import com.palcheck.integrations.application.IntegrationOperationsService;
import com.palcheck.integrations.domain.IntegrationOperation;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The Event Explorer's operation and event views, and authorized replay (docs/PHASE_3_IMPLEMENTATION_PLAN.md section 7). */
@RestController
@RequestMapping("/api")
class IntegrationOperationsController {

    private final IntegrationOperationsService operations;
    private final EventsService events;

    IntegrationOperationsController(IntegrationOperationsService operations, EventsService events) {
        this.operations = operations;
        this.events = events;
    }

    @GetMapping("/integration-operations")
    Dtos.PageDto<Dtos.IntegrationOperationDto> list(Actor actor,
            @RequestParam(required = false) String consumer,
            @RequestParam(required = false) IntegrationOperation.Status status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "25") int size) {
        return Dtos.page(operations.list(actor, consumer, status, page, size), Dtos::integrationOperation);
    }

    @GetMapping("/integration-operations/{consumer}/{operationKey}")
    Dtos.IntegrationOperationDto get(Actor actor, @PathVariable String consumer, @PathVariable String operationKey) {
        return Dtos.integrationOperation(operations.get(actor, consumer, operationKey));
    }

    @PostMapping("/integration-operations/{consumer}/{operationKey}/replay")
    Dtos.IntegrationOperationDto replay(Actor actor, @PathVariable String consumer, @PathVariable String operationKey) {
        return Dtos.integrationOperation(operations.replay(actor, consumer, operationKey));
    }

    @GetMapping("/events/{eventId}")
    Dtos.OutboxEventDto event(Actor actor, @PathVariable UUID eventId) {
        return Dtos.outboxEvent(events.get(actor, eventId));
    }
}
