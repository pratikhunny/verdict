package com.sc.verdict.examination;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * The tolerance rules the examination engine applies — part of the pinned rule set, not a runtime
 * judgement. Two things: an explicit list of goods-description equivalences that clear a cosmetic
 * variance, and the minimum extraction confidence below which a fact is not trustworthy enough to
 * act on (fail closed).
 *
 * <p>The point on stage (Pack 2) is that the rule is <em>explicit and cited</em>: the release of a
 * "tablet PCs" invoice against a "tablet computers" contract is not the engine being lenient, it is
 * this named rule permitting it, and the journal records which rule.
 */
public record ToleranceProfile(String ruleReference, List<Set<String>> equivalenceClasses, double minConfidence) {

    public ToleranceProfile {
        Objects.requireNonNull(ruleReference, "ruleReference");
        Objects.requireNonNull(equivalenceClasses, "equivalenceClasses");
        if (minConfidence < 0.0 || minConfidence > 1.0) {
            throw new IllegalArgumentException("minConfidence must be in [0,1]");
        }
        equivalenceClasses = List.copyOf(equivalenceClasses);
    }

    /** The default demo profile: one goods synonym class, and a 0.90 confidence floor. */
    public static ToleranceProfile demoDefault() {
        return new ToleranceProfile(
                "TOL-2026.09 / goods-description synonyms",
                List.of(Set.of("tablet computers", "tablet pcs", "tablet pc")),
                0.90);
    }

    /**
     * Whether two goods descriptions are equivalent under a tolerance rule. Exact match (ignoring
     * case and surrounding space) is trivially equivalent; otherwise both must fall in the same
     * declared equivalence class.
     */
    public boolean goodsEquivalent(String contractDescription, String evidenceDescription) {
        String a = normalise(contractDescription);
        String b = normalise(evidenceDescription);
        if (a.equals(b)) {
            return true;
        }
        return equivalenceClasses.stream().anyMatch(cls -> cls.contains(a) && cls.contains(b));
    }

    /** Whether the descriptions are identical after normalisation (no tolerance needed). */
    public boolean goodsExact(String contractDescription, String evidenceDescription) {
        return normalise(contractDescription).equals(normalise(evidenceDescription));
    }

    private static String normalise(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }
}
