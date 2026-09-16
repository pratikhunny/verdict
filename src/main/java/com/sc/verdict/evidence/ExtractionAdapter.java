package com.sc.verdict.evidence;

import com.sc.verdict.shared.Ids.DealId;
import com.sc.verdict.shared.Versions.AdapterVersion;

import java.time.Instant;

/**
 * L3 — the extraction seam. It turns documents into asserted facts. This is one of the three things
 * mocked on stage (screening, extraction, settlement); the port is real and every submission flows
 * through it, so going live means replacing this one implementation with a document-AI adapter and
 * changing nothing downstream.
 *
 * <p>By construction the adapter cannot decide anything: its only output is {@link ExtractedFact}s
 * with confidence and provenance. The examination engine contains no model call, and this interface
 * is why that separation holds (ADR-002).
 */
public interface ExtractionAdapter {

    /** The pinned version of this adapter, written into every fact and journal entry. */
    AdapterVersion version();

    /** Extract the facts of one evidence pack against a deal, as at an instant. */
    Submission extract(EvidencePack pack, DealId dealId, Instant at);
}
