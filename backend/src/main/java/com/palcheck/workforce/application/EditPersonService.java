package com.palcheck.workforce.application;

import com.palcheck.access.api.Actor;
import com.palcheck.access.api.Authorization;
import com.palcheck.access.api.Permission;
import com.palcheck.platform.idempotency.Fingerprint;
import com.palcheck.platform.idempotency.IdempotencyService;
import com.palcheck.shared.ConflictException;
import com.palcheck.shared.FieldViolation;
import com.palcheck.shared.NotFoundException;
import com.palcheck.shared.ValidationException;
import com.palcheck.workforce.domain.FieldChange;
import com.palcheck.workforce.domain.Patch;
import com.palcheck.workforce.domain.Person;
import com.palcheck.workforce.domain.PersonPatch;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Edits the identity fields of a Person with optimistic concurrency: the caller states the version it read, and a
 * stale version is a conflict instead of a silent overwrite. The change, its audit entry and (when a key is supplied)
 * the idempotency receipt commit together.
 */
@Service
public class EditPersonService {

    static final String OPERATION = "person.update";
    private static final Logger LOG = LoggerFactory.getLogger(EditPersonService.class);
    static final String STALE = "This record changed since you opened it. Review the latest version before saving.";

    private final Authorization authorization;
    private final IdempotencyService idempotency;
    private final PersonRepository people;
    private final WorkforceAuditor auditor;
    private final Clock clock;

    EditPersonService(Authorization authorization, IdempotencyService idempotency, PersonRepository people,
            WorkforceAuditor auditor, Clock clock) {
        this.authorization = authorization;
        this.idempotency = idempotency;
        this.people = people;
        this.auditor = auditor;
        this.clock = clock;
    }

    /** @param idempotencyKey optional; when present a repeat of the same request returns the original outcome */
    @Transactional
    public Result update(Actor actor, UUID personId, long expectedVersion, PersonPatch patch, String reason,
            String idempotencyKey) {
        authorization.require(actor, Permission.EDIT_PERSON);

        if (idempotencyKey != null) {
            var reservation = idempotency.reserve(actor.tenantId(), actor.principalId(), OPERATION, idempotencyKey,
                    fingerprint(personId, expectedVersion, patch, reason).sha256());
            if (reservation instanceof IdempotencyService.Reservation.Replay replay) {
                Person current = people.find(actor.tenantId(), replay.resultId())
                        .orElseThrow(() -> new NotFoundException("The person was not found."));
                LOG.atInfo().addKeyValue("operation", OPERATION).addKeyValue("result", "replayed")
                        .addKeyValue("personId", personId).log("workforce operation replayed");
                return new Result(current, false, true);
            }
        }

        Person current = people.find(actor.tenantId(), personId)
                .orElseThrow(() -> new NotFoundException("The person was not found."));
        if (current.version() != expectedVersion) {
            throw new ConflictException(STALE, current.version());
        }

        String cleanReason = Reasons.clean(reason);
        var applied = patch.applyTo(current.data());
        List<FieldViolation> violations = new ArrayList<>(applied.violations());
        if (violations.isEmpty()) {
            violations.addAll(applied.data().validate(""));
        }
        Reasons.validate(cleanReason, violations);
        List<FieldChange> changes = current.data().diff(applied.data());
        if (violations.isEmpty() && changes.stream().anyMatch(c -> c.field().equals("workEmail"))
                && people.workEmailInUse(actor.tenantId(), applied.data().workEmail(), personId)) {
            violations.add(new FieldViolation("workEmail", "ALREADY_IN_USE",
                    "This work email is already used by another person."));
        }
        if (!violations.isEmpty()) {
            throw new ValidationException(violations);
        }

        if (changes.isEmpty()) {
            // Nothing to store: no version bump and no audit entry. The request still succeeded.
            completeReceipt(actor, idempotencyKey, personId);
            LOG.atInfo().addKeyValue("operation", OPERATION).addKeyValue("result", "unchanged")
                    .addKeyValue("personId", personId).log("workforce update changed nothing");
            return new Result(current, false, false);
        }

        Instant now = Instant.now(clock);
        Person next = new Person(current.id(), current.tenantId(), applied.data(), current.version(),
                current.createdAt(), now);
        boolean stored;
        try {
            stored = people.update(next, expectedVersion);
        } catch (WorkEmailAlreadyInUseException e) {
            throw new ValidationException(new FieldViolation("workEmail", "ALREADY_IN_USE",
                    "This work email is already used by another person."));
        }
        if (!stored) {
            // Another request committed between our read and our write.
            long now2 = people.currentVersion(actor.tenantId(), personId)
                    .orElseThrow(() -> new NotFoundException("The person was not found."));
            throw new ConflictException(STALE, now2);
        }

        auditor.updated(actor, OPERATION, UUID.randomUUID(), WorkforceAuditor.PERSON, personId, expectedVersion + 1,
                personId, changes, cleanReason);
        completeReceipt(actor, idempotencyKey, personId);
        LOG.atInfo().addKeyValue("operation", OPERATION).addKeyValue("result", "success")
                .addKeyValue("personId", personId).addKeyValue("changedFields", changes.size())
                .log("workforce record updated");
        Person saved = people.find(actor.tenantId(), personId).orElseThrow();
        return new Result(saved, true, false);
    }

    private void completeReceipt(Actor actor, String idempotencyKey, UUID personId) {
        if (idempotencyKey != null) {
            idempotency.complete(actor.tenantId(), actor.principalId(), OPERATION, idempotencyKey, "PERSON", personId);
        }
    }

    private static Fingerprint fingerprint(UUID personId, long expectedVersion, PersonPatch patch, String reason) {
        return Fingerprint.of(OPERATION).part("personId", personId).part("expectedVersion", expectedVersion)
                .part("firstName", render(patch.firstName())).part("middleName", render(patch.middleName()))
                .part("lastName", render(patch.lastName())).part("preferredName", render(patch.preferredName()))
                .part("workEmail", render(patch.workEmail())).part("locale", render(patch.locale()))
                .part("reason", Reasons.clean(reason));
    }

    /** Distinguishes "not sent", "clear" and a value, so a patch that clears a field differs from one that omits it. */
    static String render(Patch<?> patch) {
        if (patch.isAbsent()) {
            return "absent";
        }
        return patch.isClear() ? "clear" : "set:" + patch.value();
    }

    /** The stored person, whether anything changed, and whether this was an idempotent replay. */
    public record Result(Person person, boolean changed, boolean replayed) {
    }
}
