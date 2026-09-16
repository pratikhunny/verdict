# Demo runbook — driving the two screens

The click-by-click companion to [03-pitch.md](03-pitch.md). The pitch is *what you say*; this is
*what you click, on which screen, and where to point*. Rehearse against this until the hands are
automatic and the words are free.

Two surfaces, both served by the running app:

| Screen | URL | Who it is |
|---|---|---|
| **Ops console** | `http://localhost:8080/index.html` | The bank's escrow operations |
| **Counterparty portal** | `http://localhost:8080/portal.html` | The buyer (Selangor Components) |

---

## Pre-flight (before you present)

1. Start the app from the project root and wait for `Started VerdictApplication`:
   ```bash
   ./scripts/run-app.sh
   ```
   To show a live AI extraction call, start it with a key instead (optional — see §"The AI moment"):
   ```bash
   ANTHROPIC_API_KEY=sk-ant-xxxxx VERDICT_MODEL=claude-sonnet-4-5 ./scripts/run-app.sh
   ```
2. Open **two browser windows**: ops console on your main screen, counterparty portal on the second
   (or a second tab you can flip to). Use `http://localhost:8080/...` — **not** the IDE file preview
   on port 63342 (that has no `/api` backend and the page will error).
3. On the ops console, the **④ AI extraction** panel toggle should read **"Deterministic rules"**.
   Leave it there for the scripted run; switch to Live AI only for the optional AI moment.
4. Zoom the browser to ~110–125% so the back row reads the ledger.

The console now reads as a **lifecycle, top to bottom**: ① Contract & parties → ② Transactions →
③ Documents → ④ AI extraction (confidence) → ⑤ Examination → ⑥ Determination, with the ledger,
journal and graphs on the right. Each **transaction** is its own hold → evaluate → pay cycle; you
run one scenario per transaction.

The per-scenario rhythm is always the same four clicks:
**+ New transaction → Load a sample (or Upload) → Extract facts → Examine against contract.**

---

## The run

### 0:00–1:00 · The number
Deliver the 60–80% open cold (pitch §0:00). On screen: the **contract and its parties** — buyer,
supplier, SCB as agent — the mandates, and the multi-payee shipment split.

### 1:00–2:30 · The confession — Clean
- **+ New transaction → Load a sample → "Clean shipment" → Extract facts → Examine.**
- On **Extract**, point at the fields the engine read with their **confidence bars**. On **Examine**,
  the green **RELEASE**, **USD 600,000**, the three-payee split, and **RECONCILED**.
- Line: *"It works. And it's a party trick. That's the thirty percent."*

### 2:30–6:30 · The four scenarios (one transaction each)

**Cosmetic variance.** New transaction → sample *"Cosmetic variance"* → Extract → Examine. Still
**RELEASE**; the goods condition is **MET** with a **COSMETIC** finding; read the tolerance rule in
the findings notice. *"A good escrow officer would have released this. So did we — and it recorded why."*

**Short shipment (the beat).** New transaction → sample *"Short shipment"* → Extract (note
**Quantity = 800**) → Examine. Amber **PARTIAL RELEASE**, **480,000 / 120,000**. Sit in silence,
then point: quantity **NOT MET → QUANTITY** · split pro-rated to 80% · **Residual re-earmarked** ·
ledger **held 120,000 / disbursed 480,000** · **RECONCILED**. *"No system does this. Every human does this."*

**Late shipment → approval.** *(The two-screen handoff.)* New transaction → sample *"Late shipment"*
→ Extract → Examine → orange **HOLD PENDING APPROVAL**, nothing moves. **Switch to the counterparty
portal, reload** — it shows *"Awaiting your approval"* with the notice. **Click Approve — Treasury &
CFO (dual sign)** → `RequiresCountersignature` → `Admitted` → **RELEASED**. *"The approval goes back
into the engine, as evidence."*

> **Upload moment (optional):** instead of a sample, click **Upload document** and drop a real
> `.txt` bill of lading — the engine extracts it live. With **Live AI** on, the LLM does the reading;
> on **Deterministic rules**, a parser does — both read the same document. Toggle is in the extraction
> panel.

### 6:30–7:30 · The replay
- Open the short-shipment transaction (its chip), and in the **Decision journal** panel click
  **Replay** on the entry.
- Point at **REPLAY IDENTICAL** — same outcome, same amount from the pinned inputs.
- Line: *"The model reads. The model never decides. And every decision replays."*

### 7:30–8:30 · The graph
- Point at the **Node library · graphs are configuration** panel: the marketplace graph is
  highlighted; below it, M&A holdback, construction retention, conditional payout — same node chips,
  reordered.
- Line: *"Different graph, same engine, no new code."* (If a judge presses it, mention the runnable
  proof: `./scripts/run-verify.sh` includes a second deal type modelled purely as data.)

### 8:30–9:30 · The pilot ask
- Point at the **AI extraction (L3)** chip and say: *"Three things are mocked — screening,
  extraction, the settlement rail — each behind a real port."* Toggle is right there.
- Deliver the ask (pitch §8:30).

### 9:30–10:00 · The close
- No new clicks. *"We didn't automate the agreement. We automated the disagreement."*

---

## The AI moment (optional)

If you started with `ANTHROPIC_API_KEY` set, the extraction toggle offers **Live AI · <model>**. To
show a genuine model call on any transaction:

1. In the extraction panel, click **`Use live AI ▸`**.
2. Upload a document (or load a sample) and **Extract** — the confidences now come from the model
   reading the document.
3. Click **`◂ Deterministic rules`** before the scripted beats, so they stay reproducible.

Say it plainly: *"That confidence was a live model read. The deterministic parser reads the same
document with rules — and the engine can't tell the difference, which is the point."* If the model is
slow or errors, the app tells you to switch to rules; do it and carry on.

---

## If something goes wrong

| Symptom | Fix |
|---|---|
| Page errors *"Unexpected token '<' … not valid JSON"* | You opened the IDE preview (`:63342`). Use `http://localhost:8080/index.html`. |
| Port 8080 busy | `lsof -ti:8080 \| xargs kill`, or `./scripts/run-app.sh --server.port=8090`. |
| Live AI errors mid-demo | Switch extraction to **Deterministic rules** and re-extract; nothing is lost. |
| Ledger/extraction looks wrong | Start a **+ New transaction** — each is a clean, independent lifecycle. |
| Need to prove it's real, no browser | `./scripts/run-verify.sh` — architecture + 12 authority + 43 packs + 7 adaptability, all green. |

---

## One-line cheat sheet (tape to the laptop)

Per scenario: **New txn → Load sample → Extract → Examine.**
Clean → *party trick* · Cosmetic → *released, said why* · Short → **beat, go silent** · Late →
**portal → approve → four-eyes → release** · **Replay** → *identical* · **Graphs** → *configuration*.
