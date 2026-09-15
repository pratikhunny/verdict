# Scenario catalogue — the hero deal and four evidence packs

This document does triple duty: **demo script**, **test specification**, and the **"mock production
data with its lifecycle"** the pre-hackathon feedback asked for. Every pack below must be executable
and must produce exactly the stated determination.

---

## The deal

**Cross-border marketplace trade.** Buyer in Malaysia, supplier in Vietnam, SCB as agent, marketplace
operator as observer.

| | |
|---|---|
| Deal | `DEAL-2026-0417` |
| Contract value | USD 1,000,000 |
| Goods | 1,000 units, tablet computers |
| Incoterms rule | CIF Port Klang |
| Latest shipment date | 2026-09-10 |
| Deal currency | USD (single currency — see pitch Q&A) |

### Parties

| Party | Responsibility | Notes |
|---|---|---|
| Selangor Components Sdn Bhd (MY) | Payer / obligor | Signatories: Treasury Manager (≤ USD 50k single), CFO (dual, uncapped) |
| Hanoi Precision Trading JSC (VN) | Payee / obligee | Signatory: Director |
| Standard Chartered Bank | Agent | Operations maker/checker |
| Marketplace operator | Observer | Read-only; **cannot instruct** |

### Obligations

| ID | Milestone | Conditions | Determination rule | Severable |
|---|---|---|---|---|
| **O1** Advance | Order accepted | Signed PO present; funding received | 20% fixed → USD 200,000 | No |
| **O2** Shipment | Shipment evidenced | eBL present; quantity matches; shipped ≤ latest date | 60%, **pro-rated to evidenced quantity** | **Yes** |
| **O3** Retention | Goods accepted | Inspection certificate present, **or** 14-day objection window lapses | Residual 20% | No |

**O2 is the demonstration.** Pro-rating to evidenced quantity makes partial release *necessary*
rather than decorative, and short shipment is among the most common real findings.

### Multi-payee split on O2

One milestone, several obligations — this is the modelling point that separates Verdict from
vendors who conflate the two:

| Obligation | Payee | Share of tranche |
|---|---|---|
| O2a | Supplier | 96.0% |
| O2b | Marketplace commission | 3.3% |
| O2c | Bank fee | 0.7% |

All three are discharged by the *same* milestone and scale together under partial release.

---

## The four evidence packs

Same contract. Same engine. Same rule-set version. Only the evidence differs.

---

### Pack 1 · Clean

**Purpose:** establish the baseline, then dismiss it. This is the 30% every other demo solves.

| Document | Content |
|---|---|
| eBL | 1,000 units, tablet computers, shipped 2026-09-08 |
| Commercial invoice | USD 600,000, tablet computers, 1,000 units |
| Packing list | 1,000 units |

| Condition | Verdict |
|---|---|
| eBL present | MET |
| Quantity matches (1,000 = 1,000) | MET |
| Shipped ≤ 2026-09-10 (08 Sep) | MET |

**Expected:** `RELEASE` · USD 600,000 · no findings
**Split:** supplier 576,000 · marketplace 19,800 · bank 4,200

---

### Pack 2 · Cosmetic variance

**Purpose:** the first moment the room wakes up. The system releases despite a mismatch, and says
why.

| Document | Content |
|---|---|
| eBL | 1,000 units, **"tablet PCs"**, shipped 2026-09-08 |
| Commercial invoice | USD 600,000, **"tablet PCs"**, 1,000 units |
| Packing list | 1,000 units |

Contract says *tablet computers*. Evidence says *tablet PCs*.

| Condition | Verdict | Finding |
|---|---|---|
| eBL present | MET | — |
| Goods description matches | MET **within tolerance** | `COSMETIC`, recorded with the tolerance rule that permitted it |
| Quantity matches | MET | — |
| Shipped ≤ latest date | MET | — |

**Expected:** `RELEASE` · USD 600,000 · one `COSMETIC` finding, auto-approved by tolerance profile,
extraction confidence ~0.94, tolerance rule and rule-set version cited in the journal.

**On stage:** *"A good escrow officer would have released this. So did we — and it recorded why."*

**Design note:** the finding is *recorded*, not suppressed. A `COSMETIC` finding that clears
tolerance still appears in the journal. Hiding it would defeat the audit trail.

---

### Pack 3 · Short shipment → partial release

**Purpose:** the strongest beat in the demo.

| Document | Content |
|---|---|
| eBL | **800 units**, tablet computers, shipped 2026-09-08 |
| Commercial invoice | USD 480,000, 800 units |
| Packing list | 800 units |

| Condition | Verdict | Finding |
|---|---|---|
| eBL present | MET | — |
| Quantity matches (800 ≠ 1,000) | **NOT MET** | `QUANTITY` — evidenced 800, contracted 1,000 |
| Shipped ≤ latest date | MET | — |

O2 is **severable** and the determination rule is pro-rata, so:

**Expected:** `PARTIAL_RELEASE`

| | |
|---|---|
| Released | USD 480,000 (80% of the tranche) |
| Split | supplier 460,800 · marketplace 15,840 · bank 3,360 |
| Retained | USD 120,000, re-earmarked as **residual obligation O2-R** |
| Findings notice | Single, itemised, citing the `QUANTITY` finding and the contract clause |

**On stage:** *"No system does this. Every human does this."*

**This is also the replay target.** After showing it, replay from the decision journal: same
obligation version, same evidence hashes, same rule-set version → identical determination.

---

### Pack 4 · Late shipment → approval → release

**Purpose:** the beat they discuss afterwards — a human decision re-entering the engine rather than
bypassing it.

| Document | Content |
|---|---|
| eBL | 1,000 units, tablet computers, **shipped 2026-09-13** |
| Commercial invoice | USD 600,000, 1,000 units |
| Packing list | 1,000 units |

Latest shipment date was 2026-09-10. Three days late.

| Condition | Verdict | Finding |
|---|---|---|
| eBL present | MET | — |
| Quantity matches | MET | — |
| Shipped ≤ 2026-09-10 | **NOT MET** | `TIMING` — shipped 2026-09-13, 3 days late |

`TIMING` is not severable and not auto-approvable, but it **is** approvable under the mandates in
force.

**Stage 1 — Expected:** `HOLD_PENDING_APPROVAL`

- Findings notice issued, itemised, clause cited
- Approval request drafted to the buyer
- Response window clock starts, running in **banking days**
- Funds remain earmarked; nothing moves

**Stage 2 — the buyer approves.** The approval is presented as an instruction and must pass the
admission gate:

- Buyer's Treasury Manager, exposure USD 600,000 → **exceeds the USD 50k single-signature ceiling**
- Admission returns `RequiresCountersignature` — 1 of 2
- CFO countersigns → **admitted**

This is L0 doing real work in the middle of the demo, not a stub.

**Stage 3 — the approval enters as evidence** and the engine re-examines.

| Condition | Verdict |
|---|---|
| Shipped ≤ latest date | NOT MET — **finding approved by entitled party, dual-signed** |

**Expected:** `RELEASE` · USD 600,000 · journal records the original finding, the approval, both
signatories, and the re-examination.

**On stage:** *"The approval doesn't go around the engine. It goes back into it, as evidence."*

**Negative branch worth having ready:** if the buyer declines, or the response window expires, the
determination becomes `HOLD` and the refund path opens. Have it runnable in case a judge asks.

---

## Control matrix

What each pack proves, for the "is this real" question.

| Control | Pack | Evidence |
|---|---|---|
| Deterministic examination | All | Same rule-set version, four different outcomes |
| Tolerance rules are explicit and cited | 2 | Journal names the rule that permitted release |
| Findings are recorded even when cleared | 2 | `COSMETIC` finding present on a `RELEASE` |
| Partial release with residual re-earmark | 3 | Ledger shows 480,000 out, 120,000 re-earmarked |
| Multi-payee split scales with partial release | 3 | Three payees, all pro-rated |
| Single itemised findings notice | 3, 4 | One notice, each finding, clause cited |
| Four-eyes on high-value approval | 4 | `RequiresCountersignature` then admitted |
| Human decisions re-enter as evidence | 4 | Re-examination after approval |
| Fail-closed on indeterminate evidence | — | Low-confidence extraction → no release *(add a fifth pack if time allows)* |
| Replay determinism | 3 | Identical determination from journal |
| Entitlement/earmark reconciliation | All | Control passes after every pack |

---

## Implementation notes

- **Demo determinism beats live inference.** Extraction confidences are pre-computed for the demo
  corpus. Show one live model call — on Pack 2 only, where the cosmetic variance makes the
  confidence score meaningful — and be explicit that the rest are fixtures. Stating this is
  stronger than being caught.
- **Run packs in order.** Each builds on the last rhetorically: baseline → surprise → the beat →
  the loop closing.
- **Reset between packs.** Each pack starts from the same funded deal state.
- **Keep the ledger visible.** Held balance, earmarks and entitlement should be on screen during
  Pack 3, or the partial release is just a number changing.
