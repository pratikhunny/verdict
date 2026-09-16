package com.sc.verdict.shared;

import java.util.Objects;

/**
 * Pinned version tags for the two things that must be fixed for a determination to replay:
 * the rule set the examination engine evaluated under, and the extraction adapter that asserted
 * the facts. Both are written into every {@code JournalEntry}; a replay that reads a different
 * version is not a replay.
 *
 * <p>See ADR-002 (models assert, rules determine) and the NFR on replay determinism: without a
 * pinned rule-set version, "the same evidence gives the same answer" is unprovable, because the
 * rules could have moved underneath it.
 */
public final class Versions {

    private Versions() {}

    /** The version of the deterministic rule set the examination engine evaluates under. */
    public record RuleSetVersion(String value) {
        public RuleSetVersion { value = requireNonBlank(value, "ruleSetVersion"); }
        @Override public String toString() { return value; }
    }

    /** The version of the extraction adapter that asserted a fact. Pinned per fact for provenance. */
    public record AdapterVersion(String value) {
        public AdapterVersion { value = requireNonBlank(value, "adapterVersion"); }
        @Override public String toString() { return value; }
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
