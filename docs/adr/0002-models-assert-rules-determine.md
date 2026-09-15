# ADR-002 — Models assert facts; rules determine outcomes

| | |
|---|---|
| **Status** | Accepted |
| **Date** | 2026-08-22 |
| **Deciders** | Pratik Kumar |
| **Related** | ADR-001 (three planes), ADR-003 (obligation as primitive) |

---

## Context

Verdict examines unstructured and semi-structured evidence — bills of lading, invoices, inspection
certificates — against contract conditions, and the outcome moves money. Language models are very
good at reading those documents and unsuitable for deciding what to do about them: they are
non-deterministic, unversionable in the sense that matters for audit, and cannot be replayed to an
identical answer years later.

There is also a legal dimension that is stronger than the technical one. An agent holding funds
under instructions has a **limited, non-discretionary duty** — it follows the instructions and does
not look behind the documents presented. This is the same posture as documentary-credit examination,
which is expressly limited to what documents show on their face. A model exercising judgement over
whether a release is warranted would be inconsistent with the role itself, not merely risky.

The failure mode we are guarding against is not a model hallucinating once. It is a system in which
nobody can say, after the fact, *why* money moved.

---

## Decision

**Extraction adapters assert facts. Deterministic rules determine outcomes. The boundary is
enforced mechanically.**

1. An adapter converts a document into **extracted facts**, each carrying source document hash,
   adapter version, and per-field confidence. An adapter has no access to conditions, obligations,
   rules, or the ledger.
2. The **examination engine contains no model call**. It is a pure function of
   (obligation version, conditions, extracted facts, tolerance profile, rule-set version).
3. **Confidence is an input to the rules, not a decision by the model.** A decisive fact below the
   configured threshold makes the condition *indeterminate*, and indeterminate never releases —
   the system fails closed into human examination.
4. The boundary is enforced by an **architecture test that fails the build** if the examination
   package imports anything from the extraction package. ADR-001's plane separation means
   extraction cannot reach the money plane even in principle.

---

## Options considered

**A. Model determines the outcome, human reviews.** Fastest to demo, and what most prototypes do.
Rejected: non-replayable, inconsistent with a non-discretionary duty, and review-fatigue makes the
human a rubber stamp.

**B. Model proposes a determination, rules validate it.** Superficially safer. Rejected: the
proposal anchors the outcome, and the validation surface is larger than simply computing the
determination. It also leaves the model in the decision path for audit purposes.

**C. Model asserts facts, rules determine** *(chosen)*.

**D. No model at all — templates and regex only.** Deterministic, and too brittle for real
documents. Rejected, but note that C degrades gracefully to D: a template adapter satisfies the same
port.

---

## Consequences

### Positive

- Determinations replay identically, indefinitely.
- Adapter quality can improve independently; swapping an adapter does not change how outcomes are
  computed, only the facts fed in.
- The architecture answers the "would Risk allow an LLM to move money" question structurally rather
  than by assurance.
- Extraction errors are attributable: the journal holds the fact, its source document hash, and the
  adapter version.

### Negative — accepted

- **Rules must be written.** Model judgement is not available as a shortcut for ambiguous cases;
  tolerance profiles and grade policies are explicit configuration that someone must author and
  maintain.
- **More indeterminate outcomes.** Failing closed means more escalation to humans than a
  model-decides design would produce. This is the correct trade and should be stated as such.
- **A confidently wrong fact still produces a wrong determination.** The mitigation is
  attribution and reversibility, not prevention.

---

## Prepared answer

> "It doesn't. Extraction proposes facts with confidence and provenance; a deterministic rule engine
> determines the outcome, and an architecture test fails the build if the examination package
> imports anything from the extraction package. That separation isn't a concession to Risk — an
> agent holding funds under instructions has a limited, non-discretionary duty, so it's the only
> architecture consistent with the role."

---

## Compliance

- Architecture test asserting no forbidden import from examination to extraction.
- Every extracted fact carries source hash, adapter version and confidence — all non-nullable.
- Confidence thresholds are configuration, versioned with the rule set and pinned in the journal.
