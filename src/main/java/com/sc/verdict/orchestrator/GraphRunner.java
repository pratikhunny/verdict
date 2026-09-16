package com.sc.verdict.orchestrator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A thin runtime that executes a {@link GraphDefinition} by invoking a handler per node in order.
 *
 * <p>Deliberately minimal (HLD §9: "orchestrator — built, thin"). Its purpose is to make the
 * nodes-are-code / graphs-are-configuration split real: the same handlers, sequenced by a different
 * graph, run a different deal type. It records the trace of nodes actually executed so the console
 * can show the graph running.
 */
public final class GraphRunner {

    /** Execute a graph, running the handler registered for each node. Returns the executed trace. */
    public List<Node> run(GraphDefinition graph, Map<Node, Runnable> handlers) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(handlers, "handlers");
        var trace = new ArrayList<Node>();
        for (Node node : graph.sequence()) {
            Runnable handler = handlers.get(node);
            if (handler != null) {
                handler.run();
            }
            trace.add(node);
        }
        return trace;
    }
}
