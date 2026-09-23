package com.palcheck.platform.idempotency;

import com.palcheck.shared.IdempotencyConflictException;
import com.palcheck.shared.TenantId;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Request idempotency for write commands with a guaranteed retry window of at least 24 hours.
 *
 * <p>Scope of a key: tenant + authenticated principal + operation + key. The receipt row is inserted <em>inside the
 * business transaction</em> with {@code ON CONFLICT DO NOTHING}. Consequences: a rolled-back command leaves no receipt;
 * two concurrent requests with the same key serialize on the primary key, and the second one waits for the first to
 * finish, then sees its committed result (or proceeds if the first rolled back). No "in progress" state is needed.
 * The original successful write completes its receipt with a full window; replays do not extend it.
 * After that deadline the receipt may be removed, but the business effect remains. Its removal does not
 * establish that a repeated action is safe or equivalent to the original operation.
 *
 * <p>This is command idempotency only. It is not a general framework and it does not deduplicate business effects
 * downstream (ADR-0019 decision 3: that needs a stable business operation identity, designed with the first
 * integration).
 */
@Service
public class IdempotencyService {

    /** The outcome of reserving a key. */
    public sealed interface Reservation {

        /** No retained receipt: perform normal validation/write, then complete. This is not proof of no prior effect. */
        record Fresh() implements Reservation {
        }

        /** The same request was already processed; the caller returns the referenced result instead of repeating it. */
        record Replay(String resultType, UUID resultId) implements Reservation {
        }
    }

    private final JdbcClient jdbc;
    private final Clock clock;
    private final Duration guaranteedRetryWindow;

    IdempotencyService(JdbcClient jdbc, Clock clock,
            @Value("${palcheck.idempotency.ttl:PT24H}") Duration guaranteedRetryWindow) {
        if (guaranteedRetryWindow.compareTo(Duration.ofHours(24)) < 0) {
            throw new IllegalArgumentException("The guaranteed retry window must be at least 24 hours");
        }
        this.jdbc = jdbc;
        this.clock = clock;
        this.guaranteedRetryWindow = guaranteedRetryWindow;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Reservation reserve(TenantId tenantId, UUID principalId, String operation, String key, String requestHash) {
        OffsetDateTime now = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);

        // Past the guaranteed window, receipt availability is not promised. Removing the receipt does not
        // undo the business effect or tell the caller that repeating it is safe. Normal write rules still apply.
        jdbc.sql("""
                        DELETE FROM platform.idempotency_record
                         WHERE tenant_id = :tenant AND principal_id = :principal AND operation = :operation
                           AND idempotency_key = :key AND expires_at < :now
                        """)
                .param("tenant", tenantId.value()).param("principal", principalId).param("operation", operation)
                .param("key", key).param("now", now).update();

        int inserted = jdbc.sql("""
                        INSERT INTO platform.idempotency_record
                            (tenant_id, principal_id, operation, idempotency_key, request_hash, created_at, expires_at)
                        VALUES (:tenant, :principal, :operation, :key, :hash, :now, :expires)
                        ON CONFLICT (tenant_id, principal_id, operation, idempotency_key) DO NOTHING
                        """)
                .param("tenant", tenantId.value()).param("principal", principalId).param("operation", operation)
                .param("key", key).param("hash", requestHash).param("now", now)
                .param("expires", now.plus(guaranteedRetryWindow))
                .update();
        if (inserted == 1) {
            return new Reservation.Fresh();
        }

        var existing = jdbc.sql("""
                        SELECT request_hash, result_type, result_id FROM platform.idempotency_record
                         WHERE tenant_id = :tenant AND principal_id = :principal AND operation = :operation
                           AND idempotency_key = :key
                        """)
                .param("tenant", tenantId.value()).param("principal", principalId).param("operation", operation)
                .param("key", key)
                .query((rs, n) -> new Object[] {rs.getString("request_hash"), rs.getString("result_type"),
                        rs.getObject("result_id", UUID.class)})
                .single();
        if (!requestHash.equals(existing[0])) {
            throw new IdempotencyConflictException(
                    "This idempotency key was already used for a different request. Use a new key for a new request.");
        }
        if (existing[1] == null || existing[2] == null) {
            // A committed receipt always has a result (it is set before commit); anything else is a defect.
            throw new IllegalStateException("Idempotency receipt without a result");
        }
        return new Reservation.Replay((String) existing[1], (UUID) existing[2]);
    }

    /** Records the original result and full retry window at write completion, in the same transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void complete(TenantId tenantId, UUID principalId, String operation, String key, String resultType,
            UUID resultId) {
        jdbc.sql("""
                        UPDATE platform.idempotency_record SET result_type = :type, result_id = :id,
                            expires_at = GREATEST(expires_at, :guaranteedUntil)
                         WHERE tenant_id = :tenant AND principal_id = :principal AND operation = :operation
                           AND idempotency_key = :key
                        """)
                .param("guaranteedUntil", OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC)
                        .plus(guaranteedRetryWindow))
                .param("type", resultType).param("id", resultId).param("tenant", tenantId.value())
                .param("principal", principalId).param("operation", operation).param("key", key).update();
    }

    /** Removes receipts strictly past their guaranteed window, never business records. Multiple instances may run it. */
    @Transactional
    public int purgeExpired() {
        return jdbc.sql("DELETE FROM platform.idempotency_record WHERE expires_at < :now")
                .param("now", OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC)).update();
    }
}
