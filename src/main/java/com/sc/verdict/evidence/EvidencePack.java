package com.sc.verdict.evidence;

/**
 * The demo corpus: same contract, same engine, different evidence. Each pack is a fixed set of
 * documents whose extracted facts drive one of the four determinations.
 */
public enum EvidencePack {
    /** 1,000 units, "tablet computers", shipped 2026-09-08 → RELEASE. */
    CLEAN,
    /** As clean, but goods read "tablet PCs" → RELEASE within tolerance, COSMETIC finding recorded. */
    COSMETIC_VARIANCE,
    /** 800 of 1,000 units → PARTIAL_RELEASE, residual re-earmarked. */
    SHORT_SHIPMENT,
    /** 1,000 units but shipped 2026-09-13, three days late → HOLD_PENDING_APPROVAL. */
    LATE_SHIPMENT
}
