# ADR-004 — Nodes are code; graphs are configuration

| | |
|---|---|
| **Status** | Accepted |
| **Date** | 2026-08-22 |
| **Deciders** | Pratik Kumar |
| **Related** | ADR-001, ADR-003 |

---

## Context

The escrow business already assembles bespoke arrangements from reusable services on demand. Verdict
must extend that capability rather than sit beside it as a parallel product, or it cannot convert to
a pilot.

The platform claim — *"different graph, same nodes"* — is therefore load-bearing. If a new deal type
requires new code, the claim is marketing and a judge who probes will find it in a minute.

---

## Decision

**A fixed library of nodes is code. Deal types are graph definitions plus rule sets, which are
configuration.**

Node contract:

| Element | Rule |
|---|---|
| Input | Immutable deal context as of a supplied instant. No clock reads, no ambient state |
| Output | Domain events; never direct state mutation |
| Authority | Any state-changing node presents an instruction to L0 admission first |
| Idempotency | Keyed on (deal, node, correlation key); re-execution is a no-op |
| Journal | Emits exactly one journal entry per execution |
| Determinism | Same input and rule-set version → same output |

Node library: `INGEST` · `DERIVE` · `COLLECT` · `EARMARK` · `INTAKE` · `EXAMINE` · `DETERMINE` ·
`SOLICIT_APPROVAL` · `SPLIT` · `DISBURSE` · `TIME_WINDOW` · `REFUND`.

Four deal types compose from that library with no new code: marketplace trade, holdback, retention,
conditional payout.

**Deliberately not a scripting language.** An open expression language inside a control that moves
money is unauditable and untestable. A closed vocabulary of node types, with parameters, is what a
risk function will sign.

---

## Options considered

**A. A service per deal type.** Fastest per deal type, and the reason bespoke escrow is expensive
today. Rejected: every new arrangement is a project.

**B. Configurable workflow engine with embedded scripting.** Maximum flexibility. Rejected:
unbounded expression surface, untestable in aggregate, and no way to assert that a given deal type
is safe.

**C. Closed node library, graphs as configuration** *(chosen)*.

**D. Off-the-shelf BPMN engine.** Viable and worth revisiting for production. Rejected for now:
brings an execution model and failure semantics we would have to constrain heavily to preserve
replay determinism, which is more work than the thin runtime we need.

---

## Consequences

### Positive

- A new deal type is a configuration change, reviewed as configuration.
- The node library is small enough to test exhaustively.
- Extends the existing orchestration capability rather than competing with it — the strongest
  available answer to "why build rather than buy".
- Node-level idempotency and journaling are uniform, so replay works across every graph.

### Negative — accepted

- A genuinely novel behaviour requires a new node, which is a code change with full review. This is
  intended friction.
- Graph definitions need validation — cycle detection, reachability, and that every money-effecting
  node sits behind admission.
- The claim must stay literally true. **The first deal type that needs a special case in an existing
  node is the moment this ADR is being violated**, and it will look reasonable at the time.

---

## Prepared answer

> "We didn't build a marketplace escrow product. We built the nodes. The graph is configuration —
> holdback, retention and conditional payout are different graphs over the same library, with no new
> code."
