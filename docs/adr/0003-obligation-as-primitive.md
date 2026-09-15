# ADR-003 — The obligation is the primitive; the milestone is the trigger

| | |
|---|---|
| **Status** | Accepted |
| **Date** | 2026-08-22 |
| **Deciders** | Pratik Kumar |
| **Related** | ADR-001, ADR-005 (entitlement vs balance) |

---

## Context

Conditional-payment systems typically model a **milestone** that carries an amount and a payee, and
release when its conditions are met. That works until the first real deal, which has a fee, a
commission, and a supplier payment discharged by the same event.

Separately, the market framing worth adopting is that **contracts and payments live in separate
systems**, and the obligation is the unit connecting them. Vendors in this space have made that
argument well. Where they are weaker is that they treat milestone and obligation as one thing.

---

## Decision

**Model milestone and obligation as distinct entities.**

- A **milestone** is a named point in the deal lifecycle whose **conditions** are evaluated
  together. It carries no amount and no payee.
- An **obligation** is a payment entitlement contingent on a milestone: payer, payee, determination
  rule, severability, and the clause of the contract version it derives from.
- One milestone discharges **many** obligations.

```
Milestone: shipment evidenced
  ├─ Condition: eBL presented
  ├─ Condition: quantity matches
  ├─ Condition: shipped by latest date
  ├─ Obligation: 96% of tranche → supplier
  ├─ Obligation: 3.3% of tranche → marketplace
  └─ Obligation: 0.7% of tranche → bank
```

A **condition** is independently determinable and carries its own verdict. That sub-unit is what
makes partial release and itemised findings notices possible: a single pass/fail on the whole
submission cannot express "quantity is short but everything else is fine".

---

## Options considered

**A. Milestone carries amount and payee.** Simplest. Rejected: cannot express multi-payee splits or
fee deduction without duplicating the trigger and its conditions per payee, which then drift.

**B. Obligation carries its own conditions, no milestone entity.** Removes duplication of amounts
but duplicates conditions across obligations discharged by the same event, and makes "what happened"
unanswerable — there is no entity representing the event.

**C. Milestone as trigger, obligation as entitlement, condition as determinable sub-unit**
*(chosen)*.

---

## Consequences

### Positive

- Multi-payee settlement, commissions and fee deduction are expressible without duplication.
- Partial release scales all obligations on a milestone together, pro-rata, from one determination.
- A **residual obligation** after partial release is a first-class entity, not a status flag.
- Itemised findings notices fall out naturally: one finding per failed condition.
- Obligations anchor to a clause of a specific contract version, so amendments produce new
  obligation versions rather than mutating history.

### Negative — accepted

- Three entities where vendors use one; more joins, more mapping, a steeper first read of the
  schema.
- Authoring a deal type means defining conditions and obligations separately. Mitigated by deal-type
  templates.

---

## Prepared answer

> "A milestone is a trigger; an obligation is an entitlement. One shipment event releases the
> supplier payment, the marketplace commission and our fee — that's three obligations on one
> milestone. Systems that collapse them can't express a split, or a partial release across a split,
> without duplicating the trigger."
