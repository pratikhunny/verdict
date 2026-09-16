package com.sc.verdict.orchestrator;

import java.util.List;
import java.util.Objects;

/**
 * A deal type expressed as configuration: an ordered sequence of {@link Node}s from the shared
 * library. This is the whole of what a new deal type adds — no new code (ADR-004). The
 * findings/approval loop (DETERMINE → SOLICIT_APPROVAL → EXAMINE) is a re-entry the runtime handles
 * rather than a separate node ordering, so it is not spelled out here.
 */
public record GraphDefinition(String name, List<Node> sequence) {

    public GraphDefinition {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(sequence, "sequence");
        if (sequence.isEmpty()) {
            throw new IllegalArgumentException("a graph needs at least one node");
        }
        sequence = List.copyOf(sequence);
    }
}
