package com.palcheck.messaging.api;

import com.palcheck.shared.TenantId;
import java.util.Optional;
import java.util.UUID;

/**
 * Read access to one outbox event for the Event Explorer. Like {@code audit.api.AuditHistory}, this does not
 * authorize: {@code messaging} depends on no domain module (ADR-0018, ADR-0026), so the calling application
 * facade — {@code integrations.application}, which does depend on {@code access.api} — checks {@code VIEW_EVENTS}
 * before calling.
 */
public interface OutboxEventReader {

    Optional<OutboxEventView> find(TenantId tenantId, UUID eventId);
}
