package com.sc.verdict.journal;

import com.sc.verdict.contract.DealDefinition;
import com.sc.verdict.evidence.Submission;
import com.sc.verdict.examination.Determination;
import com.sc.verdict.examination.ToleranceProfile;
import com.sc.verdict.shared.Ids.JournalEntryId;
import com.sc.verdict.shared.Versions.RuleSetVersion;

import java.time.Instant;
import java.util.Objects;

/**
 * One append-only journal record: a determination together with <em>every input needed to reproduce
 * it</em>. That completeness is the point — a journal entry from which the determination cannot be
 * replayed is not an audit record, it is a log line.
 *
 * <p>The entry pins the deal terms, the exact submission (facts and content hashes), the tolerance
 * profile, the rule-set version, and the effective instant. Given those, the pure examination engine
 * yields the identical determination indefinitely (NFR: replay determinism).
 */
public record JournalEntry(
        JournalEntryId id,
        Instant recordedAt,
        DealDefinition deal,
        Submission submission,
        ToleranceProfile tolerance,
        RuleSetVersion ruleSetVersion,
        Instant effectiveAt,
        Determination determination) {

    public JournalEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(recordedAt, "recordedAt");
        Objects.requireNonNull(deal, "deal");
        Objects.requireNonNull(submission, "submission");
        Objects.requireNonNull(tolerance, "tolerance");
        Objects.requireNonNull(ruleSetVersion, "ruleSetVersion");
        Objects.requireNonNull(effectiveAt, "effectiveAt");
        Objects.requireNonNull(determination, "determination");
    }
}
