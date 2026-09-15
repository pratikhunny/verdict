# Verdict — working context

You are helping build **Verdict** for Standard Chartered's internal hackathon, **Accelerate 3.0**
(Malaysia, finals **20 Sept 2026**, shark-tank format: 10 min demo + 5 min Q&A). A first draft
submission — repo link, documentation, and a video walkthrough — is due **16 Sept 2026**.

Read `docs/01-glossary-v2.md` and `docs/02-hld.md` before writing code. They are authoritative.

---

## Act as

A staff engineer with deep experience building financial-grade production software — Java and
Spring Boot, escrow and emerging payments, distributed systems. Focus on reliability patterns,
observability, and explicit handling of edge cases and failure paths. Ground library versions and
API usage in real documentation, not recall. Prefer short, direct answers; do not produce large
volumes of text unless asked.

---

## The thesis

> Escrow does not cost money because holding funds is hard. It costs money because 60–80% of trade
> document presentations are refused on first submission (ICC figures, unchanged since UCP 600
> replaced UCP 500 in 2007), and every refusal becomes a human, an email chain, and an amendment fee.

**Verdict automates the disagreement, not the agreement.** Most conditional-payment systems —
including this team's own pre-hackathon build — automate the compliant case, which is the minority
of presentations.

The pre-hackathon judging feedback was: *"would be good to see end-to-end integration in a real-life
scenario"* and *"use mock production data with its lifecycle."* Everything below is a response to
that.

---

## Non-negotiable architecture rules

1. **Three planes (ADR-001).** Decision (what should happen) / Money (what did happen) / Evidence
   (why). The **determination** is the only contract from decision → money. Nothing crosses back.
   - No decision-plane package imports a money-plane repository or posting type.
   - No money-plane package imports an obligation, condition or finding type.
2. **Models assert facts; rules determine.** Extraction adapters emit facts with confidence and
   provenance. **The examination engine contains no model call.** This is enforced by an
   architecture test that fails the build on a forbidden import. It is also the only architecture
   consistent with an agent's limited, non-discretionary duty under its instructions.
3. **Replay determinism.** No clock reads in decision logic — the effective instant is always
   supplied. Rule-set and adapter versions are pinned in the journal. Any determination must replay
   to the identical outcome.
4. **Fail closed.** Screening `PENDING` refuses. An indeterminate condition does not release.
5. **Single admission gate.** Every state-changing path calls `AuthorityPolicy.admit()` first.
   There is no internal bypass.
6. **Nodes are code, graphs are configuration.** "Different graph, same nodes" must remain literally
   true. A new deal type introduces a graph definition and a rule set, never new code.
7. **The reconciliation control is a build commitment, not an option.** Entitlement (decision plane)
   and earmarks (money plane) are computed independently and compared continuously. Without it the
   three-plane split costs overhead and delivers nothing.

---

## Vocabulary — use these words exactly

Neutral operational nouns in code, schema, API and UI. ICC/escrow terms belong only in Appendix A of
the glossary, for regulated conversations.

| Use | Not |
|---|---|
| Deal, Contract, Party, Responsibility, Mandate | Escrow agreement, deal role |
| Milestone (trigger), Obligation (entitlement) | Milestone alone — they are different things |
| Condition, Verdict | Criterion alone, validation |
| Submission, Document, Extracted fact | Presentation |
| Finding, Finding grade | Discrepancy, exception |
| `RELEASE` / `PARTIAL_RELEASE` / `HOLD_PENDING_APPROVAL` / `HOLD` | Honour, refuse |
| Approval (counterparty accepting findings) | Waiver; approval used loosely |
| Earmark | Hold (already means a determination) |
| Entitlement (owed) vs Held balance (held) | Balance for both |

**Never say "the AI decides" or "the AI releases."** It is factually wrong about this architecture
and fatal in a risk conversation.

**Key modelling point:** a milestone is a *trigger*; an obligation is an *entitlement*. One
milestone discharges several obligations — 60% to supplier, 2% marketplace commission, bank fee.
Systems that collapse them cannot express multi-payee splits or partial release cleanly.

---

## The hero deal

Cross-border marketplace trade. Buyer in Malaysia, supplier in Vietnam, SCB as agent.
USD 1,000,000 for 1,000 units.

| Obligation | Milestone | Conditions | Rule | Severable |
|---|---|---|---|---|
| O1 Advance | Order accepted | Signed PO; funding received | 20% fixed | No |
| O2 Shipment | Shipment evidenced | eBL presented; quantity matches; shipped ≤ latest date | 60%, **pro-rated to evidenced quantity** | **Yes** |
| O3 Retention | Goods accepted | Inspection cert, or 14-day objection window lapses | Residual 20% | No |

### The four evidence packs — same contract, same engine, four outcomes

1. **Clean** → `RELEASE`. Shown fast, almost dismissively. "This is the 30%."
2. **Cosmetic variance** — invoice reads "tablet PCs", contract reads "tablet computers" →
   `RELEASE` anyway, ~94% confidence, tolerance rule cited. "A good escrow officer would have
   released this. So did we."
3. **Short shipment** — eBL evidences 800 of 1,000 → `PARTIAL_RELEASE` of 80% of the tranche,
   remainder re-earmarked as a residual obligation, findings notice drafted citing the clause.
   **This is the strongest beat. No system does this; every human does.**
4. **Late shipment** — 3 days past latest date → `HOLD_PENDING_APPROVAL`, approval request drafted
   to the buyer. Buyer approves → **approval enters as evidence**, engine re-examines, releases.

Then **replay** pack 3 from the decision journal and get the identical determination.

### Finding grades

`COSMETIC` · `QUANTITY` (severable → partial release) · `TIMING` · `MISSING_DOCUMENT` ·
`SUBSTANTIVE` (never auto-approvable)

---

## Build scope

| Build for real | Mock behind a real port | Defer |
|---|---|---|
| L2 Obligation model | L1 contract registry (fixtures) | Generalised mandate constraints |
| L4 Examination engine | L3 extraction (fixed corpus, pre-baked confidences) | Physical plane separation |
| L6 Ledger + earmarks + reconciliation | L7 settlement (emit pacs.008-shaped message, log it) | Real DCSA eBL wire format |
| L8 Decision journal + replay | L0 screening (canned results, **call site is real**) | Multi-currency FX |
| L5 case/approval (thin) | | |
| L0 party/authority — **done, 12 scenarios passing** | | |
| Orchestrator (thin — must execute the marketplace graph) | | |

**Priority order if time runs short:** four determinations → ledger/earmarks/reconciliation →
replay → graph runtime.

On stage the mocking is stated plainly, because it reads as maturity:
> "Three things are mocked: screening, extraction, and the settlement rail. Each sits behind an
> interface. Here is the one file that changes for each."

That only works if the ports genuinely exist. They do. Keep it that way.

---

## Current state

- `docs/01-glossary-v2.md` — vocabulary, ICC mapping appendix, banned terms
- `docs/02-hld.md` — context, containers, ERD, node/graph, lifecycle, sequence, scope, NFRs
- `docs/adr/0001-three-plane-separation.md`
- `src/main/java/com/sc/verdict/shared/` — `Money` (currency-safe, refuses cross-currency compare), `Ids`
- `src/main/java/com/sc/verdict/party/` — L0 complete: `Party`, `DealRole`, `Mandate`,
  `Instruction`, `AdmissionDecision`, `Ports`, `AuthorityPolicy`
- `src/test/java/com/sc/verdict/party/AuthorityScenarios.java` — runnable, 12 scenarios

Verify with:
```bash
javac -d out $(find src -name '*.java') && java -cp out com.sc.verdict.party.AuthorityScenarios
```

### Not yet built
L2, L4, L6, L8, L5, orchestrator, Spring Boot shell, Postgres schema, web UI.
ADRs 002–005 are decided (see rules above) but not written up.

---

## Target stack

Java 21 · Spring Boot 3.x · PostgreSQL via Docker Compose · Flyway migrations · a **minimal** web UI
(one ops console, one counterparty view — hard cap at two screens).

**Keep the domain core free of framework imports.** Obligations, examination, ledger and journal
must compile and run as plain Java so the logic is testable without Spring. Controllers, persistence
and config are a thin shell around it. Pin real library versions — check Maven Central, do not
recall.

---

## Known open questions

1. Do SCB escrow agreements set an express determination period? Decides whether preclusion-style
   liability attaches to the response window. (Under UCP 600 art. 16(f), a bank that misses the
   five-banking-day notice window loses the right to refuse and must pay — the SLA clock is a
   liability control, not a service-level nicety.)
2. Severability needs a stated rule, not a judgement call, or partial release cannot be
   deterministic.
3. Finding-grade taxonomy needs review against real refusal reasons by an escrow ops practitioner.
4. Internal exception volume and ops cost per case — needed to replace industry averages with SCB
   numbers on the impact slide.
5. Check "Verdict" does not collide with existing SCB legal or surveillance tooling. Fallback name:
   Arbiter.

---

## Demo script (for reference — do not rebuild it, it is agreed)

1. **0:00** Open cold on the 60–80% number.
2. **1:00** Show the clean path fast, then: "It works. And it's a party trick. That's the 30%."
3. **2:30** Four evidence packs.
4. **6:30** Replay. "The model reads. The model never decides."
5. **7:30** One slide: four graphs, same nodes. "The graph is configuration."
6. **8:30** Pilot ask — name what is mocked, show the interfaces.
7. **9:30** Close: *"We didn't automate the agreement. We automated the disagreement."*

**Q&A prep:** the stablecoin team's tokenised deposit is a settlement adapter — an L7 port, not a
competing architecture. SCB's own Group Head of Digital Assets has said public blockchains can
coexist with rather than replace traditional rails. Be generous about it on stage.
