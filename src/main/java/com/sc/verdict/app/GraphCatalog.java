package com.sc.verdict.app;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The graph for each deal type, as a real directed graph — nodes with positions and <em>explicit
 * edges</em> (edges are transitions/conditions, not implied by list order). Same node library, a
 * different graph per deal type: the marketplace graph splits to three payees and loops back through
 * an approval branch; the construction graph waits on a time window and loops through objection
 * resolution.
 *
 * <p>Node ids match the lifecycle step keys so the UI highlights the current node as a transaction
 * progresses. Kinds: {@code money}, {@code decision}, {@code evidence}, {@code branch}.
 */
@Component
public class GraphCatalog {

    private static final int X0 = 20, DX = 140, Y = 24, BY = 128;

    public Views.GraphView2 forDealType(String dealType) {
        return dealType != null && dealType.toLowerCase().contains("construction")
                ? construction() : marketplace();
    }

    private Views.GraphView2 marketplace() {
        List<Views.GNode> nodes = List.of(
                node("COLLECT_FUNDS", "COLLECT", 0, "money"),
                node("EARMARK", "EARMARK", 1, "money"),
                node("INTAKE_EVIDENCE", "INTAKE", 2, "evidence"),
                node("EXAMINE", "EXAMINE", 3, "decision"),
                node("DETERMINE", "DETERMINE", 4, "decision"),
                node("SPLIT", "SPLIT ×3", 5, "money"),
                node("DISBURSE", "DISBURSE", 6, "money"),
                branch("SOLICIT_APPROVAL", "SOLICIT APPROVAL", 4));
        List<Views.GEdge> edges = List.of(
                edge("COLLECT_FUNDS", "EARMARK"), edge("EARMARK", "INTAKE_EVIDENCE"),
                edge("INTAKE_EVIDENCE", "EXAMINE"), edge("EXAMINE", "DETERMINE"),
                edge("DETERMINE", "SPLIT"), edge("SPLIT", "DISBURSE"),
                dashed("DETERMINE", "SOLICIT_APPROVAL", "findings"),
                dashed("SOLICIT_APPROVAL", "EXAMINE", "approval re-enters as evidence"));
        return new Views.GraphView2("Marketplace trade", nodes, edges);
    }

    private Views.GraphView2 construction() {
        List<Views.GNode> nodes = List.of(
                node("COLLECT_FUNDS", "COLLECT", 0, "money"),
                node("EARMARK", "EARMARK", 1, "money"),
                node("INTAKE_EVIDENCE", "INTAKE", 2, "evidence"),
                node("EXAMINE", "EXAMINE", 3, "decision"),
                node("DETERMINE", "DETERMINE", 4, "decision"),
                node("TIME_WINDOW", "TIME WINDOW", 5, "decision"),
                node("DISBURSE", "DISBURSE", 6, "money"),
                branch("SOLICIT_APPROVAL", "RESOLVE OBJECTION", 4));
        List<Views.GEdge> edges = List.of(
                edge("COLLECT_FUNDS", "EARMARK"), edge("EARMARK", "INTAKE_EVIDENCE"),
                edge("INTAKE_EVIDENCE", "EXAMINE"), edge("EXAMINE", "DETERMINE"),
                edge("DETERMINE", "TIME_WINDOW"), edge("TIME_WINDOW", "DISBURSE"),
                dashed("DETERMINE", "SOLICIT_APPROVAL", "objection"),
                dashed("SOLICIT_APPROVAL", "EXAMINE", "resolved → re-examine"));
        return new Views.GraphView2("Construction retention", nodes, edges);
    }

    private static Views.GNode node(String id, String label, int col, String kind) {
        return new Views.GNode(id, label, X0 + col * DX, Y, kind);
    }

    private static Views.GNode branch(String id, String label, int col) {
        return new Views.GNode(id, label, X0 + col * DX, BY, "branch");
    }

    private static Views.GEdge edge(String from, String to) {
        return new Views.GEdge(from, to, null, false);
    }

    private static Views.GEdge dashed(String from, String to, String label) {
        return new Views.GEdge(from, to, label, true);
    }
}
