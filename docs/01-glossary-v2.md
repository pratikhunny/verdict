# Verdict — Domain Glossary and Ubiquitous Language

**Version:** 2 · **Status:** Draft · **Owner:** Pratik Kumar · **Audience:** engineers, product, risk
**Supersedes:** v1, which used ICC and escrow-law terms as the primary vocabulary.

---

## 0. Naming policy

**One vocabulary, deliberately neutral, legally grounded.**

Verdict runs conditional hold-and-release for arrangements that are not all escrow: marketplace
settlement, trade finance, conditional vendor payouts, retention and holdback structures. The
mechanics are identical — hold funds, evaluate conditions against evidence, split, release. The
*legal characterisation* differs per deal and is set by the governing agreement, not by our schema.

So the model uses neutral operational nouns. Where a term has an established legal or ICC analogue,
the definition names it, and Appendix A maps the full set. Three rules:

1. **Neutral in the model, precise in the mapping.** `Finding` in the schema; when a trade-finance
   practitioner asks, the answer is "what UCP 600 Article 16 calls a discrepancy" — and we can
   show the mapping rather than improvise it.
2. **Naming does not change substance.** If the bank holds client money and releases on conditions,
   regulatory treatment follows what it *is*. Neutral vocabulary buys product reach, never
   regulatory relief. Never argue otherwise, internally or on stage.
3. **Coined terms are declared.** Appendix B lists every word we invented and why. If a term is
   not in this document, it does not appear in code, schema, API or deck.

---

## 1. Deal structure

| Term | Definition |
|---|---|
| **Deal** | The container for one arrangement: its contracts, parties, obligations, balance and journal. The top-level aggregate. Where the arrangement is a traditional escrow, the deal corresponds to one escrow agreement — but a deal need not be an escrow. |
| **Contract** | A governing document of the deal and its lineage: the underlying commercial agreement, any escrow or account-bank instrument, and their amendments. Versions are immutable; an amendment produces a new version with a diff, never an edit. |
| **Party** | A legal entity or natural person known to the platform, independent of any deal. Global and versioned. |
| **Responsibility** | A party's effective-dated capacity within one deal — payer, payee, agent, verifier, observer. A party may hold several in one deal: a payer on the funding obligation is a payee on the refund path. Effective-dated because parties are replaced mid-deal by novation or assignment. |
| **Mandate** | What a responsibility may actually instruct: instruction type, constraints, and quorum. Standard bank account-mandate practice applied per deal. |
| **Instruction** | A request to change state, presented for admission before any layer acts. Carries its own effective instant so admission is reproducible. |
| **Signatory** | A natural person authorised to act for a corporate party. |
| **Platform authority** vs **deal authority** | Two planes. Platform authority governs who configures the system; deal authority governs who instructs money. They never cross. |

---

## 2. Milestones and obligations

> **The distinction that matters most in this document.** A milestone is a *trigger*; an obligation
> is an *entitlement*. One milestone can discharge several obligations — shipment evidenced releases
> 60% to the supplier, 2% commission to the marketplace and a fee to the bank. Systems that collapse
> the two cannot express fee splits, multi-payee settlement, or partial release cleanly.

| Term | Definition |
|---|---|
| **Milestone** | A named point in the deal lifecycle whose conditions are evaluated together. Reaching a milestone triggers determination of the obligations attached to it. |
| **Condition** | One independently testable element of a milestone. "Shipment evidenced, quantity matching, shipped by the latest date" is three conditions, not one. Each carries its own verdict, which is what makes partial release and itemised findings possible. |
| **Obligation** | A payment commitment contingent on a milestone: who pays, who receives, how much, evidenced by what, by when. Anchored to a clause of a specific contract version. Every movement of money in Verdict discharges an obligation. |
| **Determination rule** | How the payable amount is computed once conditions are met — fixed, percentage, pro-rated to evidenced quantity, tiered. Deterministic; no model involvement. |
| **Severability** | Whether an obligation can be discharged in part. A quantity-based obligation is severable; a single-delivery obligation may not be. Must be a stated property, not a judgement call, or partial release cannot be deterministic. |
| **Latest date** | The last date on which an act may occur under the terms — shipment, delivery, presentation of evidence. |
| **Response window** | The period within which the platform must determine and notify. Expressed in **banking days**, not calendar days, following market practice. Not a service-level target: under documentary-credit rules a bank that misses the equivalent window loses the right to refuse and must pay. Treat the clock as a liability control. |

---

## 3. Evidence

| Term | Definition |
|---|---|
| **Submission** | A set of documents or events delivered in support of one or more obligations. The unit of examination — not the individual document. |
| **Document** | One artifact within a submission, stored immutably with its hash. |
| **Extracted fact** | A structured assertion produced from a document by an adapter, carrying source, document hash, adapter version and per-field confidence. **Facts are asserted, never decided upon.** |
| **Provenance** | The full chain behind a fact: which document, which version of which adapter, at what confidence, at what time. |
| **On-the-face standard** | Examination is limited to what the documents show on their face; the platform does not investigate the underlying transaction. This is the scope boundary for extraction, and it mirrors both documentary-credit examination practice and the limited, non-discretionary duty an escrow agent owes under its instructions. |
| **eBL** | Electronic bill of lading conforming to the DCSA standard. A structured evidence adapter, not a scanned document. Distinct from a PDF of a bill of lading, which lacks the exclusivity of control required of an electronic transferable record. |

---

## 4. Examination and outcome

| Term | Definition |
|---|---|
| **Examination** | Evaluating a submission's extracted facts against the conditions of a milestone. |
| **Verdict** | The outcome for one condition: met, not met, or indeterminate. |
| **Finding** | A recorded mismatch between evidence and terms. The industry term in documentary credits is *discrepancy*; we use the neutral word because most Verdict deals are not documentary credits. |
| **Finding grade** | The class of a finding. Drives both the determination rules and the mandate constraints — see §5. |
| **Determination** | The outcome for a whole obligation, composed from its condition verdicts. Exactly one of the four below. |
| **Approval** | A counterparty's acceptance of stated findings, permitting release notwithstanding. Solicited, never assumed. Enters the system as **evidence** and is re-examined; it is not a bypass. Use only in this sense — never as a loose synonym for a mandate exercise or countersignature. |
| **Findings notice** | The single artifact issued when release is withheld, stating each finding relied on, its grade, the contract clause engaged, and what happens to the funds. Single and itemised: multiple partial notices, or a vague ground such as "conflicting data", are not sufficient in documentary practice and will not be sufficient here. |

### The four determinations

| Determination | Meaning |
|---|---|
| `RELEASE` | Conditions met. Disburse the determined amount. |
| `PARTIAL_RELEASE` | Conditions met in part, and the obligation is severable. Disburse the evidenced portion; retain the remainder against a residual obligation. |
| `HOLD_PENDING_APPROVAL` | Findings present, but waivable under the mandates in force. Findings notice issued **and** approval solicited from the entitled party. |
| `HOLD` | Findings present, not waivable or approval declined. Findings notice issued; funds retained pending instruction. |

---

## 5. Finding grades

Shared vocabulary between the examination rules and the authority layer, so a mandate can say
"Treasury may approve cosmetic findings to USD 50,000; only the CFO may approve a timing finding,
at any value." Categories are drawn from the common causes of documentary refusal.

| Grade | Definition | Worked example |
|---|---|---|
| `COSMETIC` | Data conflicts not affecting identity, quantity, value or timing. | Invoice reads "tablet PCs"; contract reads "tablet computers". |
| `QUANTITY` | Evidenced quantity or value differs from terms. Severable — the usual route to partial release. | eBL evidences 800 units against 1,000 contracted. |
| `TIMING` | An act occurred outside a permitted window. | Shipment three days after the latest shipment date. |
| `MISSING_DOCUMENT` | A stipulated document is absent, unsigned or unendorsed. | Inspection certificate not presented. |
| `SUBSTANTIVE` | Goes to identity of parties, goods, or the instrument. Never auto-approvable. | Consignee is not the named payee. |

---

## 6. Money

| Term | Definition |
|---|---|
| **Posting** | An immutable double-entry record. Never amended; corrections are reversing postings. |
| **Held balance** | Funds held under one deal, segregated from the bank's own assets. |
| **Earmark** | A reservation of held balance against a specific obligation. Invariant: sum of earmarks ≤ available balance. |
| **Entitlement** | What a party is *owed* under determined obligations, as distinct from **held balance**, which is what is *held*. Keeping these separate is the defect this architecture exists to avoid. |
| **Disbursement** | Payment out of held balance to a payee. |
| **Settlement rail** | The mechanism effecting a disbursement: internal book transfer, RTGS, correspondent, tokenised deposit. Pluggable. |
| **pacs.008 / camt.053** | ISO 20022 reference message shapes for outbound settlement and inbound reconciliation respectively. |

---

## 7. Orchestration

| Term | Definition |
|---|---|
| **Node** | A unit of platform behaviour composable into a graph — obligation extraction, examination, determination, earmark, disbursement. Code: reviewed, versioned, tested. |
| **Graph** | The ordered composition of nodes defining one deal type. Configuration, not code. "Different graph, same nodes" is the platform claim and must remain literally true. |
| **Deal type** | A named graph plus its default rule set: marketplace trade, holdback, retention, conditional payout. |
| **Decision journal** | Append-only record binding obligation version, evidence hashes, rule-set version, determination, actor and resulting postings — sufficient to replay any determination to the identical outcome. |

---

## Appendix A — Legal and ICC mapping

For regulated conversations, audit, and Q&A. Read left to right when a practitioner uses the
legal term; right to left when explaining our model to one.

| Verdict term | Documentary credit / escrow analogue | Source |
|---|---|---|
| Deal | Escrow arrangement under an escrow agreement | escrow practice |
| Contract | Escrow agreement; underlying sale contract | escrow practice |
| Responsibility | Deal role; agent, depositor, beneficiary | escrow practice |
| Submission | Presentation | UCP 600 art. 2 |
| Conditions met | Complying presentation | UCP 600 art. 2 |
| Examination, on-the-face standard | Examination "on their face" | UCP 600 art. 14(a) |
| Finding | Discrepancy | UCP 600 art. 16; ISBP 821 |
| `RELEASE` | Honour | UCP 600 art. 2 |
| `PARTIAL_RELEASE` | *No analogue* — UCP has no partial-honour concept | — |
| `HOLD` / `HOLD_PENDING_APPROVAL` | Refusal | UCP 600 art. 16(a) |
| Findings notice | Single notice of refusal stating each discrepancy and the disposal of documents | UCP 600 art. 16(c) |
| Approval | Waiver, solicited from the applicant | UCP 600 art. 16(b) |
| Response window | Five banking days; missing it precludes the bank from asserting discrepancy | UCP 600 arts. 14(b), 16(d), 16(f) |
| Banking day | Banking day | UCP 600 art. 2 |
| eBL, control | Electronic transferable record | UNCITRAL MLETR 2017; DCSA |
| Posting, held balance | Double-entry posting; segregated client money | accounting; escrow practice |

**Standing caveat:** Verdict is not governed by UCP 600. UCP 600 governs documentary credits. We
borrow its examination vocabulary because it is the most precise language the industry has for
"documents were checked against terms and did not match — now what". Each deal's legal
characterisation comes from its own governing contract.

---

## Appendix B — Terms we coined

| Term | Why no existing term served |
|---|---|
| **Condition** (as an independently determinable sub-unit) | Documentary practice treats a presentation as one pass/fail. Per-condition verdicts are what make partial release and itemised notices possible. |
| **Finding**, **finding grade** | Neutral substitute for *discrepancy*, needed because most deals are not documentary credits. The grade taxonomy is ours. |
| **Partial release** | No documentary-credit analogue exists; escrow practice does release against evidenced portions. We name the gap rather than pretend it is covered. |
| **Earmark** | Chosen over "hold", which we already use for a determination outcome. Two meanings of "hold" in one system is a defect waiting to happen. |
| **Entitlement** | Needed a word for the decision-plane counterpart of held balance. |
| **Decision journal** | An audit log records events; this records the *grounds* of a determination, sufficient to reproduce it. |
| **Responsibility**, **node**, **graph**, **deal type** | Structural terms with no financial-domain equivalent. |

---

## Appendix C — Terms not to use

| Banned | Reason | Use instead |
|---|---|---|
| **Escrow agreement**, **presentation**, **discrepancy**, **honour**, **waiver** in schema, API or code | Import legal characterisation we do not control and narrow the product to traditional escrow. Correct in Appendix A conversations; wrong in the model. | Contract, submission, finding, release, approval |
| **Smart contract** | Implies on-chain irrevocability we do not offer, and collides with SCB's existing Smart Contracts Suite. | Programmable release, graph |
| **Exception** | Overloaded with the JVM meaning throughout the codebase. | Finding |
| **Approval** used loosely | Reserved for a counterparty accepting findings. Using it for a mandate exercise or a countersignature hides which act occurred. | Name the specific act |
| **Trigger** | Vague about whether evidence, time or instruction caused it. | Milestone, condition, event |
| **AI decides / AI releases** | Factually wrong about this architecture and fatal in a risk conversation. | The adapter asserts facts; the rules determine |
| **Validation** for document checking | Also means schema checking. | Examination (evidence), validation (schema) |

---

## Appendix D — Open questions

1. **Non-discretionary duty.** The architecture rests on the claim that an agent holding funds under
   instructions has a limited, non-discretionary role and does not look behind the documents. Confirm
   the characterisation with Legal before it appears in external material — it is load-bearing.
2. **Finding grades.** Needs review by an escrow ops practitioner against real refusal reasons. The
   `COSMETIC` / `SUBSTANTIVE` boundary is the one most likely to be contested.
3. **Response window.** Do SCB escrow and account-bank agreements set an express determination
   period, or are they silent? Determines whether preclusion-style liability attaches.
4. **Severability.** Under what conditions is an obligation severable? Needs a stated rule, or
   partial release cannot be deterministic — and partial release is the demo's strongest beat.
5. **"Milestone" in the problem statement.** The hackathon brief says milestone-based payments.
   We keep the word, but with the milestone/obligation split above. Confirm the escrow team reads
   "milestone" the same way.
