# ADR-001 — Separate the decision, money and evidence planes

| | |
|---|---|
| **Status** | Accepted |
| **Date** | 2026-08-22 |
| **Deciders** | Pratik Kumar |
| **Supersedes** | — |
| **Related** | ADR-005 (entitlement vs balance), ADR-002 (extraction/determination boundary) |

---

## Context

Verdict holds funds and releases them on conditions. Three questions must be answerable at any
moment, and they are different questions:

1. **What *should* happen?** Which obligations are due, to whom, for how much.
2. **What *did* happen?** What is held, what is reserved, what was paid.
3. **Why did it happen?** On what evidence, under which rules, authorised by whom.

Systems in this space typically answer all three from one model — an obligation row carrying a
status, an amount, and a `released` flag, with the balance derived from it or stored beside it.
That design fails in four specific ways, all of which we will be asked about:

- **Replay is impossible.** Once a status is overwritten, the grounds of the earlier determination
  are gone. "Why did this money move in March?" has no mechanical answer.
- **Rail substitution is invasive.** Settlement failure states leak into obligation states, so
  changing rails means touching business logic.
- **Reconciliation has no reference point.** If entitlement and balance are the same record, they
  cannot disagree — which sounds desirable and is in fact the problem. Disagreement is the signal
  that something is wrong, and a design that suppresses it suppresses the alarm.
- **Audit and operations contend.** The record that must be immutable is the same record operations
  needs to update.

The bank's exposure here is not hypothetical. Where a determination is challenged, the ability to
reproduce it from its original inputs is the difference between a defensible position and a
commercial settlement.

---

## Decision

**Separate the system into three planes with a one-directional contract between them.**

| Plane | Answers | Owns | Mutability |
|---|---|---|---|
| **Decision** | What should happen | Milestones, obligations, conditions, findings, determinations, entitlements | Versioned; new versions, never edits |
| **Money** | What did happen | Held balance, earmarks, postings, disbursements | Append-only postings; corrections are reversing postings |
| **Evidence** | Why it happened | Documents, extracted facts, approvals, decision journal | Append-only, never mutated |

### Boundary rules

1. **The decision plane never writes postings.** It emits a **determination**, which is the sole
   contract between planes. A determination names the obligation version, the amount, the payee,
   and the journal entry that justifies it.
2. **The money plane never reads obligations.** It obeys instructions that carry an obligation
   reference as an opaque correlation key. It has no opinion on whether a release is correct.
3. **The evidence plane is written by both and mutated by neither.** Every determination and every
   posting emits a journal entry; nothing ever edits one.
4. **Human decisions enter as evidence, not as bypasses.** An approval of findings is recorded in
   the evidence plane and re-enters the decision plane for re-examination. There is no path by which
   a human writes a determination directly.

### The control that makes the separation pay for itself

Because entitlement (decision plane) and earmarks (money plane) are computed independently, they
can be compared. A continuous invariant check asserts, per deal:

```
sum(entitlements not yet disbursed)  ==  sum(earmarks)
sum(earmarks)                        <=  available held balance
```

A break is an alert, not a rounding difference. In a conflated model this check cannot exist,
because the two sides are the same number.

---

## Options considered

**A. Single transactional model** — obligation aggregate carrying status and balance.
Simplest, fastest to build, and how most first versions are written. Rejected: no replay, no
independent reconciliation, and rail failure states contaminate business state.

**B. Single event-sourced stream** — one event log, all projections derived.
Gives replay and immutability. Rejected as the primary structure: a single stream forces one
consistency and retention regime across evidence that must be kept for years and operational state
that changes minutely. It also does not by itself prevent entitlement and balance being projected
from the same events with the same errors.

**C. Three-plane separation** *(chosen)* — distinct models, determination as the contract,
append-only journal.

**D. Plane-per-service with asynchronous integration** — the same separation, deployed as separate
services with eventual consistency between them.
Rejected **for now**: the separation we need is logical, not physical. Distributing it adds
partition-failure modes and reconciliation lag without adding correctness. The boundaries are drawn
so that this remains available later without redesign.

---

## Consequences

### Positive

- **Determinations are replayable.** Given a journal entry, the same obligation version, evidence
  hashes and rule-set version reproduce the identical outcome indefinitely.
- **Settlement rails are substitutable** — internal book transfer, RTGS, correspondent, tokenised
  deposit — because rail state never enters the decision plane.
- **Reconciliation is a first-class control** rather than an after-the-fact report.
- **The evidence plane can be retained, exported or notarised** independently of operational data.
- **Extraction cannot reach the money plane** even in principle. This is what makes ADR-002
  enforceable rather than aspirational.

### Negative — accepted

- **More code and more mapping.** Two models where one would do, plus the determination contract
  between them. Cost is real and is the price of the reconciliation control.
- **Two things that can disagree.** The invariant check must actually be built and monitored, or the
  separation delivers overhead without benefit. **This is a build commitment, not an option.**
- **Cross-plane queries need a read model.** "Show this deal's obligations and balances together"
  spans planes.
- **Discipline is required at review.** The first pull request that writes a posting from the
  decision plane will look reasonable. It must be rejected.

### Neutral

- Physical deployment is unconstrained: one JVM and one Postgres schema satisfies this ADR. The
  separation is enforced by module boundaries and package structure, not by network hops.

---

## Prepared answers

**"Isn't this over-engineered for a hackathon?"**
The separation is what makes the replay demonstration possible, and replay is what a bank needs to
see before conditional release is credible. Without it we would be showing a workflow tool. It also
costs less than it appears: one JVM, one database, enforced by module boundaries.

**"Why keep entitlement and balance apart when they should always agree?"**
Because they should always agree — which means when they don't, something is wrong, and we want to
find out from an alert rather than from a client. If they are the same record they cannot disagree
and the control does not exist.

**"How would this work with a different settlement rail?"**
The money plane exposes a rail port. Rail state never enters the decision plane, so adding
tokenised deposit settlement is an adapter, not a change to how releases are determined.

---

## Compliance

Enforced by module boundaries and reviewed at pull request. The specific rules:

- No decision-plane package may import a money-plane repository or posting type.
- No money-plane package may import an obligation, condition or finding type.
- Every determination and posting writes exactly one journal entry, in the same transaction as the
  state change it records.
- The entitlement/earmark invariant check runs continuously and alerts on any break.
