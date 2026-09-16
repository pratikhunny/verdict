package com.sc.verdict.app;

import com.sc.verdict.evidence.Submission;
import com.sc.verdict.shared.Ids.DealId;

import java.time.Instant;
import java.util.List;

/**
 * Reads uploaded documents into a {@link Submission} of extracted facts — the L3 job. Two
 * implementations: a deterministic parser (rules over the text, identical every time) and a live LLM
 * (Spring AI). Both read the same uploaded text; the routing between them is the ops team's toggle.
 */
public interface DocumentExtractor {

    /** A short label for the journal / UI, e.g. "deterministic-rules" or "llm:claude-…". */
    String label();

    /** Whether this extractor can run right now (the LLM needs a key). */
    boolean available();

    Submission extract(String submissionId, DealId dealId, List<UploadedDoc> documents, Instant at);
}
