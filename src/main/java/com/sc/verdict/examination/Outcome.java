package com.sc.verdict.examination;

/**
 * The four determinations. Neutral operational verbs, deliberately — never "honour" or "refuse"
 * (glossary). These are the only outcomes the engine may reach, and the whole demo is four packs
 * producing one of each from the same contract and rule set.
 */
public enum Outcome {
    /** All conditions met (findings, if any, cleared or approved). Full entitlement pays out. */
    RELEASE,
    /** A severable shortfall: release pro-rata, re-earmark the remainder as a residual obligation. */
    PARTIAL_RELEASE,
    /** A party-approvable finding: nothing moves, an approval request is drafted, the clock starts. */
    HOLD_PENDING_APPROVAL,
    /** A blocking finding that is neither severable nor approvable. Funds stay earmarked. */
    HOLD
}
