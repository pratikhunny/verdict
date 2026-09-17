# Verdict — API Reference (frontend wiring)

**Audience:** the engineer/agent wiring the React frontend.
**Companion:** [`07-frontend-brief.md`](07-frontend-brief.md) — the product/UX intent.

This is the **complete, exact** HTTP contract. Every shape below was captured from the running server, not
paraphrased. The backend is frozen; treat these types as source of truth. If a shape looks wrong, flag it —
don't rename fields client-side.

---

## 1. At a glance

- **Base path:** everything is under `/api`. No versioning, no auth, no headers required beyond
  `Content-Type: application/json` on the two calls that take a JSON body.
- **Money** is always `{ "amount": "600000.00", "currency": "USD" }` — `amount` is a **decimal string**
  (parse with `Number()` / `parseFloat`; never assume it's a JS number). Format for display with grouping.
- **Confidence** (`FactView.confidence`) is a **0..1 float** (e.g. `0.99`) — render as a percentage.
- **Errors** are HTTP **409** with body `{ "error": "<human message>" }`. See §7.
- **CORS:** none needed — in dev use a proxy; in prod the app serves the frontend same-origin (§2).
- **State** lives in-memory on the server and resets on restart. There's no pagination, no auth, no websockets;
  the UI advances state by POSTing actions and re-fetching the affected views.

### Endpoint map

| Group | Method | Path | Returns |
|---|---|---|---|
| Setup | GET | `/api/dealtypes` | `string[]` |
| Setup | GET | `/api/contract?dealType=` | `ContractView` |
| Setup | GET | `/api/graph?dealType=` | `GraphView2` |
| Setup | GET | `/api/orderform?dealType=` | `OrderFormView` |
| Setup | GET | `/api/samples?dealType=` | `Sample[]` |
| Extraction | GET | `/api/extraction` | `ExtractionModeView` |
| Extraction | POST | `/api/extraction/{mode}` (`RULES`\|`LLM`) | `ExtractionModeView` |
| Txn | GET | `/api/transactions` | `TransactionView[]` |
| Txn | POST | `/api/transactions?dealType=` (JSON body) | `TransactionView` |
| Txn | GET | `/api/transactions/{id}` | `TransactionView` |
| Money | POST | `/api/transactions/{id}/fund` | `LedgerView` |
| Money | POST | `/api/transactions/{id}/earmark` | `LedgerView` |
| Money | POST | `/api/transactions/{id}/disburse` | `LedgerView` |
| Evidence | POST | `/api/transactions/{id}/documents` (multipart) | `ExtractionResultView` |
| Evidence | POST | `/api/transactions/{id}/documents/text` (JSON) | `ExtractionResultView` |
| Evidence | POST | `/api/transactions/{id}/samples/{scenario}` | `ExtractionResultView` |
| Evidence | DELETE | `/api/transactions/{id}/documents` | `ExtractionResultView` |
| Evidence | GET | `/api/transactions/{id}/extraction` | `ExtractionResultView` |
| Evidence | POST | `/api/transactions/{id}/extract` | `ExtractionResultView` |
| Decision | POST | `/api/transactions/{id}/examine` | `DeterminationView` |
| Decision | POST | `/api/transactions/{id}/approve` | `ApprovalView` |
| Decision | POST | `/api/transactions/{id}/replay/{determinationId}` | `ReplayView` |
| View | GET | `/api/transactions/{id}/ledger` | `LedgerView` |
| View | GET | `/api/transactions/{id}/journal` | `JournalView` |
| View | GET | `/api/transactions/{id}/pending` | `DeterminationView` \| `{ "pending": false }` |
| View | GET | `/api/transactions/{id}/determination` | `DeterminationView` \| `{ "determined": false }` |

---

## 2. Running the backend & dev proxy

```bash
./scripts/run-app.sh          # or: mvn -DskipTests spring-boot:run   → http://localhost:8080
```

**Vite dev proxy** (so the client only ever calls `'/api/...'`):

```ts
// vite.config.ts
export default defineConfig({
  server: { proxy: { '/api': 'http://localhost:8080' } },
});
```

For the demo, the built frontend is served same-origin as the API (either run Vite's dev server behind the proxy
above, or copy the `dist/` output into `src/main/resources/static/` — a `frontend-maven-plugin` step to automate
that copy is a small follow-up, not yet wired). Either way the client must call **relative** paths
(`fetch('/api/transactions')`) and **never** hard-code `localhost`, so the same build works in dev and when
Spring serves it.

---

## 3. Enumerations (exact string values)

```
DealType            "Marketplace trade" | "Construction retention"
Outcome             "RELEASE" | "PARTIAL_RELEASE" | "HOLD_PENDING_APPROVAL" | "HOLD"
VerdictStatus       "MET" | "NOT_MET" | "INDETERMINATE"
FindingGrade        "COSMETIC" | "QUANTITY" | "TIMING" | "MISSING_DOCUMENT" | "SUBSTANTIVE" | "OBJECTION"
FindingResolution   "UNRESOLVED" | "CLEARED_BY_TOLERANCE" | "APPROVED_BY_PARTY"
ConditionKind       "DOCUMENT_PRESENT" | "GOODS_DESCRIPTION_MATCHES" | "QUANTITY_MATCHES"
                    | "SHIPPED_WITHIN_LATEST_DATE" | "CERTIFICATE_PRESENT" | "COMPLETION_AT_LEAST"
                    | "NO_OUTSTANDING_OBJECTION"
FactKey             "EBL_PRESENT" | "EVIDENCED_QUANTITY" | "GOODS_DESCRIPTION" | "SHIPMENT_DATE"
                    | "APPROVAL_GRANTED" | "CERTIFICATE_PRESENT" | "COMPLETION_PERCENT" | "OBJECTION_RAISED"
GraphNodeKind       "money" | "decision" | "evidence" | "branch"
ExtractionMode      "RULES" | "LLM"
FieldType           "text" | "number" | "date" | "money"     // order-form fields
Sample key (mkt)    "CLEAN" | "COSMETIC_VARIANCE" | "SHORT_SHIPMENT" | "LATE_SHIPMENT"
Sample key (con)    "CLEAN" | "INCOMPLETE" | "OBJECTION" | "MISSING_CERT"
TransactionView.status (informational): "NEW" | "FUNDED" | "EARMARKED" | "EXTRACTED" | <Outcome>
ApprovalView step.result: "Admitted" | "RequiresCountersignature" | <Outcome>   // admission-gate result names
```

---

## 4. TypeScript types (copy verbatim)

```ts
// ---------- shared ----------
export interface Money { amount: string; currency: string; } // amount is a decimal string
export interface Chip { label: string; value: string; }

// ---------- setup ----------
export interface PartyView { name: string; type: string; jurisdiction: string; screening: string; }
export interface ResponsibilityView { party: string; role: string; mandate: string; }
export interface ConditionDto { kind: ConditionKind; clause: string; }
export interface ObligationDto {
  id: string; payee: string; payeeParty: string; rule: string;
  severable: boolean; clause: string; fullEntitlement: Money;
}
export interface ContractView {
  id: string; dealType: DealType; tranche: Money; chips: Chip[]; tolerance: string;
  parties: PartyView[]; responsibilities: ResponsibilityView[];
  conditions: ConditionDto[]; payees: ObligationDto[];
}
export interface GNode { id: string; label: string; x: number; y: number; kind: GraphNodeKind; }
export interface GEdge { from: string; to: string; label: string | null; dashed: boolean; }
export interface GraphView2 { name: string; nodes: GNode[]; edges: GEdge[]; }

export interface FieldSpec {
  key: string; label: string; type: FieldType;
  demo: string; suffix: string | null; help: string;
}
export interface OrderFormView { dealType: DealType; title: string; fields: FieldSpec[]; }
export interface Sample { key: string; label: string; }

// ---------- transactions ----------
export interface TransactionView {
  id: string; dealType: DealType; status: string;
  held: Money; released: Money; retained: Money;
  outcome: string;                 // Outcome once determined, else same as status
  documentCount: number;
  funded: boolean; earmarked: boolean; extracted: boolean; determined: boolean; disbursed: boolean;
  fundAmount: Money;               // what THIS transaction collects
  milestoneValue: Money;           // entitlement at stake this milestone
  orderTerms: Chip[];              // the operator's entered order terms
}

// ---------- documents & extraction ----------
export interface UploadedDocView { id: string; filename: string; type: string; sizeBytes: number; text: string; }
export interface FactView { field: string; key: FactKey; value: string; confidence: number; sourceDocument: string; }
export interface ExtractionResultView { extractor: string; documents: UploadedDocView[]; facts: FactView[]; }
export interface ExtractionModeView { mode: ExtractionMode; liveAvailable: boolean; model: string; }

// ---------- determination ----------
export interface LineDto { obligation: string; payee: string; payeeParty: string; amount: Money; }
export interface ResidualDto { sourceObligation: string; residualObligation: string; payee: string; retained: Money; }
export interface FindingDto { grade: FindingGrade; resolution: FindingResolution; clause: string; confidence: number; detail: string; }
export interface VerdictDto { condition: string; status: VerdictStatus; grade: FindingGrade | null; }
export interface DeterminationView {
  id: string; outcome: Outcome; released: Money; retained: Money;
  disbursements: LineDto[]; residuals: ResidualDto[];
  findings: FindingDto[]; verdicts: VerdictDto[];
  findingsNotice: string; ruleSetVersion: string; submissionId: string; evidenceDigestShort: string;
}

// ---------- ledger & journal ----------
export interface EarmarkDto { obligation: string; payee: string; amount: Money; }
export interface ReconDto { entitlementTotal: Money; earmarkTotal: Money; balanced: boolean; }
export interface LedgerView {
  held: Money; unallocated: Money; reserved: Money; disbursed: Money;
  earmarks: EarmarkDto[]; reconciliation: ReconDto;
}
export interface EntryDto {
  id: string; recordedAt: string; determinationId: string; outcome: Outcome;
  ruleSetVersion: string; effectiveAt: string; evidenceDigestShort: string; factCount: number;
}
export interface JournalView { entries: EntryDto[]; }
export interface ReplayView {
  determinationId: string; identical: boolean;
  originalOutcome: Outcome; replayedOutcome: Outcome;
  originalReleased: Money; replayedReleased: Money;
}

// ---------- approval ----------
export interface StepDto { label: string; result: string; detail: string; }
export interface ApprovalView { steps: StepDto[]; determination: DeterminationView; }

// pending/determination endpoints return the view OR a sentinel:
export type PendingResponse = DeterminationView | { pending: false };
export type DeterminationResponse = DeterminationView | { determined: false };
```

---

## 5. Endpoints — detail & real examples

### 5.1 Setup

**`GET /api/dealtypes`** → the deal types, in display order. Drives the top selector.
```json
["Marketplace trade", "Construction retention"]
```

**`GET /api/contract?dealType=Marketplace%20trade`** → the whole master-agreement view for the deal type
(template terms + parties + participants + conditions + obligations). `dealType` optional (defaults to
Marketplace). Abridged:
```json
{
  "id": "DEAL-2026-0417", "dealType": "Marketplace trade",
  "tranche": { "amount": "600000.00", "currency": "USD" },
  "chips": [ { "label": "Goods type", "value": "tablet computers" }, { "label": "Release rule", "value": "pro-rata to evidenced quantity (severable)" } ],
  "tolerance": "TOL-2026.09 / goods-description synonyms",
  "parties": [ { "name": "Selangor Components Sdn Bhd", "type": "Corporate", "jurisdiction": "Malaysia", "screening": "CLEAR" } ],
  "responsibilities": [ { "party": "Selangor Components Sdn Bhd", "role": "Payer / Obligor", "mandate": "Treasury Manager — waive ≤ USD 50k (single); CFO — dual, uncapped" } ],
  "conditions": [ { "kind": "QUANTITY_MATCHES", "clause": "cl. 4.2 — quantity" } ],
  "payees": [ { "id": "O2a", "payee": "PAYEE-SUPPLIER", "payeeParty": "PTY-SELLER-VN", "rule": "96% of the 60% shipment tranche", "severable": true, "clause": "cl. 4 — shipment tranche (supplier)", "fullEntitlement": { "amount": "576000.00", "currency": "USD" } } ]
}
```

**`GET /api/graph?dealType=`** → the DAG to draw. Nodes carry pixel `x,y` (lay them out directly on an SVG
canvas); edges are explicit; `dashed:true` marks a conditional branch. `label` may be `null`.
```json
{
  "name": "Marketplace trade",
  "nodes": [
    { "id": "COLLECT_FUNDS", "label": "COLLECT", "x": 20,  "y": 24,  "kind": "money" },
    { "id": "EXAMINE",       "label": "EXAMINE", "x": 440, "y": 24,  "kind": "decision" },
    { "id": "SOLICIT_APPROVAL", "label": "SOLICIT APPROVAL", "x": 580, "y": 128, "kind": "branch" }
  ],
  "edges": [
    { "from": "COLLECT_FUNDS", "to": "EARMARK", "label": null, "dashed": false },
    { "from": "DETERMINE", "to": "SOLICIT_APPROVAL", "label": "findings", "dashed": true },
    { "from": "SOLICIT_APPROVAL", "to": "EXAMINE", "label": "approval re-enters as evidence", "dashed": true }
  ]
}
```
Node ids match the lifecycle "current step" values in §6 so you can highlight the active node.

**`GET /api/orderform?dealType=`** → the fields to render in the new-transaction form. `type` is one of
`text|number|date|money`; `suffix` is a unit chip (e.g. `USD`, `units`, `%`) or `null`; `demo` is the exact
"Fill for demo" value; `help` is the caption.
```json
{
  "dealType": "Construction retention", "title": "Open a new milestone claim",
  "fields": [
    { "key": "poolValue", "label": "Escrow pool (total)", "type": "money", "demo": "1000000", "suffix": "USD", "help": "The homebuyer's ring-fenced funds; the milestone releases a tranche of this." },
    { "key": "completionThreshold", "label": "Required completion", "type": "number", "demo": "40", "suffix": "%", "help": "Certified completion must reach this to release the tranche — binary, not pro-rata." }
  ]
}
```
Post the collected `{ key: value }` map as the body of `POST /api/transactions` (§5.3). Marketplace keys:
`orderRef, contractValue, quantity, latestShipmentDate`. Construction keys: `projectRef, poolValue,
completionThreshold, milestoneLabel`. Numbers/money go as plain strings; the server sanitises commas/currency.

**`GET /api/samples?dealType=`** → ready-made evidence packs for the demo.
```json
[ { "key": "CLEAN", "label": "Clean shipment" }, { "key": "SHORT_SHIPMENT", "label": "Short shipment (800/1,000)" } ]
```

### 5.2 Extraction mode (the "what's mocked" toggle)

**`GET /api/extraction`** → current extractor state.
```json
{ "mode": "RULES", "liveAvailable": false, "model": "llm:claude-sonnet-4-5" }
```
**`POST /api/extraction/RULES`** or **`/api/extraction/LLM`** → sets the mode, returns the same shape. If
`liveAvailable` is false, LLM mode still sets but `POST …/extract` will 409 until an API key is configured —
show the toggle as "needs key" and fall back gracefully.

### 5.3 Create a transaction

**`POST /api/transactions?dealType=Marketplace%20trade`**, `Content-Type: application/json`, body = the
order-form field map:
```json
{ "orderRef": "PO-2026-0417", "contractValue": "1000000", "quantity": "1000", "latestShipmentDate": "2026-09-10" }
```
Returns the new `TransactionView` (id like `TXN-0001`). The body is optional — POST with no body falls back to
demo defaults — but the form always sends it. **The entered values drive the engine** (a typed `quantity` of
`800` against a 1,000-unit eBL flips the outcome), so this isn't cosmetic.

### 5.4 Money plane — fund / earmark / disburse

All three take no body and return the updated `LedgerView`. Order is enforced: fund → earmark → (…examine…) →
disburse. `LedgerView` after earmark:
```json
{
  "held": { "amount": "600000.00", "currency": "USD" },
  "unallocated": { "amount": "0.00", "currency": "USD" },
  "reserved": { "amount": "600000.00", "currency": "USD" },
  "disbursed": { "amount": "0.00", "currency": "USD" },
  "earmarks": [
    { "obligation": "O2a", "payee": "PTY-SELLER-VN", "amount": { "amount": "576000.00", "currency": "USD" } },
    { "obligation": "O2b", "payee": "PTY-MARKETPLACE", "amount": { "amount": "19800.00", "currency": "USD" } },
    { "obligation": "O2c", "payee": "PTY-SCB-AGENT", "amount": { "amount": "4200.00", "currency": "USD" } }
  ],
  "reconciliation": {
    "entitlementTotal": { "amount": "600000.00", "currency": "USD" },
    "earmarkTotal": { "amount": "600000.00", "currency": "USD" },
    "balanced": true
  }
}
```
Render as the **wallet tree**: `held` = pool root; each `earmarks[]` entry = a payee sub-wallet; `unallocated`
= a "future milestones" node when > 0 (construction leaves ~60% unallocated after earmarking the 40% tranche);
`disbursed` = the paid-out node after disburse. `reconciliation.balanced` drives the green RECONCILED / red
DRIFT strip.

### 5.5 Evidence plane — documents & extraction

- **`POST …/documents`** — `multipart/form-data`, field name **`file`** (a `.txt`/text file). Returns
  `ExtractionResultView` with the doc added (no facts yet).
- **`POST …/documents/text`** — JSON `{ "filename": "x.txt", "text": "…" }` for pasting.
- **`POST …/samples/{scenario}`** — loads a ready pack (replaces current docs). `{scenario}` from `/samples`.
- **`DELETE …/documents`** — clears docs.
- **`GET …/extraction`** — current docs + facts (use to hydrate a re-selected transaction).
- **`POST …/extract`** — runs the active extractor over the docs; returns docs + **facts with confidence**:
```json
{
  "extractor": "deterministic-rules",
  "documents": [ { "id": "DOC-EBL-SHORT_SHIPMENT", "filename": "eBL-short_shipment.txt", "type": "eBL", "sizeBytes": 282, "text": "ELECTRONIC BILL OF LADING…" } ],
  "facts": [
    { "field": "Quantity", "key": "EVIDENCED_QUANTITY", "value": "800", "confidence": 0.99, "sourceDocument": "DOC-EBL-SHORT_SHIPMENT" },
    { "field": "Shipment date", "key": "SHIPMENT_DATE", "value": "2026-09-08", "confidence": 0.99, "sourceDocument": "DOC-EBL-SHORT_SHIPMENT" }
  ]
}
```
`confidence` ∈ [0,1]; render bars (≥0.90 green, ≥0.75 gold, else red). `extractor` is the label to show
(`deterministic-rules` or a model id in LLM mode). Document intake is **locked once a determination exists** —
these routes 409 after examine; gate the UI (§6).

### 5.6 Decision plane — examine

**`POST …/examine`** → the `DeterminationView` (the verdict). Requires funded + earmarked + at least one
extract. The hero **PARTIAL_RELEASE** (short shipment):
```json
{
  "id": "DET-TXN-0001-SUB-1", "outcome": "PARTIAL_RELEASE",
  "released": { "amount": "480000.00", "currency": "USD" },
  "retained": { "amount": "120000.00", "currency": "USD" },
  "disbursements": [ { "obligation": "O2a", "payee": "PAYEE-SUPPLIER", "payeeParty": "PTY-SELLER-VN", "amount": { "amount": "460800.00", "currency": "USD" } } ],
  "residuals": [ { "sourceObligation": "O2a", "residualObligation": "O2a-R", "payee": "PAYEE-SUPPLIER", "retained": { "amount": "115200.00", "currency": "USD" } } ],
  "findings": [ { "grade": "QUANTITY", "resolution": "UNRESOLVED", "clause": "cl. 4.2 — quantity", "confidence": 0.99, "detail": "evidenced 800, contracted 1000 — short by 200" } ],
  "verdicts": [
    { "condition": "C-EBL-PRESENT", "status": "MET", "grade": null },
    { "condition": "C-QTY-MATCH", "status": "NOT_MET", "grade": "QUANTITY" },
    { "condition": "C-SHIP-DATE", "status": "MET", "grade": null }
  ],
  "findingsNotice": "FINDINGS NOTICE — DEAL-2026-0417 · MS-SHIPMENT-EVIDENCED\nDetermination: PARTIAL_RELEASE (rule set RS-2026.09-v1)\n  1. QUANTITY [UNRESOLVED] — evidenced 800, contracted 1000 — short by 200  (cl. 4.2 — quantity)  conf 0.99\n",
  "ruleSetVersion": "RS-2026.09-v1", "submissionId": "TXN-0001-SUB-1", "evidenceDigestShort": "d7b3c2384a9f"
}
```
- `verdicts` → the examination table (per condition: MET / NOT_MET / INDETERMINATE, + `grade` when there's a finding).
- `outcome` → the big verdict word + colour.
- `disbursements` → the per-payee split; `residuals` → the re-earmarked remainder (only on PARTIAL_RELEASE).
- `findingsNotice` → the dark monospace notice block; render its `\n` newlines with `white-space: pre-wrap`.
- **HOLD** has empty `disbursements`/`residuals` and released `0.00`. **HOLD_PENDING_APPROVAL** likewise (a
  `TIMING`/`OBJECTION` finding, `resolution: "UNRESOLVED"`) — route it to the approval flow.

### 5.7 Decision plane — approve (four-eyes)

**`POST …/approve`** — valid only when the current determination is `HOLD_PENDING_APPROVAL`. It runs the
admission gate (single sig → requires countersignature → dual sig admitted), enters the approval as evidence,
re-examines, and settles. Returns an `ApprovalView`:
```json
{
  "steps": [
    { "label": "Approver signs — single signatory", "result": "RequiresCountersignature", "detail": "USD 600000.00 exceeds the USD 50k single-signature ceiling — one of two signatures" },
    { "label": "Counter-signatory signs", "result": "Admitted", "detail": "OBLIGOR under WAIVE_DISCREPANCY mandate (uncapped)" },
    { "label": "Approval enters as evidence → re-examined → disbursed", "result": "RELEASE", "detail": "finding approved by entitled party; engine re-examines" }
  ],
  "determination": { "outcome": "RELEASE", "released": { "amount": "600000.00", "currency": "USD" }, "findings": [ { "grade": "TIMING", "resolution": "APPROVED_BY_PARTY", "detail": "shipped 2026-09-13, latest 2026-09-10 — 3 day(s) late" } ], "…": "full DeterminationView" }
}
```
Render `steps` as a signed-off checklist, then swap the verdict card to the new (RELEASE) determination. Note the
finding is now `resolution: "APPROVED_BY_PARTY"` — the point of the whole beat: *approval entered as evidence, it
did not bypass the engine.*

### 5.8 Views — ledger, journal, pending, determination, replay

- **`GET …/ledger`** → `LedgerView` (§5.4). Refetch after fund/earmark/examine/disburse/approve.
- **`GET …/journal`** → append-only entries; each is replayable via its `determinationId`.
  ```json
  { "entries": [ { "id": "JRN-0001", "recordedAt": "2026-09-17T15:41:56.462663Z", "determinationId": "DET-TXN-0001-SUB-1", "outcome": "PARTIAL_RELEASE", "ruleSetVersion": "RS-2026.09-v1", "effectiveAt": "2026-09-15T09:00:00Z", "evidenceDigestShort": "d7b3c2384a9f", "factCount": 4 } ] }
  ```
- **`GET …/pending`** → the pending `DeterminationView` if the txn is HOLD_PENDING_APPROVAL & not disbursed,
  else `{ "pending": false }`. The **portal** polls `GET /api/transactions`, filters
  `outcome === "HOLD_PENDING_APPROVAL" && !disbursed`, then reads this for the notice text.
- **`GET …/determination`** → the current `DeterminationView`, or `{ "determined": false }`.
- **`POST …/replay/{determinationId}`** → re-runs the pinned determination from the journal:
  ```json
  { "determinationId": "DET-TXN-0001-SUB-1", "identical": true, "originalOutcome": "PARTIAL_RELEASE", "replayedOutcome": "PARTIAL_RELEASE", "originalReleased": { "amount": "480000.00", "currency": "USD" }, "replayedReleased": { "amount": "480000.00", "currency": "USD" } }
  ```
  `identical: true` → the green **REPLAY IDENTICAL** badge. This is the trust moment; make it satisfying.

---

## 6. Lifecycle state machine & button gating

A transaction advances through fixed stages. **Derive every button's enabled/disabled state from the
`TransactionView` booleans** — don't track it separately. Disable (don't hide) so the pipeline reads as steps.

```
NEW → funded → earmarked → (docs + extracted) → determined → disbursed
                                     │
                                     └─ if outcome = HOLD_PENDING_APPROVAL → approve → (determined+disbursed)
```

| Action | Enable when | Backend guard (else 409) |
|---|---|---|
| Fund | `!funded` | already funded |
| Earmark | `funded && !earmarked` | fund first / already earmarked |
| Upload / Load sample / Clear | `earmarked && !determined` | earmark first / locked after determination |
| Extract | `earmarked && !determined && documentCount > 0` | need a document |
| Examine | `earmarked && extracted && !determined` | earmark + extract first |
| Disburse | `determined && (outcome === "RELEASE" || outcome === "PARTIAL_RELEASE") && !disbursed` | nothing to disburse on HOLD |
| Approve | `outcome === "HOLD_PENDING_APPROVAL" && !disbursed` | no pending approval |
| Replay | a journal entry exists | — |

**Current graph node** (for highlighting) maps from the same booleans:
```
!funded                                        → "COLLECT_FUNDS"
funded && !earmarked                           → "EARMARK"
earmarked && !extracted                        → "INTAKE_EVIDENCE"
extracted && !determined                       → "EXAMINE"
determined && HOLD_PENDING_APPROVAL && !disb.  → "SOLICIT_APPROVAL"
determined && (RELEASE|PARTIAL) && !disbursed  → "DISBURSE"
otherwise                                      → done (all happy-path nodes complete)
```

**Refetch discipline** after each mutation (this is the whole app's data flow):
- fund / earmark → refetch `ledger`, `transactions`
- extract → refetch `extraction`, `transactions`
- examine → refetch `determination`(the response), `ledger`, `journal`, `transactions`
- disburse → refetch `ledger`, `journal`, `transactions`, `determination`
- approve → refetch `determination`, `ledger`, `journal`, `transactions`

(With TanStack Query, invalidate the `['transaction', id, …]` keys; with plain hooks, call the getters.)

---

## 7. Errors

Any invalid action (wrong stage, unknown id, LLM without a key) returns **HTTP 409** with:
```json
{ "error": "TXN-0002 is already funded" }
```
Catch non-OK responses, parse `.error`, and show it inline near the action. Never swallow it into a blank state.
A tiny client helper:
```ts
async function api<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`/api${path}`, init);
  if (!res.ok) {
    let msg = `HTTP ${res.status}`;
    try { msg = (await res.json()).error ?? msg; } catch {}
    throw new Error(msg);
  }
  return res.json() as Promise<T>;
}
// examples
const deals   = () => api<string[]>('/dealtypes');
const create  = (dealType: string, inputs: Record<string,string>) =>
  api<TransactionView>(`/transactions?dealType=${encodeURIComponent(dealType)}`,
    { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(inputs) });
const examine = (id: string) => api<DeterminationView>(`/transactions/${id}/examine`, { method: 'POST' });
const upload  = (id: string, file: File) => {
  const fd = new FormData(); fd.append('file', file);
  return api<ExtractionResultView>(`/transactions/${id}/documents`, { method: 'POST', body: fd }); // no JSON header
};
```

---

## 8. Golden paths (exact call sequences to reproduce each outcome)

All use `dealType = "Marketplace trade"` unless noted. Each step is a POST returning the type in §4; refetch per §6.

**Clean → RELEASE**
```
POST /transactions {orderRef,contractValue:"1000000",quantity:"1000",latestShipmentDate:"2026-09-10"}
POST …/fund → …/earmark → …/samples/CLEAN → …/extract → …/examine   ⇒ outcome RELEASE (released 600,000)
POST …/disburse
```
**Cosmetic variance → RELEASE (~0.94 conf, tolerance cited)** — same, sample `COSMETIC_VARIANCE`.
**Short shipment → PARTIAL_RELEASE** — same, sample `SHORT_SHIPMENT` ⇒ released 480,000 / retained 120,000, residuals present. `POST …/disburse`.
**Late shipment → HOLD_PENDING_APPROVAL → approve → RELEASE**
```
… sample LATE_SHIPMENT → …/extract → …/examine   ⇒ HOLD_PENDING_APPROVAL
POST …/approve                                   ⇒ RELEASE 600,000 (finding APPROVED_BY_PARTY)
```
**Replay** — after any examine: `POST …/replay/{determination.id}` ⇒ `identical: true`.

**Construction (`dealType = "Construction retention"`)** — funds the whole pool; earmarks the tranche; samples
`CLEAN|INCOMPLETE|OBJECTION|MISSING_CERT`:
```
CLEAN     (45% cert)          ⇒ RELEASE 400,000
INCOMPLETE(30% cert)          ⇒ HOLD
OBJECTION (lien recorded)     ⇒ HOLD_PENDING_APPROVAL → approve → RELEASE
```
Prove the wiring live: create with `completionThreshold:"50"` and load `CLEAN` (45%) ⇒ **HOLD** — the typed
threshold changed the verdict.

---

## 9. Rules of engagement

- **Don't** rename fields, invent endpoints, add auth/persistence, or hard-code `localhost`.
- **Do** treat `amount` as a string, `confidence` as a 0..1 float, and errors as 409-with-`.error`.
- **Do** derive UI state from `TransactionView` booleans, and refetch per §6 after every mutation.
- Missing data you think you need? Flag it to the backend owner — the contract is intentionally small and can grow.
```
