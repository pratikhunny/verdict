# Evidence base

Every external number or claim used in the pitch, the HLD or the README, with its source. If a
statement is not in this file, do not assert it on stage.

---

## 1. The headline number

**60–80% of documentary credit presentations are refused on first presentation.**

- Source: International Chamber of Commerce. The figure is long-standing and widely reported in
  ICC and trade-finance literature.
- **The important part:** the rate did not materially improve when UCP 600 replaced UCP 500 in
  2007. Two decades of rule-tightening moved it very little.
- How to use it: *"Sixty to eighty percent of trade document presentations are refused on first
  submission. That's the ICC's number, and it hasn't moved in twenty years."*
- **Caution:** this is a documentary-credit statistic, not an escrow statistic. It is the best
  public proxy for how often documents fail to match terms. If challenged, concede the distinction
  immediately and pivot to the fee-schedule evidence in §2 — that evidence is about escrow directly.

---

## 2. Where escrow cost actually sits

Standard corporate escrow fee schedules price, separately:

- a **setup / acceptance fee** at execution
- an **annual administration fee**
- a **per-amendment fee** (commonly around USD 100)
- **"extraordinary" fees** for anything non-routine, negotiated case by case and often capped

**The inference — state it as an inference, not a finding:** amendments and non-routine handling
are priced separately *because* they are unpredictable and labour-intensive. Routine administration
is a flat annual fee; exceptions are metered. That is the industry telling you where the cost is.

---

## 3. UCP 600 — the rules Verdict's outcome model borrows from

| Article | Content | Why it matters here |
|---|---|---|
| Art. 2 | Defines *complying presentation*, *honour*, *banking day* | Our `RELEASE` maps to honour; all clocks run in banking days |
| Art. 14(a) | Documents examined **on their face** against the terms | The scope boundary for extraction — no investigation behind the documents |
| Art. 14(b) | Maximum **five banking days** following the day of presentation to examine | The anchor for the response-window clock |
| Art. 16(a) | A non-complying presentation may be refused | `HOLD` |
| Art. 16(b) | The issuing bank **may in its sole judgement approach the applicant for a waiver** | `HOLD_PENDING_APPROVAL` — approval is *solicited*, never assumed |
| Art. 16(c) | Refusal requires a **single notice** stating that it is refused, **each** discrepancy relied on, and the **disposal** of the documents | The findings notice must be single and itemised. A vague ground is not sufficient |
| Art. 16(d) | Notice must be given within the same five-banking-day window | |
| Art. 16(f) | Failure to give a conforming notice **precludes** the bank from claiming the presentation was discrepant | **The SLA clock is a liability control, not a service level.** Miss it and the bank must pay on flawed documents |

**Standing caveat:** UCP 600 governs documentary credits. It does **not** govern escrow. We borrow
its examination vocabulary because it is the most precise language the industry has for "documents
were checked against terms and did not match — now what". Each deal's legal characterisation comes
from its own governing contract. Say this before anyone asks.

---

## 4. Escrow agent duty — the architecture's legal backing

An escrow agent's duty is generally characterised as **ministerial**: follow the escrow instructions
strictly, with no discretion to look behind the documents presented. This is the same posture as
UCP 600 art. 14(a)'s on-their-face standard.

**Why it matters more than any technical argument:** "the model asserts facts, the rules determine"
is not a safety concession made to Risk. It is the only architecture consistent with what an agent
holding funds under instructions is permitted to do. Discretionary judgement by a model would be
inconsistent with the role itself.

> **Open — confirm with Legal before external use.** This is load-bearing for the pitch. If Legal
> will not confirm the characterisation, fall back to the weaker but still sufficient argument:
> determinism and replayability are what make conditional release auditable.

---

## 5. Standard Chartered's own public position

Use these to attach the idea to a named strategy and a named owner.

- **Philip Panaino**, Global Head of Cash Management — has publicly stated the ambition to develop
  digital escrow as a universally trusted and accepted settlement method for trade and commerce.
- **Sandrine Jourdainne** — Global Head of Deposits, Liquidity and Escrow Solutions. The business
  owner for any pilot.
- **Smart Contracts Suite** — SCB's existing platform. **Note the name collision:** never call
  Verdict a "smart contract" system; it will be heard as a claim about that platform.
- **Rene Michau**, Group Head of Digital Assets — has described public blockchains as a new
  operating system for financial services that can **coexist with rather than replace** traditional
  rails.

**Use of the last one:** it is the graceful answer to the competing stablecoin team. Their tokenised
settlement becomes an L7 adapter rather than a rival architecture, and you are quoting your own bank
rather than dismissing a colleague.

---

## 6. The integration surface — why "real-life integration" is answerable

- **eBL adoption** has risen to roughly **11%** of bills of lading, from about **1.2% in 2021**.
- **DCSA and SWIFT have demonstrated technical interoperability** between eBL platforms and SWIFT
  messaging — already done, not a roadmap item.
- **DCSA's nine carrier members have committed to 100% eBL issuance by 2030**, covering on the order
  of **45 million documents a year**.

**Why this answers the pre-hackathon feedback directly:** structured, machine-readable trade
evidence is arriving on a published timetable. An engine that examines structured evidence against
contract conditions is aimed at where the data is going, not where it has been.

- **ISO 20022** — `pacs.008` (FI-to-FI customer credit transfer) is the reference outbound
  settlement shape; `camt.053` (bank-to-customer statement) the reference inbound reconciliation
  shape.

---

## 7. Competitive context

**finhub.ai COLM / finhub.ai Trust** (DataNimbus) — positions *obligation* as the missing primitive
between contracts and payments, claims 70,000+ contracts managed, ~$1T annual money movement,
50,000 enterprises, and markets a white-labelled bank product covering 36 escrow use cases.

**Their framing is good and worth adopting:** contracts and payments live in separate systems, and
the obligation is the unit that connects them.

**Two gaps to press on:**

1. Their capability chain runs document repository → contract intelligence → obligation inference →
   payment determination → reconciliation → diligence copilot. **Nothing addresses what happens when
   evidence is ambiguous, partial or contested.** That is the 60–80% case.
2. They conflate milestone and obligation. Keeping them separate — one milestone discharging several
   obligations — is what makes multi-payee splits, fee deduction and partial release expressible.

**The "why build, not buy" answer:** a vendor sells a product. SCB already runs an orchestrator that
assembles bespoke escrow from reusable services on demand. Verdict is nodes for the engine the bank
already has — no vendor onboarding, no data-residency negotiation, and the graph composition remains
the bank's own asset.

---

## 8. Numbers we do **not** have yet

These are the gaps that would most strengthen the impact slide. Industry averages are a fallback;
SCB's own numbers are far stronger in the room.

| Needed | Why it matters | Who to ask |
|---|---|---|
| Exceptions per 100 escrow deals | Converts the ICC proxy into an SCB fact | Escrow operations |
| Ops hours per exception case | The multiplier in the impact calculation | Escrow operations |
| Amendment-fee volume | Direct revenue-leakage and client-friction measure | Product / finance |
| Average days funds sit pending exception resolution | Float and client-experience argument | Escrow operations |
| Whether SCB escrow agreements set an express determination period | Decides whether preclusion-style liability attaches | Legal / product |

**Impact framing once the numbers exist:**
`exceptions per 100 deals × ops hours per exception × loaded cost` + amendment-fee leakage +
float days released early.

---

## 9. Rules for using this file on stage

1. **Never assert a number that is not in this file.** If asked for a figure you do not have, say
   you do not have it and name who owns it. That reads as credibility, not weakness.
2. **Concede distinctions immediately.** The ICC number is documentary credits, not escrow. Say so
   before a judge does.
3. **Label inferences as inferences.** The fee-schedule argument in §2 is an inference from pricing
   structure, not a published finding.
4. **Quote SCB people accurately or not at all.** Paraphrase rather than invent quotes.
