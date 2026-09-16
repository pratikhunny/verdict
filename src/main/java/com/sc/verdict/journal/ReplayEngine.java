package com.sc.verdict.journal;

import com.sc.verdict.examination.Determination;
import com.sc.verdict.examination.ExaminationEngine;

import java.util.Objects;

/**
 * Replays a journal entry: re-runs the examination engine on the entry's pinned inputs and checks
 * that the determination is byte-for-byte the one that was recorded.
 *
 * <p>This is the trust close of the demo — "the model reads, the model never decides" — made
 * checkable. Because the engine is a pure function of the pinned inputs and rule-set version, the
 * replayed determination must equal the original; if it ever did not, either an input was not pinned
 * or the engine read something it should not have.
 */
public final class ReplayEngine {

    private final ExaminationEngine engine;

    public ReplayEngine(ExaminationEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    public ReplayResult replay(JournalEntry entry) {
        Objects.requireNonNull(entry, "entry");
        Determination replayed = engine.examine(
                entry.deal(), entry.submission(), entry.tolerance(),
                entry.ruleSetVersion(), entry.effectiveAt());
        return new ReplayResult(entry.determination(), replayed, replayed.equals(entry.determination()));
    }

    /** The comparison outcome. {@code identical} must be true for a determinism claim to hold. */
    public record ReplayResult(Determination original, Determination replayed, boolean identical) {

        public String describe() {
            return "original %s (%s) vs replay %s (%s) → %s".formatted(
                    original.id(), original.outcome(),
                    replayed.id(), replayed.outcome(),
                    identical ? "IDENTICAL" : "DIVERGED");
        }
    }
}
