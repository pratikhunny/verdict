# Verdict

**Programmable conditional settlement — an engine that decides what to do when the evidence
*doesn't* match the terms.**

Accelerate 3.0 · Smart Escrow Network · Transaction Banking · Escrow Solutions

---

## The problem in one paragraph

Escrow does not cost money because holding funds is hard. It costs money because **60–80% of trade
document presentations are refused on first submission** — an ICC figure that did not improve when
UCP 600 replaced UCP 500 in 2007. Every refusal becomes an operations analyst, an email chain and an
amendment fee. Standard escrow fee schedules price this openly: a setup fee, an annual
administration fee, a per-amendment fee, and separately negotiated "extraordinary" fees for anything
non-routine.

Most conditional-payment systems automate the *compliant* case. That is the minority of
presentations.

**Verdict automates the disagreement.** It examines evidence against contract conditions and returns
one of four determinations — `RELEASE`, `PARTIAL_RELEASE`, `HOLD_PENDING_APPROVAL`, `HOLD` — with an
itemised findings notice and a replayable record of why.

---

## What makes it a bank system rather than a demo

| | |
|---|---|
| **Models assert, rules determine** | Extraction adapters emit facts with confidence and provenance. The examination engine contains **no model call**. Enforced by an architecture test, not by convention. |
| **Every determination replays** | Same obligation version + evidence hashes + rule-set version → identical outcome, indefinitely. No clock reads in decision logic. |
| **Partial release is real** | An obligation evidenced at 800 of 1,000 units releases 80% of the tranche and re-earmarks the remainder as a residual obligation. Every human escrow officer does this; almost no system does. |
| **Human decisions re-enter as evidence** | A counterparty approving findings is recorded as evidence and re-examined. There is no path by which a human writes a determination directly. |
| **Entitlement and balance are separate** | Computed independently in different planes, then reconciled. If they disagree, an alert fires — a control that cannot exist when they are the same record. |

---

## Repository map

```
CLAUDE.md                    Working context — read this first
docs/
  01-glossary-v2.md          Vocabulary, ICC/escrow mapping, banned terms
  02-hld.md                  Containers, domain model, node/graph contract, lifecycle, scope
  03-pitch.md                10-minute demo script and Q&A preparation
  04-scenarios.md            Hero deal and the four evidence packs, as executable specs
  05-evidence-base.md        Every external number cited, with its source
  adr/
    0001-three-plane-separation.md
    0002-models-assert-rules-determine.md
    0003-obligation-as-primitive.md
    0004-nodes-are-code-graphs-are-configuration.md
    0005-entitlement-separate-from-balance.md
src/main/java/com/sc/verdict/
  shared/                    Money (currency-safe), Ids (typed identifiers)
  party/                     L0 — parties, responsibilities, mandates, admission gate
  obligation/                L2 — milestones, conditions, obligations   (in progress)
  evidence/                  L3 — submissions, documents, extracted facts (in progress)
  examination/               L4 — verdicts, findings, determinations     (in progress)
src/test/java/com/sc/verdict/
  party/AuthorityScenarios.java   12 executable authority scenarios
scripts/run-l0.sh            Compile and run L0 with no build tool
```

---

## Running it

### Prerequisites

- JDK 21 (`java -version` should report 21 or later)

### L0 — Party & Authority

No build tool required.

```bash
./scripts/run-l0.sh
```

Or directly:

```bash
javac -d out $(find src -name '*.java')
java -cp out com.sc.verdict.party.AuthorityScenarios
```

Expected output: a table of 12 scenarios, all reporting `OK`, ending with
`All 12 scenarios behaved as specified.`

### What the L0 scenarios demonstrate

| Scenario | Control proved |
|---|---|
| Treasury approves USD 5k | Mandate ceiling honoured |
| Treasury approves USD 80k alone | Quorum shortfall → awaiting countersignature, **not** a rejection |
| Treasury + CFO approve USD 80k | Dual control satisfied by distinct signers |
| Treasury countersigns own instruction | **Four-eyes** — one person cannot satisfy dual control |
| MYR against a USD-only mandate | Currency scope enforced; no implicit FX inside a control |
| MYR falls through to an uncapped dual mandate | Constraint failure narrows the mandate, it does not reject the instruction |
| Marketplace observer instructs | Read-only responsibilities cannot instruct |
| Supplier attempts to release to itself | No mandate for that instruction type |
| Bank operations releases | The bank is an ordinary party bound by the same rules |
| Back-dated instruction | Temporal resolution — authority as at the effective instant, not today |
| Screening hit | Financial-crime gate, fails closed |
| Corporate with no named signatory | A corporate acts only through a natural person |

---

## Architecture in one diagram

Three planes. The **determination** is the only contract from decision to money, and nothing crosses
back. See [ADR-001](docs/adr/0001-three-plane-separation.md).

| Plane | Answers | Mutability |
|---|---|---|
| **Decision** | What *should* happen | Versioned; new versions, never edits |
| **Money** | What *did* happen | Append-only postings; corrections are reversing postings |
| **Evidence** | *Why* it happened | Append-only, never mutated |

Full container, domain and sequence diagrams: [docs/02-hld.md](docs/02-hld.md).

---

## Build status

| Layer | Status |
|---|---|
| L0 Party & Authority | **Complete** — 12 scenarios passing |
| L2 Obligation model | In progress |
| L4 Examination engine | In progress |
| L6 Ledger, earmarks, reconciliation | In progress |
| L8 Decision journal and replay | In progress |
| L5 Case & approval | Planned, thin |
| Orchestrator (graph runtime) | Planned, thin |
| L1 contract registry · L3 extraction · L7 settlement · screening | **Mocked behind real ports** |

Everything mocked sits behind an interface, so the file that changes to go live is nameable. Scope
rationale: [docs/02-hld.md §9](docs/02-hld.md).

---

## Target stack

Java 21 · Spring Boot 3.x · PostgreSQL (Docker Compose) · Flyway · minimal web UI.

The domain core is deliberately free of framework imports: obligations, examination, ledger and
journal compile and run as plain Java, so the logic is testable without Spring. Controllers,
persistence and configuration are a thin shell around it.

---

## Licence and status

Internal prototype built for Accelerate 3.0. Not a released product. Nothing here constitutes a
legal characterisation of any arrangement — each deal's characterisation comes from its own
governing contract. See the standing caveat in
[docs/01-glossary-v2.md, Appendix A](docs/01-glossary-v2.md).
