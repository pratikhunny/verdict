package com.sc.verdict.evidence;

import com.sc.verdict.shared.Ids.DealId;
import com.sc.verdict.shared.Ids.SubmissionId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A bundle of documents and the facts extracted from them, submitted against a deal at an instant.
 *
 * <p>The submission is the evidence-plane unit the examination engine reads. It is immutable; the
 * approval loop does not mutate a submission but produces a new one carrying the original facts plus
 * the approval fact, so the re-examination has its own hashes and its own journal entry.
 */
public record Submission(
        SubmissionId id,
        DealId dealId,
        List<Document> documents,
        List<ExtractedFact> facts,
        Instant submittedAt) {

    public Submission {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(dealId, "dealId");
        Objects.requireNonNull(submittedAt, "submittedAt");
        documents = List.copyOf(documents == null ? List.of() : documents);
        facts = List.copyOf(facts == null ? List.of() : facts);
    }

    /** The first fact asserted for a key, if any. */
    public Optional<ExtractedFact> fact(FactKey key) {
        return facts.stream().filter(f -> f.key() == key).findFirst();
    }

    /** All facts asserted for a key — used where a key may repeat, e.g. approvals. */
    public List<ExtractedFact> factsFor(FactKey key) {
        return facts.stream().filter(f -> f.key() == key).toList();
    }

    /** A new submission carrying every fact of this one plus the supplied additional facts. */
    public Submission withAdditionalFacts(SubmissionId newId, Instant at, List<ExtractedFact> extra) {
        var merged = new java.util.ArrayList<>(facts);
        merged.addAll(extra);
        return new Submission(newId, dealId, documents, merged, at);
    }
}
