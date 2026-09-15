# Pitch — 10 minutes, then 5 of Q&A

Shark-tank format. Judging is on **conversion of ideas to pilots to production**, quantified
business impact, and adoption into business processes. That single fact should shape every choice
below: a demo that looks *pilotable* beats a demo that looks clever.

Every number used here is sourced in [05-evidence-base.md](05-evidence-base.md). Do not assert
anything that is not in that file.

---

## The spine

> **We didn't automate the agreement. We automated the disagreement.**

---

## Script

### 0:00–1:00 · The number

Open cold. No agenda slide, no team introduction.

> "Sixty to eighty percent of trade document presentations are refused on first submission. That's
> the ICC's number, and it hasn't moved in twenty years — not through UCP 500, not through UCP 600.
>
> Every one of those refusals is a human, an email chain, and an amendment fee. That is what escrow
> actually costs. Not the holding of money — the *arguing* about it."

### 1:00–2:30 · The confession

> "Here's what we built for the pre-hackathon round."

Run the clean path. Fast. Contract in, obligations out, funds ring-fenced, evidence matches, money
moves. Then stop.

> "It works. And it's a party trick. Because that's the thirty percent. Every escrow demo you'll see
> today solves the thirty percent."

**Why this beat exists:** it disarms every competing pitch in the room and pre-empts your own
weakness. Judges remember the team that named its own gap. It also directly answers the
pre-hackathon feedback rather than pretending it wasn't given.

### 2:30–6:30 · The four packs

Same contract. Same engine. Four evidence packs. Narrate only what is happening — the system should
carry it. Specs in [04-scenarios.md](04-scenarios.md).

| Pack | Outcome | The line |
|---|---|---|
| Clean | `RELEASE` | — (move fast) |
| "tablet PCs" vs "tablet computers" | `RELEASE` | *"A good escrow officer would have released this. So did we."* |
| 800 of 1,000 shipped | `PARTIAL_RELEASE` | *"No system does this. Every human does this."* |
| Three days late | `HOLD_PENDING_APPROVAL` → approve → `RELEASE` | *"The approval doesn't go around the engine. It goes back into it, as evidence."* |

The fourth is the one they discuss at lunch: a human decision re-entering the machine rather than
bypassing it.

### 6:30–7:30 · The replay

> "Six months later, compliance asks why this money moved."

Hit replay on the partial release. Same obligation version, same evidence hashes, same rule-set
version, same determination, clause cited.

> "The model reads. The model never decides. Rules decide, deterministically, and every decision is
> replayable. That's the difference between a demo and something Risk will actually sign."

### 7:30–8:30 · The graph

One slide. Four graphs, same node library.

> "We didn't build a marketplace escrow product. We built the nodes. The graph is configuration.
> Holdback, retention, conditional payout — different graph, same engine, no new code."

### 8:30–9:30 · The pilot ask

Name the mocks, show the interfaces, name the owner.

> "Everything you saw is real except three adapters: screening, document extraction, and the
> settlement rail. Here's the one file that changes for each."
>
> "Philip Panaino has stated the ambition to make digital escrow a universally trusted settlement
> method for trade and commerce. Sandrine Jourdainne owns Escrow Solutions globally. We're asking
> for one live corridor and one deal type."

### 9:30–10:00 · The close

> "Escrow costs what it costs because banks pay people to argue about paperwork.
>
> We didn't automate the agreement. We automated the disagreement."

---

## Slides

Keep it to seven. The demo is the pitch; slides are punctuation.

| # | Slide | Purpose |
|---|---|---|
| 1 | The 60–80% number, alone on a dark field | Cold open |
| 2 | Where escrow cost sits — the fee schedule | Establishes the inference honestly |
| 3 | *(no slide — live demo, four packs)* | |
| 4 | Models assert / rules determine — the boundary drawn | The Risk answer, visual |
| 5 | Four graphs, one node library | The platform claim |
| 6 | What's real, what's mocked, what changes to go live | The pilot answer |
| 7 | The ask: one corridor, one deal type, named owner | Close |

---

## Q&A preparation

Five minutes, and the questions are predictable. Answer in two sentences, then stop.

**"Would Risk ever let an LLM move money?"**
It doesn't. Extraction proposes facts with confidence and provenance; a deterministic rule engine
determines the outcome, and an architecture test fails the build if the examination package imports
anything from the extraction package. That separation isn't a concession to Risk — an agent holding
funds under instructions has a limited, non-discretionary duty, so it's the only architecture
consistent with the role.

**"Why build this rather than buy it? Vendors sell escrow platforms."**
A vendor sells a product. We already run an orchestrator that assembles bespoke escrow from reusable
services on demand — this is new nodes for the engine the bank already has. No vendor onboarding, no
data-residency negotiation, and the composition stays our asset.

**"What about the blockchain and stablecoin team?"**
Their settlement is our L7 adapter. Rene Michau has said public blockchains can coexist with rather
than replace traditional rails, and our rail port already has a tokenised-deposit implementation
alongside book transfer and RTGS. *(Be generous here. It's a stronger position than competing.)*

**"What's the quantified business impact?"**
The honest answer: exceptions per hundred deals × ops hours per exception × loaded cost, plus
amendment-fee leakage and float days released early. We have industry proxies; what we want from a
pilot is our own numbers, and the instrumentation to produce them is in the build.
*(Do not invent a figure. See evidence base §8.)*

**"How is this different from the pre-hackathon submission?"**
That build automated the compliant case. The feedback asked for a real lifecycle — so we built the
lifecycle that actually costs money: short-shipped, late, contested, partially released, approved,
replayed.

**"Is this UCP 600 governed?"**
No. UCP 600 governs documentary credits. We borrow its examination vocabulary because it's the most
precise language the industry has for evidence that doesn't match terms. Each deal's legal
characterisation comes from its own governing contract.

**"What happens when extraction gets it wrong?"**
Confidence below threshold makes the condition indeterminate, and indeterminate never releases — the
system fails closed into human examination. A wrong fact at high confidence produces a wrong
determination, which is why the journal records the fact, its source document hash and the adapter
version: the error is attributable and the determination is reversible.

**"How long to a pilot?"**
Three adapters — screening, extraction, one settlement rail — against interfaces that already exist.
The integration estimate is a file list rather than a discovery phase. What we need from the bank is
one corridor and one deal type.

**"Does this handle multi-currency?"**
Not yet, deliberately. FX inside an authority control needs a rate source and an as-of policy, and
guessing at either in a prototype would be worse than declining. Single deal currency today;
currency scope is already modelled in the mandate constraints.

---

## Delivery notes

- **Do not read the slides.** They are punctuation.
- **Let the demo sit in silence** during the partial-release beat. Resist narrating.
- **Concede distinctions before they're raised** — the ICC number is documentary credits, not
  escrow; the fee-schedule argument is an inference. Conceding early reads as command of the
  material.
- **Name what is mocked, unprompted.** It converts a demo into a pilot conversation.
- **If asked something unknown, say so and name who owns the answer.** Never improvise a number.
- **Be generous about the other teams.** The room notices.
