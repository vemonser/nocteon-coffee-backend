package com.nocteon.nocteon_api.audit.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

import com.nocteon.nocteon_api.audit.enums.AuditAction;
import com.nocteon.nocteon_api.common.security.ActorResolver;
import com.nocteon.nocteon_api.common.security.AuditActor;

import lombok.RequiredArgsConstructor;

/**
 * The three dashboard writes that every manageable domain shares — create, update,
 * delete — written the same way, from one place.
 *
 * <p>The dashboard manages a dozen small catalogues (promo codes, coffee-data
 * lookups, journal posts and categories, shipping zones, product tags and options)
 * that have no common base class. Copying the "resolve the actor, log, diff the
 * fields" sequence into each of them is how one of them ends up recording
 * {@code system} as an e-mail, or an {@code Instant} the serialiser cannot write.
 * They call this instead, and the entity type travels in the payload.
 *
 * <p>Every row joins the caller's transaction ({@code AuditLogService} uses default
 * propagation): a change that rolls back leaves no row claiming it happened. Reads
 * write nothing.
 */
@Component
@RequiredArgsConstructor
public class DomainAuditRecorder {

    private final AuditLogService auditLogService;

    /**
     * The row for a row that now exists. {@code newValues} is the entity's state
     * after the save — small, JSON-friendly, no secrets.
     */
    public void created(AuditAction action, String entityType, Long entityId, String entitySlug,
            Map<String, Object> newValues) {
        AuditActor actor = ActorResolver.currentAuditActor();
        auditLogService.logSuccess(action, actor.userId(), actor.userEmail(),
                entityType, entityId, entitySlug, null, null, null, newValues, null);
    }

    /**
     * The row for an edit: only the fields that differ, so the reader sees the
     * change and not a second copy of the form.
     *
     * <p>An edit that changed nothing still leaves a row — the admin did submit it,
     * and "who saved this at 14:02" is a fair question to ask of the log — but both
     * value columns are then null rather than two identical snapshots.
     */
    public void updated(AuditAction action, String entityType, Long entityId, String entitySlug,
            Map<String, Object> before, Map<String, Object> after) {
        AuditChanges.Changed changed = AuditChanges.between(before, after);
        AuditActor actor = ActorResolver.currentAuditActor();
        auditLogService.logSuccess(action, actor.userId(), actor.userEmail(),
                entityType, entityId, entitySlug, null, null,
                changed.oldValuesOrNull(), changed.newValuesOrNull(), null);
    }

    /**
     * The row for a soft delete: the column that moved is {@code deletedAt}, and
     * the row that moved it is still there to be restored.
     */
    public void softDeleted(AuditAction action, String entityType, Long entityId, String entitySlug,
            Instant deletedAt) {
        Map<String, Object> oldValues = new LinkedHashMap<>();
        oldValues.put("deletedAt", null);

        Map<String, Object> newValues = new LinkedHashMap<>();
        // A string, not an Instant: the audit writer's ObjectMapper has no java.time
        // support, and a value it cannot serialise costs the whole row.
        newValues.put("deletedAt", deletedAt == null ? null : deletedAt.toString());

        AuditActor actor = ActorResolver.currentAuditActor();
        auditLogService.logSuccess(action, actor.userId(), actor.userEmail(),
                entityType, entityId, entitySlug, null, null, oldValues, newValues, null);
    }

    /**
     * The row for a permanent delete: the item is gone, so the audit row is the only thing left
     * that names it.
     *
     * <p>Always {@link AuditAction#ENTITY_PERMANENT_DELETE}, whichever domain the row belongs
     * to: "this stopped existing" is one question, and answering it with a different action per
     * entity is how a purge goes unrecorded. The entity type travels in the payload.
     *
     * <p>Called after the delete has been flushed, so a database refusal leaves no row claiming
     * a removal that did not happen.
     */
    public void permanentlyDeleted(String entityType, Long entityId, String entitySlug, Instant deletedAt) {
        Map<String, Object> oldValues = new LinkedHashMap<>();
        oldValues.put("slug", entitySlug);
        oldValues.put("deletedAt", deletedAt == null ? null : deletedAt.toString());
        deleted(AuditAction.ENTITY_PERMANENT_DELETE, entityType, entityId, entitySlug, oldValues);
    }

    /**
     * The row for a hard delete, where there is nothing left to point at: the
     * {@code oldValues} are the entity's last state.
     */
    public void deleted(AuditAction action, String entityType, Long entityId, String entitySlug,
            Map<String, Object> oldValues) {
        AuditActor actor = ActorResolver.currentAuditActor();
        auditLogService.logSuccess(action, actor.userId(), actor.userEmail(),
                entityType, entityId, entitySlug, null, null, oldValues, null, null);
    }
}
