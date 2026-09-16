package com.sc.verdict.orchestrator;

import java.util.List;

import static com.sc.verdict.orchestrator.Node.*;

/**
 * Four deal types, one node library, no new code — the platform claim as a single file (HLD §6).
 * Each graph is a different ordering of the same {@link Node} constants. This is the "one slide,
 * four graphs, same nodes" beat made executable: change the configuration, get a different product.
 */
public final class Graphs {

    private Graphs() {}

    /** The hero case: collect, earmark, take evidence, examine, determine, split, disburse. */
    public static final GraphDefinition MARKETPLACE_TRADE = new GraphDefinition(
            "Marketplace trade",
            List.of(COLLECT_FUNDS, EARMARK, INTAKE_EVIDENCE, EXAMINE, DETERMINE, SPLIT, DISBURSE));

    /** M&A holdback: funds sit behind a time window before a claim can even be examined. */
    public static final GraphDefinition MNA_HOLDBACK = new GraphDefinition(
            "M&A holdback",
            List.of(COLLECT_FUNDS, TIME_WINDOW, INTAKE_EVIDENCE, EXAMINE, DETERMINE, SPLIT, DISBURSE));

    /** Construction retention: examine a certificate, retain, hold to a window, then disburse. */
    public static final GraphDefinition CONSTRUCTION_RETENTION = new GraphDefinition(
            "Construction retention",
            List.of(COLLECT_FUNDS, INTAKE_EVIDENCE, EXAMINE, DETERMINE, EARMARK, TIME_WINDOW, DISBURSE));

    /** Conditional payout: the minimal path — one event, one examination, one disbursement. */
    public static final GraphDefinition CONDITIONAL_PAYOUT = new GraphDefinition(
            "Conditional payout",
            List.of(COLLECT_FUNDS, INTAKE_EVIDENCE, EXAMINE, DETERMINE, DISBURSE));

    public static List<GraphDefinition> all() {
        return List.of(MARKETPLACE_TRADE, MNA_HOLDBACK, CONSTRUCTION_RETENTION, CONDITIONAL_PAYOUT);
    }
}
