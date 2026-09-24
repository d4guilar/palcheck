package com.palcheck.messaging.api;

/**
 * Durably records an integration-relevant fact inside the caller's own transaction (ADR-0026): if this call
 * succeeds, the event survives the transaction independent of whether a broker is reachable at that moment. Requires
 * an existing transaction ({@code Propagation.MANDATORY}) — the same shape as {@code audit.api.AuditRecorder} — so
 * an outbox row can never be written outside the business change it describes.
 */
public interface OutboxWriter {

    void append(OutboxEventDraft draft);
}
