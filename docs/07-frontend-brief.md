# Verdict — Frontend Brief (React build)

**Audience:** the engineer/agent building the new React frontend.
**Companion doc:** [`08-api-reference.md`](08-api-reference.md) — every endpoint, type, and call sequence. This
brief is the *what and why*; that one is the *exact wire*.

> Read this once end-to-end before writing a component. The backend is **done, running, and frozen** —
> your job is a frontend that makes a 10-minute demo land with judges. Do not change API shapes; if you
> think one is wrong, flag it, don't rename it.

---

## 1. The 20-second pitch (memorize this — the UI must sell it)

> Escrow doesn't cost money because holding funds is hard. It costs money because **60–80% of trade-document
> presentations are refused on first submission** — and every refusal becomes a human, an email chain, and a
> fee. Most systems automate the *clean* case, which is the minority. **Verdict automates the disagreement.**
> The model reads the documents; a deterministic engine decides; and every decision replays to the identical
> result. Same contract, four different evidence packs, four correct outcomes — including the one no software
> does and every human does: a **partial release**.

The closing line of the pitch — and the phrase the UI exists to earn — is:

> **"We didn't automate the agreement. We automated the disagreement."**

Everything you build should make a judge *feel* that sentence by minute 8.

---

## 2. What we're building

Two screens, one backend, no auth. This is a **demo instrument**, not a CRUD app — optimize for clarity on a
projector at 3 metres, and for a presenter clicking through a script live.

| Screen | Route | Who it is | Purpose |
|---|---|---|---|
| **Ops console** | `/` | The bank's escrow operations officer | Run a transaction end to end: pick the deal, open an order, fund, earmark, take in documents, watch the model extract facts, examine, land a **verdict**, disburse. The main stage. |
| **Counterparty portal** | `/portal` | The buyer (payer) | See what's been routed to them for approval; approve a held finding, which re-enters the engine as evidence and releases. The "four-eyes" beat. |

They share one in-memory backend, so an action on one screen is visible on the other (a HOLD on the console
lights up a badge on the portal). Keep both on the same origin.

---

## 3. The two deal types (the platform's punchline: *different graph, same engine*)

The whole architecture claim is "a new deal type is **configuration, not code**." The UI must make that
literal by rendering *both* convincingly from the same components, driven entirely by API data.

| | **Marketplace trade** (the hero) | **Construction retention (RERA)** |
|---|---|---|
| Story | Cross-border trade: buyer in Malaysia, supplier in Vietnam, SCB as agent. USD 1M for 1,000 units. | India real-estate escrow: homebuyer's funds ring-fenced; bank pays the developer per construction milestone, verified by an engineer's completion certificate. |
| Release rule | **Pro-rata / severable** — short shipment → *partial* release, remainder re-earmarked. | **Binary** — certified completion ≥ threshold → release the whole tranche, or hold. Never pro-rata. |
| Payees | Three (supplier 96%, marketplace 3.3%, bank 0.7% of the tranche) | One (the developer) |
| Human-in-the-loop | Late shipment → buyer approves the timing finding | Objection/lien recorded → homebuyer approves over the objection |
| Graph | 7 nodes + a dashed "solicit approval" loop | Same nodes, a different branch |

The **deal-type selector at the very top drives the whole page**: contract terms, parties, obligations, the
graph, the order form, and the sample documents all change with it. Selecting a transaction pins its deal type
back into the selector so they can never disagree.

---

## 4. The demo narrative → what must be on screen at each beat

This is the agreed 10-minute script (`03-pitch.md`). Build the UI so the presenter never has to apologise for it.

| Time | Beat | The UI has to make this effortless |
|---|---|---|
| 0:00 | The 60–80% number | (slide, not UI) |
| 1:00 | **Clean path, fast.** "It works. And it's a party trick. That's the 30%." | New order → Fill for demo → fund → earmark → load **Clean** → extract → examine → **RELEASE**. Should take ~15 seconds and feel dismissively easy. |
| 2:30 | **Four evidence packs, same contract.** Clean · Cosmetic · **Short** · Late. | Same lifecycle, swap the sample. The **Short shipment → PARTIAL RELEASE** is the emotional peak — see §6. |
| 6:30 | **Replay.** "The model reads. The model never decides." | One click on a journal entry re-runs the determination and shows **REPLAY IDENTICAL**. |
| 7:30 | **Four graphs, same nodes.** | Switch the deal type to Construction; the graph, parties, and rules change with zero code. |
| 8:30 | **Pilot ask — name what's mocked.** | The extraction toggle (Deterministic rules ⇄ Live AI) is the visible "one file changes" proof. |
| 9:30 | Close on the tagline. | The masthead already carries it. |

---

## 5. Screen 1 — Ops console: information architecture

Two bands, top to bottom. Everything in **Setup** is "configured once, reused by every transaction";
everything in **Operations** is "per transaction — its own hold → evaluate → pay cycle." Keep that split visible.

### SETUP band
1. **The four conceptual levels** (a horizontal explainer strip). This is load-bearing — judges ask "what's
   fixed vs dynamic." Left→right, with scope badges:
   - **① ② Master agreement** *(Template · fixed)* — roles, their mandates, the rules. No companies, no amounts.
   - **③ Parties (entities)** *(Onboarded once)* — real legal entities, KYC/screened once, reused across deals.
   - **④ Participants** *(Fixed for this deal)* — which onboarded entity fills each role.
   - **⑤ Transaction (an order)** *(Per transaction · dynamic)* — quantity, dates, amount, evidence.
2. **Deal type & graph** — the deal-type `<select>` + the branching graph (a real DAG, see §6).
3. **Agreement terms, rules & mandates** *(Template)* — term chips, the obligations/split table (by **role**,
   in **%**, not amounts), the conditions the engine examines, and the roles→mandates table.
4. **Parties (entities)** *(Onboarded once)* + **Participants (who fills each role)** *(Fixed for this deal)* —
   two side-by-side cards. Keep entity (one-time onboarding) separate from role-binding (per deal).

### OPERATIONS band
5. **Transactions** — a "+ New transaction" button that opens the **order form** (see §6), plus chips for each
   open transaction (id + deal type + outcome, colour-coded).
6. The **lifecycle**, in a two-column grid. Left column is the staged pipeline; right column is the live state.
   - **Ⓐ Parties & amount** *(Per transaction)* — the fixed counterparties + the *entered* order terms.
   - **Ⓑ Collect funds** *(Money plane)* — Fund button + the held/disbursed/earmarked/unallocated tiles + the
     reconciliation strip.
   - **Ⓒ Earmark to payees** *(Money plane)* — reserve each payee's entitlement into a sub-wallet.
   - **Ⓓ Documents** *(Evidence)* — upload a real file, or load a sample set; shows the raw document text.
   - **Ⓔ AI extraction** *(Evidence)* — the model asserts facts with a **confidence bar per field**; a toggle
     switches Deterministic rules ⇄ Live AI. *The model reads; it never decides* — say this on the card.
   - **Ⓕ Examination** *(Decision)* — the deterministic engine's per-condition verdicts (MET / NOT MET + grade).
   - **Ⓖ Determination & disbursement** *(Decision → Money)* — **the verdict card** (see §6), the split, residuals,
     the findings notice, and the Disburse button.
   - **↺ Approval (four-eyes)** — appears only on HOLD_PENDING_APPROVAL; approve here or on the portal.
   - **Right rail:** the **escrow wallet tree**, the **flow stepper** (this transaction's position on the graph),
     and the **decision journal** (append-only, each row replayable).

Every card carries (a) a **scope badge** saying which of the four levels it belongs to, and (b) a one-line
**"⚡ In production, triggered by …"** note — because in a real deployment there is no human clicking these; a
payment webhook funds, a document arrival triggers intake, etc. That honesty reads as maturity to judges.

---

## 6. The five moments to get *right* (everything else is support)

These are where you spend your polish budget. If these five sing, the demo wins.

1. **The order form + "✨ Fill for demo."** Opening a transaction must feel like a product, not a canned demo.
   Clicking "+ New transaction" opens a form whose fields come from the API (`GET /orderform`), each with a
   label, help text, and unit suffix. A prominent **Fill for demo** button populates every field with the
   canonical values in one click. The entered values are **real** — they drive the engine (a typed quantity of
   800 against a 1,000-unit eBL genuinely flips the outcome). Sell that: "these aren't decoration; change the
   number and the verdict changes."

2. **The verdict card (Ⓖ).** This is the peak. When examination returns, the outcome should land like a stamp:
   a large, colour-coded outcome word (**RELEASE / PARTIAL RELEASE / HOLD PENDING APPROVAL / HOLD**), the
   released vs retained figures as big tabular numbers, and — critically — **one plain-English sentence** of
   what it means. For the hero PARTIAL_RELEASE: *"A severable condition fell short. USD 480,000 of 600,000
   releases now, pro-rated to the evidence; the remainder is re-earmarked as a residual obligation — not
   refused. No system does this automatically; every escrow officer does it by hand."* Then the per-payee split
   table, the residual (re-earmarked) table, and the dark **findings notice** (monospace, reads like a real
   bank telex — the API returns the exact text in `findingsNotice`).

3. **The escrow wallet as a tree that *moves*.** Not flat tiles — a hierarchy: **Escrow pool (held)** → per-payee
   **earmark sub-wallets** (+ an "unallocated / future milestones" node when > 0) → **paid out** on disburse.
   Money visibly flows down the tree as the lifecycle advances. Pair it with the **reconciliation** strip —
   *Σ entitlements = Σ earmarks*, a green "RECONCILED" (or red "DRIFT"). That strip is the proof that the
   decision plane and money plane agree; make it quietly always-present.

4. **The branching graph with a live current node.** A real DAG drawn from `{nodes, edges}` (nodes carry `x,y`;
   edges are explicit transitions, some `dashed` = conditional branches). Not a linear chip strip. The node the
   current transaction sits on **glows**; the dashed "solicit approval / resolve objection" branch is visibly a
   detour off the happy path. Switching deal type swaps the whole graph — that *is* the "same nodes, different
   graph" claim, on screen.

5. **Replay.** In the journal, each determination row has a **Replay** button. One click → **REPLAY IDENTICAL**
   (green), showing the re-run outcome and amount equal the original. Small surface, huge trust payload: it's how
   you answer "so the AI decides who gets paid?" with "no — watch."

Supporting but worth care: the **confidence bars** (grow from 0, green ≥ 90% / gold ≥ 75% / red below), the
**four-outcome contrast** (the transaction chips colour-code by outcome so all four can sit side by side), and
the **extraction toggle** (Deterministic ⇄ Live AI, showing the model label / "no key").

---

## 7. Design language

Financial-grade, calm, confident. Clean over clever. The vibe is "a bank actually shipped this," not "a
hackathon slide." Design for light mode (a projector-friendly, enterprise look); dark mode is optional.

### Brand & plane palette (use these exact tokens)
```
SCB green      #38B449   deep #0E6E3C     ← primary / brand / RELEASE
SCB blue       #0072CE   deep #00539B     ← brand secondary / links / section numerals
```
The system has **three planes**; give each its own hue and use it consistently on cards, badges, and graph nodes:
```
Decision plane   green   ink #0E6E3C   soft #e8f3ec   line #bfe0cc   ← examine, determine, verdict
Money plane      gold    ink #B0770E   soft #faf2e2   line #ecd7a8   ← fund, earmark, disburse, wallet
Evidence plane   indigo  ink #3B4CA0   soft #edeffb   line #cdd3f0   ← documents, extraction, journal
```
Outcome colours:
```
RELEASE                 green  #0E6E3C
PARTIAL_RELEASE         gold   #B0770E
HOLD_PENDING_APPROVAL   orange #C2410C
HOLD                    red    #B42318
```
Neutrals: ink `#131722`, secondary ink `#3a4353`, muted `#667085`, faint `#98a2b3`, hairline `#e6e9ef`,
page bg `#f4f6f8`, card `#ffffff`. Graph node kinds map to the planes: `money`→gold, `decision`→green,
`evidence`→indigo, `branch`→orange.

### Type, space, motion
- **Type:** a clean system stack (`-apple-system / Segoe UI / Roboto`) is fine and renders as SF Pro on the demo
  Mac — no web-font network dependency at the venue. Strong hierarchy is what reads as premium: a display weight
  for headings and big money numbers, tabular figures for all amounts (`font-variant-numeric: tabular-nums`).
- **Space:** generous. Cards ~16px radius, soft layered shadows, hairline borders, a category accent bar on the
  left edge of each card in its plane colour.
- **Motion (subtle, purposeful):** cards settle in on mount; confidence bars grow from zero; the current graph
  node glows/pulses; the verdict badge stamps in. Respect `prefers-reduced-motion`. Never gratuitous.

You are free to use Tailwind, CSS Modules, vanilla-extract, whatever — but keep the tokens above. `framer-motion`
is a reasonable optional dependency for the four motion beats.

---

## 8. Vocabulary — non-negotiable (this is a bank; words are risk)

Use these exact words in all UI copy. They are enforced culture, not preference (`01-glossary-v2.md`).

| Say | Never say |
|---|---|
| Deal, Contract, Party, Responsibility, Mandate | Escrow agreement, deal role |
| Milestone (trigger) vs Obligation (entitlement) | "Milestone" for both |
| Condition, Verdict, Determination | Criterion, validation |
| Submission, Document, Extracted fact | Presentation |
| Finding, Finding grade | Discrepancy, exception |
| RELEASE / PARTIAL_RELEASE / HOLD_PENDING_APPROVAL / HOLD | Honour, refuse |
| Approval | Waiver |
| Earmark | Hold (that means a determination) |
| Entitlement (owed) vs Held balance (held) | "Balance" for both |

**Never write "the AI decides" or "the AI releases."** It is factually wrong about this architecture and fatal
in a risk conversation. The model **asserts facts with confidence**; the deterministic **engine determines**.
Put that distinction on the extraction and examination cards in words.

---

## 9. Component inventory (suggested) → data source

Build these as reusable, data-driven components. The right-hand column is the endpoint(s) each reads (see
`08-api-reference.md`). Nothing here is hard-coded per deal type — it's all API data.

| Component | Reads |
|---|---|
| `<Masthead>` (logo, tagline, nav with pending badge) | — / `GET /transactions` for the badge count |
| `<DealTypePicker>` | `GET /dealtypes` |
| `<FourLevelsStrip>` | static copy (in §5) |
| `<DealGraph>` (SVG DAG, highlights current node) | `GET /graph?dealType=` + current step derived from a `TransactionView` |
| `<AgreementTerms>` (chips, obligations table, conditions, mandates) | `GET /contract?dealType=` |
| `<PartiesGrid>` / `<ParticipantsTable>` | `GET /contract?dealType=` (`parties`, `responsibilities`) |
| `<TransactionChips>` | `GET /transactions` |
| `<OrderForm>` (+ Fill for demo) | `GET /orderform?dealType=` → `POST /transactions` |
| `<OrderSummary>` (Ⓐ counterparties + order terms) | `GET /transactions/{id}` |
| `<FundCard>` / `<EarmarkCard>` | `POST …/fund`, `POST …/earmark` → `GET …/ledger` |
| `<DocumentsCard>` (upload / sample / clear + doc text) | `POST …/documents`, `POST …/samples/{key}`, `DELETE …/documents`, `GET/POST …/extract` |
| `<ExtractionPanel>` (facts + confidence bars + mode toggle) | `POST …/extract`, `GET/POST /extraction` |
| `<ExaminationTable>` (per-condition verdicts) | `POST …/examine` |
| `<VerdictCard>` (the peak — outcome, meaning, split, residuals, notice, disburse) | `POST …/examine`, `POST …/disburse` |
| `<ApprovalCard>` / portal approval | `GET …/pending`, `POST …/approve` |
| `<WalletTree>` + `<Reconciliation>` | `GET …/ledger` |
| `<FlowStepper>` | derived from `TransactionView` booleans |
| `<Journal>` (+ Replay) | `GET …/journal`, `POST …/replay/{detId}` |

---

## 10. States, errors, and edge cases (don't skip — this is what "senior" looks like)

- **Lifecycle gating.** Each action is only valid at a certain stage. Derive button enablement from the
  `TransactionView` booleans (`funded`, `earmarked`, `extracted`, `determined`, `disbursed`) — the exact table is
  in `08-api-reference.md §6`. Disable, don't hide, so the pipeline reads as a sequence.
- **Errors are 409 with `{ "error": "<message>" }`.** Surface the message inline near the action (a toast or a
  small red line), never a blank failure. Example: funding twice returns `"TXN-0002 is already funded"`.
- **Loading & empty.** Every panel needs an empty state ("Earmark the wallet, then add documents.") and a loading
  state. The demo is live; never show a raw spinner-of-death or an unstyled flash.
- **Locked after determination.** Once a transaction has a determination, document intake is closed (the backend
  enforces it). Show a gentle "settled — start a new transaction for another cycle" note rather than letting the
  user hit a 409.
- **Live AI unavailable.** If `GET /extraction` reports `liveAvailable: false`, the toggle should still show but
  say "needs key," and extracting in LLM mode returns a 409 — catch it and suggest switching back to rules.
- **Portal with nothing pending.** Show a calm empty state, not an error.

---

## 11. Tech & wiring

- **Stack:** Vite + React + TypeScript. State: React Query (TanStack Query) is a strong fit — the whole app is
  fetch-render-mutate-refetch, and its cache invalidation maps cleanly onto "after examine, refetch ledger +
  journal + transactions." Plain `useState`/`useEffect` is fine too; don't over-architect.
- **Dev:** the backend runs at `http://localhost:8080`. Use a Vite dev proxy so `'/api'` → `:8080` (config in
  `08-api-reference.md §2`). In production the Spring app serves the built assets from the same origin, so all
  calls are same-origin `'/api/...'` — **never hard-code `localhost` in the client.**
- **Types:** copy the TypeScript definitions from `08-api-reference.md §4` verbatim; they mirror the server DTOs.
- **Two routes:** `/` (ops console) and `/portal`. React Router or a tiny hash router — your call.
- **Don't** add auth, a backend, or persistence. Don't rename API fields. Don't invent endpoints — if you need
  data that isn't there, flag it; the backend owner will add it.

---

## 12. Definition of done

- [ ] Both deal types render fully from API data — switching the deal type changes contract, parties,
      obligations, graph, order form, and samples with **no per-type code**.
- [ ] The complete lifecycle runs from the UI for the hero **Short shipment** pack and lands **PARTIAL RELEASE
      480,000** with the residual re-earmarked, then disburses, then **REPLAY IDENTICAL**.
- [ ] All four marketplace outcomes reachable by swapping the sample (Clean→RELEASE, Cosmetic→RELEASE ~94%,
      Short→PARTIAL, Late→HOLD_PENDING_APPROVAL→approve→RELEASE) and both construction outcomes.
- [ ] The order form's **Fill for demo** works, and a hand-typed value visibly changes the verdict.
- [ ] The counterparty portal shows a pending HOLD, approves it, and shows the release — with the console's
      pending badge updating.
- [ ] The five moments in §6 are visibly polished; the five design tokens and the vocabulary in §7–8 are honoured.
- [ ] Nothing hard-codes `localhost`; errors surface their message; every panel has empty/loading states.

Build the thing that makes a judge lean forward at minute 3 and nod at minute 8. That's the brief.
