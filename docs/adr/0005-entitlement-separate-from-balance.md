# ADR-005 — Entitlement is separate from balance

| | |
|---|---|
| **Status** | Accepted |
| **Date** | 2026-08-22 |
| **Deciders** | Pratik Kumar |
| **Related** | ADR-001 (three planes), ADR-003 (obligation as primitive) |

---

## Context

Two numbers exist in any conditional-settlement system and are routinely stored as one:

- **Entitlement** — what a party is *owed* under determined obligations. A decision-plane fact.
- **Held balance and earmarks** — what is actually *held* and reserved. A money-plane fact.

Storing them as one record is the default, and it removes the only independent check the system
could have had.

---

## Decision

**Compute entitlement and earmarks independently, in different planes, and reconcile them
continuously.**

- The decision plane owns **entitlement**, derived from determinations against obligation versions.
- The money plane owns **held balance**, **earmarks** and **postings**, and has no opinion on
  whether a release is correct.
- A scheduled control asserts, per deal:

```
sum(entitlements not yet disbursed)  ==  sum(earmarks)
sum(earmarks)                        <=  available held balance
```

A break raises an **alert**, not a report line.

Ledger rules: double-entry, postings immutable, corrections are reversing postings, no negative
held balance, no over-earmark. Enforced by database constraint where expressible, by the control
where not.

---

## Options considered

**A. One record — obligation carries amount, status and released flag.** Rejected: no independent
check, and settlement failure states contaminate business state.

**B. Balance derived from obligation state on read.** Rejected: derivation and truth become the same
thing, so a logic error is invisible; and it cannot represent funds held that no obligation yet
claims.

**C. Independent computation plus reconciliation** *(chosen)*.

---

## Consequences

### Positive

- The reconciliation break is a real signal. Logic errors, partial failures and race conditions
  surface as an alert rather than as a client complaint.
- Partial release is expressible: 80% disbursed, 20% re-earmarked against a residual obligation,
  with both sides independently verifiable.
- Funds can be held before any obligation claims them — a real state during funding.
- The two things Risk asks for first — the reconciliation control and the decision journal — are
  built rather than promised.

### Negative — accepted

- **The control must actually be built and monitored.** Without it, the separation is pure overhead.
  This is a build commitment carried from ADR-001, scheduled early rather than late.
- Reporting that shows obligations and balances together spans planes and needs a read model.
- Two writes where one would do, and a deliberate decision about ordering and failure between them.

---

## Prepared answer

> "Because they should always agree — which means when they don't, something is wrong, and we want
> to hear it from an alert rather than from a client. If they're the same record they can't
> disagree, and the control doesn't exist."
