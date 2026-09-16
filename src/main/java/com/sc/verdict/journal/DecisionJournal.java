package com.sc.verdict.journal;

import com.sc.verdict.contract.DealDefinition;
import com.sc.verdict.evidence.Submission;
import com.sc.verdict.examination.Determination;
import com.sc.verdict.examination.ToleranceProfile;
import com.sc.verdict.shared.Ids.DeterminationId;
import com.sc.verdict.shared.Ids.JournalEntryId;
import com.sc.verdict.shared.Versions.RuleSetVersion;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * L8 — the decision journal. Append-only by construction: it exposes appends and reads, and no way
 * to mutate or remove an entry. Every determination writes exactly one entry (HLD §6 node contract),
 * and that entry is complete enough to replay (see {@link ReplayEngine}).
 */
public final class DecisionJournal {

    private final List<JournalEntry> entries = new ArrayList<>();
    private long seq = 0;

    /** Append one entry for a determination and its inputs. Returns the stored, immutable entry. */
    public JournalEntry append(DealDefinition deal, Submission submission, ToleranceProfile tolerance,
                               RuleSetVersion ruleSetVersion, Instant effectiveAt,
                               Determination determination, Instant recordedAt) {
        var entry = new JournalEntry(
                new JournalEntryId("JRN-%04d".formatted(++seq)),
                recordedAt, deal, submission, tolerance, ruleSetVersion, effectiveAt, determination);
        entries.add(entry);
        return entry;
    }

    public List<JournalEntry> entries() {
        return List.copyOf(entries);
    }

    public Optional<JournalEntry> forDetermination(DeterminationId determinationId) {
        return entries.stream()
                .filter(e -> e.determination().id().equals(determinationId))
                .reduce((first, second) -> second); // the latest, if a determination id recurs
    }
}
