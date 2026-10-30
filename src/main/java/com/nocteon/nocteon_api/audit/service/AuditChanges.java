package com.nocteon.nocteon_api.audit.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
/**
 * The old/new halves of one change, for an audit row.
 *
 * <p>An UPDATE row exists to answer "what did this admin change", so it carries the
 * fields that actually differ and nothing else: a renamed category shows the name,
 * not the slug and the image that stayed the same. Snapshot the entity before and
 * after the mutation with two {@code Map}s and this type does the comparison once,
 * for every domain — a second copy of the loop is how the two halves drift apart.
 *
 * <p>Values must already be JSON-friendly (enums as {@code name()}, instants as
 * ISO strings): {@code AuditLogService} serialises with a plain
 * {@code ObjectMapper}, which has no {@code java.time} support.
 */
public final class AuditChanges {

    /** Nothing differed: an empty pair, which the recorder still writes as a row. */
    public static final Changed NONE = new Changed(Map.of(), Map.of());

    private AuditChanges() {
    }

    /**
     * The fields that differ between the two snapshots.
     *
     * <p>Keys are compared over the union: a key the "after" snapshot dropped
     * records {@code newValues[key] = null}, a key it added records
     * {@code oldValues[key] = null}, so "was unset" and "is now unset" both read
     * correctly instead of silently disappearing.
     */
    public static Changed between(Map<String, Object> before, Map<String, Object> after) {
        if (before == null && after == null) {
            return NONE;
        }
        Map<String, Object> oldValues = new LinkedHashMap<>();
        Map<String, Object> newValues = new LinkedHashMap<>();

        if (after != null) {
            for (Map.Entry<String, Object> entry : after.entrySet()) {
                Object was = before == null ? null : before.get(entry.getKey());
                if (!Objects.equals(was, entry.getValue())) {
                    oldValues.put(entry.getKey(), was);
                    newValues.put(entry.getKey(), entry.getValue());
                }
            }
        }
        if (before != null) {
            for (Map.Entry<String, Object> entry : before.entrySet()) {
                if (after == null || !after.containsKey(entry.getKey())) {
                    oldValues.put(entry.getKey(), entry.getValue());
                    newValues.put(entry.getKey(), null);
                }
            }
        }
        return new Changed(oldValues, newValues);
    }

    /** The two columns of an UPDATE row. Empty means "nothing differed". */
    public record Changed(Map<String, Object> oldValues, Map<String, Object> newValues) {

        public boolean isEmpty() {
            return oldValues.isEmpty() && newValues.isEmpty();
        }

        /** The column value: the map, or {@code null} when the map is empty. */
        public Map<String, Object> oldValuesOrNull() {
            return oldValues.isEmpty() ? null : oldValues;
        }

        /** The column value: the map, or {@code null} when the map is empty. */
        public Map<String, Object> newValuesOrNull() {
            return newValues.isEmpty() ? null : newValues;
        }
    }
}
