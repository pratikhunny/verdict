package com.sc.verdict.orchestrator;

/**
 * The node library — code, shared by every deal type (HLD §6, ADR-004). A node is a unit of the
 * pipeline: reviewed, versioned, tested once. A new deal type composes these into a different graph;
 * it does not add a node. "Different graph, same nodes" is meant literally, and it is these twelve
 * constants that make it literal.
 */
public enum Node {
    INGEST_CONTRACT,
    DERIVE_OBLIGATIONS,
    COLLECT_FUNDS,
    EARMARK,
    INTAKE_EVIDENCE,
    EXAMINE,
    DETERMINE,
    SOLICIT_APPROVAL,
    SPLIT,
    DISBURSE,
    TIME_WINDOW,
    REFUND
}
