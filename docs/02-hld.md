# Verdict — High Level Design

**Accelerate 3.0 · Smart Escrow Network · Transaction Banking**
**Version:** 1 · **Date:** 2026-09-15 · **Owner:** Pratik Kumar
**Reads with:** [Glossary](01-glossary-v2.md) · [ADR-001](adr/0001-three-plane-separation.md)

---

## 1. The problem

Escrow's cost is not holding money. It is resolving the cases where the evidence does not match the
terms.

The ICC's own figures put refusal of documentary presentations on first submission at **60–80%**,
a rate that did not improve when UCP 600 replaced UCP 500 in 2007. Every refusal becomes an ops
analyst, an email chain, and an amendment fee. Standard escrow fee schedules price this openly:
a setup fee, an annual administration fee, a per-amendment fee, and separately negotiated
"extraordinary" fees for anything non-routine.

Most conditional-payment systems, including our own pre-hackathon build, automate the compliant
case. That is the minority of presentations.

**Verdict automates the disagreement.** It examines evidence against contract conditions, and
returns one of four determinations — release, partial release, hold pending approval, or hold —
with an itemised findings notice and a replayable record of why.

---

## 2. Scope of this document

| In | Out |
|---|---|
| Container structure, domain model, node/graph contract, lifecycle, NFRs, scope split | Database DDL, API schemas, rule DSL grammar — see LLD |

---

## 3. Context

```mermaid
flowchart TB
    payer["Payer<br/><i>funds the deal</i>"]
    payee["Payee<br/><i>performs, gets paid</i>"]
    ops["Escrow Operations<br/><i>bank, maker-checker</i>"]
    mkt["Marketplace / Platform<br/><i>originates deals</i>"]

    verdict{{"<b>VERDICT</b><br/>conditional hold, examine,<br/>split and release"}}

    cif[("Client master<br/><i>party data</i>")]
    screen[("Screening<br/><i>sanctions, CDD</i>")]
    docs[("Evidence sources<br/><i>eBL, invoices, ERP</i>")]
    rails[("Settlement rails<br/><i>book transfer, RTGS,<br/>tokenised deposit</i>")]
    core[("Core banking<br/><i>nostro, reconciliation</i>")]

    payer -->|"funds, approves findings"| verdict
    payee -->|"submits evidence"| verdict
    mkt -->|"creates deals via API"| verdict
    ops -->|"examines escalations"| verdict

    verdict <-->|"party lookup"| cif
    verdict -->|"screen before instruction"| screen
    verdict <-->|"evidence intake"| docs
    verdict -->|"disbursement"| rails
    verdict <-->|"balance, recon"| core

    style verdict fill:#1a3a2e,color:#fff,stroke:#0d5c3f,stroke-width:3px
```

---

## 4. Container view — the three planes

Per [ADR-001](adr/0001-three-plane-separation.md), the system separates what *should* happen, what
*did* happen, and *why*. The determination is the only contract between the first two.

```mermaid
flowchart TB
    subgraph exp["L9 · Experience"]
        api["Deal API"]
        console["Ops console"]
        portal["Counterparty portal"]
    end

    subgraph orch["Orchestrator — graphs over nodes"]
        dag["Graph runtime<br/><i>deal type = configuration</i>"]
    end

    subgraph dec["DECISION PLANE — what should happen"]
        l0["L0 · Party &amp; Authority<br/><i>mandates, quorum, admission</i>"]
        l1["L1 · Contract Registry<br/><i>versions, amendments</i>"]
        l2["L2 · Obligation Model<br/><i>milestones, conditions</i>"]
        l4["L4 · Examination Engine<br/><i>deterministic rules</i>"]
        l5["L5 · Case &amp; Approval<br/><i>escalation, maker-checker</i>"]
    end

    subgraph ev["EVIDENCE PLANE — why it happened"]
        l3["L3 · Evidence Intake<br/><i>adapters, extracted facts</i>"]
        l8["L8 · Decision Journal<br/><i>append-only, replayable</i>"]
    end

    subgraph mon["MONEY PLANE — what did happen"]
        l6["L6 · Fund Control Ledger<br/><i>double-entry, earmarks</i>"]
        l7["L7 · Settlement<br/><i>rail adapters, idempotent</i>"]
    end

    exp --> orch
    orch --> dec
    l3 -->|"extracted facts<br/>(never decisions)"| l4
    l2 --> l4
    l4 -->|"DETERMINATION<br/>the only contract"| l6
    l4 --> l5
    l5 -->|"approval enters<br/>as evidence"| l3
    l6 --> l7
    l4 -.->|"journal entry"| l8
    l6 -.->|"journal entry"| l8
    l0 -.->|"admits every<br/>instruction"| orch

    recon{{"Reconciliation control<br/>Σ entitlements = Σ earmarks"}}
    l2 -.-> recon
    l6 -.-> recon

    style dec fill:#e8f0ec,stroke:#0d5c3f,stroke-width:2px
    style mon fill:#fdf0e6,stroke:#b5651d,stroke-width:2px
    style ev fill:#eceef5,stroke:#3a4a7a,stroke-width:2px
    style recon fill:#fff3cd,stroke:#856404,stroke-width:2px
```

**The two boundaries that define the system:**

1. **L3 → L4.** Extraction adapters assert facts with confidence. They never decide. The examination
   engine contains no model call.
2. **L4 → L6.** The decision plane emits a determination. It never writes a posting. The money plane
   has no opinion on whether a release is correct.

---

## 5. Domain model

```mermaid
erDiagram
    PARTY ||--o{ RESPONSIBILITY : "holds in deal"
    PARTY ||--o{ SIGNATORY : "acts through"
    RESPONSIBILITY ||--o{ MANDATE : "carries"
    MANDATE ||--o{ MANDATE_CONSTRAINT : "bounded by"

    DEAL ||--|{ CONTRACT : "governed by"
    DEAL ||--|{ RESPONSIBILITY : "involves"
    DEAL ||--|{ MILESTONE : "contains"
    DEAL ||--|| HELD_BALANCE : "holds"

    CONTRACT ||--|{ CONTRACT_VERSION : "versioned as"
    CONTRACT_VERSION ||--o{ OBLIGATION : "gives rise to"

    MILESTONE ||--|{ CONDITION : "evaluated by"
    MILESTONE ||--o{ OBLIGATION : "triggers"

    OBLIGATION ||--o{ DETERMINATION : "resolved by"
    OBLIGATION ||--o| OBLIGATION : "residual of"

    SUBMISSION ||--|{ DOCUMENT : "contains"
    DOCUMENT ||--|{ EXTRACTED_FACT : "yields"
    SUBMISSION ||--o{ DETERMINATION : "examined into"

    DETERMINATION ||--o{ FINDING : "records"
    DETERMINATION ||--o| EARMARK : "reserves"
    DETERMINATION ||--o| DISBURSEMENT : "pays"
    DETERMINATION ||--|| JOURNAL_ENTRY : "justified by"

    HELD_BALANCE ||--o{ EARMARK : "reserved into"
    HELD_BALANCE ||--|{ POSTING : "moved by"
    DISBURSEMENT ||--|{ POSTING : "effected by"

    OBLIGATION {
        string obligation_id PK
        string contract_version_id FK
        string milestone_id FK
        string payer_party_id FK
        string payee_party_id FK
        string determination_rule
        boolean severable
        string clause_reference
        int version
    }
    MILESTONE {
        string milestone_id PK
        string name
        string state
        date latest_date
    }
    CONDITION {
        string condition_id PK
        string milestone_id FK
        string expression
        string evidence_type
    }
    DETERMINATION {
        string determination_id PK
        string outcome
        decimal amount
        string rule_set_version
        timestamp effective_at
    }
    FINDING {
        string finding_id PK
        string grade
        string condition_id FK
        string clause_reference
        decimal confidence
    }
    EARMARK {
        string earmark_id PK
        decimal amount
        string state
    }
```

**The distinction that carries the product:** a **milestone** is a trigger; an **obligation** is an
entitlement. One milestone discharges several obligations — 60% to the supplier, 2% commission to
the marketplace, a fee to the bank. Systems that collapse the two cannot express multi-payee splits,
fee deduction, or partial release cleanly.

---

## 6. Nodes and graphs — the platform claim

Nodes are code: reviewed, versioned, tested. Graphs are configuration. **"Different graph, same
nodes" must remain literally true**, or the platform claim is marketing.

```mermaid
flowchart LR
    subgraph lib["Node library — code, shared by every deal type"]
        n1["INGEST<br/>contract"]
        n2["DERIVE<br/>obligations"]
        n3["COLLECT<br/>funds"]
        n4["EARMARK"]
        n5["INTAKE<br/>evidence"]
        n6["EXAMINE"]
        n7["DETERMINE"]
        n8["SOLICIT<br/>approval"]
        n9["SPLIT"]
        n10["DISBURSE"]
        n11["TIME<br/>window"]
        n12["REFUND"]
    end

    style lib fill:#e8f0ec,stroke:#0d5c3f,stroke-width:2px
```

```mermaid
flowchart TB
    subgraph g1["Marketplace trade — hero case"]
        direction LR
        a1["COLLECT"] --> a2["EARMARK"] --> a3["INTAKE"] --> a4["EXAMINE"] --> a5["DETERMINE"] --> a6["SPLIT"] --> a7["DISBURSE"]
        a5 -.->|"findings"| a8["SOLICIT<br/>approval"]
        a8 -.-> a4
    end

    subgraph g2["M&amp;A holdback"]
        direction LR
        b1["COLLECT"] --> b2["TIME<br/>window"] --> b3["INTAKE<br/><i>claim</i>"] --> b4["EXAMINE"] --> b5["DETERMINE"] --> b6["SPLIT"] --> b7["DISBURSE"]
    end

    subgraph g3["Construction retention"]
        direction LR
        c1["COLLECT"] --> c2["INTAKE<br/><i>certificate</i>"] --> c3["EXAMINE"] --> c4["DETERMINE"] --> c5["EARMARK<br/><i>retention</i>"] --> c6["TIME<br/>window"] --> c7["DISBURSE"]
    end

    subgraph g4["Conditional payout"]
        direction LR
        d1["COLLECT"] --> d2["INTAKE<br/><i>event</i>"] --> d3["EXAMINE"] --> d4["DETERMINE"] --> d5["DISBURSE"]
    end

    style g1 fill:#e8f0ec,stroke:#0d5c3f,stroke-width:3px
    style g2 fill:#f7f7f7,stroke:#999
    style g3 fill:#f7f7f7,stroke:#999
    style g4 fill:#f7f7f7,stroke:#999
```

Four deal types. One node library. No new code.

### Node contract

Every node satisfies the same interface, which is what makes composition safe:

| Element | Rule |
|---|---|
| Input | Immutable deal context as of an instant |
| Output | Zero or more domain events; never direct state mutation |
| Authority | Any state-changing node presents an instruction to L0 admission first |
| Idempotency | Keyed on (deal, node, correlation key); re-execution is a no-op |
| Journal | Emits exactly one journal entry per execution |
| Determinism | Given the same input and rule-set version, produces the same output |

---

## 7. Obligation lifecycle

```mermaid
stateDiagram-v2
    [*] --> DERIVED: obligation extracted from contract
    DERIVED --> EARMARKED: funds collected and reserved
    EARMARKED --> UNDER_EXAMINATION: evidence submitted

    UNDER_EXAMINATION --> RELEASED: conditions met
    UNDER_EXAMINATION --> PARTIALLY_RELEASED: met in part, severable
    UNDER_EXAMINATION --> HELD_PENDING_APPROVAL: findings, waivable
    UNDER_EXAMINATION --> HELD: findings, not waivable

    PARTIALLY_RELEASED --> [*]: residual obligation created
    HELD_PENDING_APPROVAL --> UNDER_EXAMINATION: approval received, re-examined
    HELD_PENDING_APPROVAL --> HELD: approval declined
    HELD_PENDING_APPROVAL --> HELD: response window elapsed

    HELD --> UNDER_EXAMINATION: fresh evidence submitted
    HELD --> REFUNDED: deal terminated
    RELEASED --> [*]
    REFUNDED --> [*]

    note right of HELD_PENDING_APPROVAL
        Response window runs in banking days.
        Under documentary-credit rules a bank
        that misses it loses the right to refuse.
        This clock is a liability control.
    end note
```

---

## 8. The hero flow — short shipment, partial release

```mermaid
sequenceDiagram
    autonumber
    participant Payee as Payee
    participant L3 as L3 Evidence
    participant L0 as L0 Authority
    participant L4 as L4 Examination
    participant L6 as L6 Ledger
    participant L8 as L8 Journal
    participant Payer as Payer

    Payee->>L3: submit eBL + invoice + packing list
    L3->>L3: extract facts, hash docs, record confidence
    Note over L3: quantity = 800<br/>contracted = 1,000
    L3->>L4: extracted facts (assertions only)

    L4->>L4: evaluate each condition
    Note over L4: shipment evidenced ✓<br/>quantity matched ✗ (QUANTITY)<br/>within latest date ✓
    L4->>L4: obligation severable → PARTIAL_RELEASE
    L4->>L8: journal entry (facts, rules, outcome)

    L4->>L0: instruct disbursement, 80% of tranche
    L0-->>L4: admitted (agent mandate)
    L4->>L6: determination → release 80%, retain 20%
    L6->>L6: post disbursement, re-earmark residual
    L6->>L8: journal entry (postings)

    L4->>Payer: findings notice — QUANTITY, clause 4.2
    Payer->>L3: approve finding (enters as evidence)
    L3->>L4: re-examine
    L4->>L6: release residual
```

---

## 9. Scope — build, mock, defer

The honest split. Everything mocked is an **adapter behind a port**, so the file that changes to go
live is nameable.

| Layer | Hackathon | Why |
|---|---|---|
| **L0 Party & Authority** | **Built** | Mandates, quorum, four-eyes, admission gate. Done. |
| L0 Screening | **Mocked** | Port is real and called on every instruction; implementation returns canned results. |
| **L1 Contract Registry** | **Built, thin** | Versioning and clause references only. No redlining, no drafting. |
| **L2 Obligation Model** | **Built** | The primitive. Schema is the idea. |
| L2 Contract → obligation extraction | **Mocked** | LLM call over a fixed corpus; obligations also authorable by hand. |
| **L3 Evidence Intake** | **Partly built** | eBL and invoice adapters real over a fixed corpus; confidences pre-baked for demo determinism. |
| **L4 Examination Engine** | **Built** | Four determinations, five finding grades, findings notice. The core. |
| **L5 Case & Approval** | **Built, thin** | Escalation, approval solicitation, maker-checker. No SLA dashboards. |
| **L6 Fund Control Ledger** | **Built** | Double-entry, earmarks, invariants. Never mocked, even in a hackathon. |
| **L6 Reconciliation control** | **Built** | Σ entitlements = Σ earmarks. The control that justifies ADR-001. |
| L7 Settlement | **Mocked** | Emits a well-formed pacs.008 and logs it. Rail port is real. |
| **L8 Decision Journal** | **Built** | Append-only with a working replay. The trust close. |
| **L9 Experience** | **Built, minimal** | One ops console, one counterparty view. Not five screens. |
| Orchestrator graph runtime | **Built, thin** | Enough to execute the marketplace graph and show three others as configuration. |
| Multi-currency FX | **Deferred** | Single deal currency. FX inside a control needs a rate source and an as-of policy. |
| Mandate constraint framework | **Deferred** | Amount ceiling and currency scope only. Velocity, beneficiary and corridor scope designed, not built. |
| Physical plane separation | **Deferred** | Logical separation by module. ADR-001 option D remains open. |

---

## 10. Non-functional requirements

| Property | Target | How |
|---|---|---|
| **Replay determinism** | Any determination reproduces its identical outcome indefinitely | No clock reads in decision logic; effective instant supplied; rule-set and adapter versions pinned in the journal |
| **Idempotency** | Re-delivery causes no duplicate movement | Instruction id as idempotency key through ledger and settlement |
| **Auditability** | Every movement traceable to evidence, rule and actor | One journal entry per determination and posting, written in the same transaction |
| **Ledger integrity** | No negative balance, no over-earmark | Database constraints plus invariant check; postings never amended |
| **Authority** | No instruction without a resolvable mandate | Single admission gate; no internal bypass path |
| **Fail-closed** | Unevaluable control refuses | Screening PENDING refuses; indeterminate condition does not release |

---

## 11. Deployment

Single Spring Boot service, single Postgres. Plane separation enforced by module boundaries, not
network hops — deliberately, per ADR-001. Runs locally with `docker compose up`.

```mermaid
flowchart LR
    ui["Web UI<br/><i>static</i>"] --> app["Verdict service<br/><i>Spring Boot, Java 21</i>"]
    app --> pg[("PostgreSQL<br/><i>3 schemas: decision, money, evidence</i>")]
    app -.->|"mocked"| ext["Adapters<br/><i>screening, rails, extraction</i>"]

    style app fill:#1a3a2e,color:#fff
```

---

## 12. Why this converts to a pilot

- Every mocked component is a **named port with one implementation to write** — screening, rails,
  extraction. The integration estimate is a file list, not a discovery phase.
- The vocabulary is neutral, so the same engine serves escrow, trade finance and conditional payouts
  without re-platforming. Legal characterisation stays with each deal's governing contract.
- Settlement rails are pluggable, including tokenised deposit — so digital-asset settlement is an
  adapter here, not a competing architecture.
- The reconciliation control and decision journal are the two things Risk asks for first, and both
  are built rather than promised.
