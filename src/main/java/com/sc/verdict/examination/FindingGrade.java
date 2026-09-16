package com.sc.verdict.examination;

/**
 * The taxonomy of findings. The grade is not decoration: it decides what the engine may do with a
 * failed condition without a human, and it is the vocabulary the whole demo turns on.
 *
 * <p>Two properties carry the logic. {@code severable} means a shortfall can be released in
 * proportion rather than held whole — only {@link #QUANTITY}. {@code autoApprovable} means the
 * engine may clear it under a tolerance rule with no party approval — only {@link #COSMETIC}.
 * Everything else needs a party's approval ({@link #TIMING}) or is never releasable without fresh
 * evidence ({@link #SUBSTANTIVE}, {@link #MISSING_DOCUMENT}).
 */
public enum FindingGrade {

    /** A description or formatting variance within tolerance. Cleared by rule, but recorded. */
    COSMETIC(false, true, false),
    /** Evidenced quantity below contracted. Severable → drives partial release, not an approval. */
    QUANTITY(true, false, false),
    /** Shipped after the latest date. Not severable, not auto-clearable, but party-approvable. */
    TIMING(false, false, true),
    /** A required document is absent. Cannot be examined into a release. */
    MISSING_DOCUMENT(false, false, false),
    /** A material breach of terms. Never auto-approvable; requires fresh evidence or termination. */
    SUBSTANTIVE(false, false, false),
    /** An outstanding objection or lien (construction retention). Party-approvable once resolved. */
    OBJECTION(false, false, true);

    private final boolean severable;
    private final boolean autoApprovable;
    private final boolean partyApprovable;

    FindingGrade(boolean severable, boolean autoApprovable, boolean partyApprovable) {
        this.severable = severable;
        this.autoApprovable = autoApprovable;
        this.partyApprovable = partyApprovable;
    }

    /** May a shortfall of this grade be released pro-rata rather than held whole? */
    public boolean isSeverable() {
        return severable;
    }

    /** May the engine clear this grade under a tolerance rule, with no party approval? */
    public boolean isAutoApprovable() {
        return autoApprovable;
    }

    /** May an entitled party approve this grade so the engine releases on re-examination? */
    public boolean isPartyApprovable() {
        return partyApprovable;
    }
}
