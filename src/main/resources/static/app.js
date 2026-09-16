// Verdict ops console — deal type drives contract + graph + flow; programmable-tree wallet.

const $ = (id) => document.getElementById(id);
const esc = (s) => String(s ?? '').replace(/[&<>"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
const money = (m) => m ? `${m.currency} ${Number(m.amount).toLocaleString('en-US', {minimumFractionDigits: 2})}` : '—';
const pct = (c) => `${Math.round(c * 100)}%`;
const band = (c) => c >= 0.9 ? 'hi' : (c >= 0.75 ? 'mid' : 'lo');

async function apiError(r) {
  const ct = r.headers.get('content-type') || '';
  if (ct.includes('application/json')) { try { return (await r.json()).error || r.statusText; } catch (e) {} }
  return `HTTP ${r.status}. Open this app at http://localhost:8080 (run ./scripts/run-app.sh) — the IDE file preview has no /api backend.`;
}
async function getJSON(p) { const r = await fetch(p); if (!r.ok) throw new Error(await apiError(r)); return r.json(); }
async function postJSON(p, opts) { const r = await fetch(p, {method: 'POST', ...(opts || {})}); if (!r.ok) throw new Error(await apiError(r)); return r.json(); }
async function del(p) { const r = await fetch(p, {method: 'DELETE'}); if (!r.ok) throw new Error(await apiError(r)); return r.json(); }

let sel = null;                 // selected transaction id
let selectedDealType = null;    // deal type driving the view
let currentGraphData = null;    // {nodes, edges} for the selected deal type
let currentContract = null;     // ContractView for the selected deal type

async function init() {
  await loadExtractionMode();
  const types = await getJSON('/api/dealtypes');
  $('dealTypeSelect').innerHTML = types.map((t, i) =>
    `<option value="${esc(t)}" ${i === 0 ? 'selected' : ''}>${esc(t)}</option>`).join('');
  selectedDealType = types[0];
  await applyDealType(selectedDealType);
  await loadTransactions();
}

async function onDealType() {
  selectedDealType = $('dealTypeSelect').value;
  // Deselect a transaction of a different type so the graph and the transaction never disagree.
  if (sel) {
    const v = await getJSON(`/api/transactions/${sel}`);
    if (v.dealType !== selectedDealType) { sel = null; $('lifecycle').hidden = true; }
  }
  await applyDealType(selectedDealType);
  await loadTransactions(false);
}

// load contract + graph + samples for a deal type
async function applyDealType(dealType) {
  const q = '?dealType=' + encodeURIComponent(dealType);
  const [c, g, samples] = await Promise.all([
    getJSON('/api/contract' + q), getJSON('/api/graph' + q), getJSON('/api/samples' + q)
  ]);
  currentContract = c;
  renderContract(c);
  currentGraphData = g;
  renderGraph();
  $('dealTypeNote').textContent = g.name.startsWith('Marketplace')
    ? 'Marketplace trade — pro-rata partial release, three payees.'
    : 'Construction retention (RERA) — milestone-binary release on an engineer certificate.';
  $('sampleSelect').innerHTML = '<option value="">Load a sample…</option>' +
    samples.map(s => `<option value="${esc(s.key)}">${esc(s.label)}</option>`).join('');
}

function renderContract(c) {
  const chips = [`<span class="chip"><b>${esc(c.id)}</b></span>`]
    .concat(c.chips.map(ch => `<span class="chip">${esc(ch.label)} <b>${esc(ch.value)}</b></span>`)).join('');
  const parties = c.parties.map(p =>
    `<tr><td><b>${esc(p.name)}</b></td><td>${esc(p.role)}</td><td>${esc(p.jurisdiction)}</td><td class="muted">${esc(p.note)}</td></tr>`).join('');
  const mandate = c.mandate.map(m => `<li>${esc(m)}</li>`).join('');
  const payees = c.payees.map(o =>
    `<tr><td class="mono">${esc(o.id)}</td><td>${esc(o.payeeParty)}</td><td>${esc(o.rule)}</td>
         <td>${o.severable ? 'severable' : 'whole (not severable)'}</td></tr>`).join('');
  const conds = c.conditions.map(cd => `<span class="node-chip">${esc(cd.kind.replace(/_/g,' '))}</span>`).join(' ');
  $('contract').innerHTML = `
    <div class="deal-chips" style="margin-bottom:14px">${chips}</div>
    <div class="two-col">
      <div><h3>Roles &amp; mandates</h3>
        <table><thead><tr><th>Party</th><th>Role</th><th>Juris.</th><th></th></tr></thead><tbody>${parties}</tbody></table>
        <ul class="mandate">${mandate}</ul></div>
      <div><h3>Obligations — split rules (%, not amounts)</h3>
        <table><thead><tr><th>Obl.</th><th>Payee</th><th>Split rule</th><th></th></tr></thead><tbody>${payees}</tbody></table>
        <h3 style="margin-top:12px">Conditions examined</h3><div>${conds}</div>
        <p class="hint">Tolerance: ${esc(c.tolerance)} · amounts are set per transaction, below.</p></div>
    </div>`;
}

// Per-transaction parties + amount (the amount that will be funded for THIS transaction).
function renderTxnParties(v) {
  if (!currentContract) { $('txnParties').innerHTML = ''; return; }
  const parties = currentContract.parties.map(p =>
    `<tr><td><b>${esc(p.name)}</b></td><td>${esc(p.role)}</td><td class="muted">${esc(p.note)}</td></tr>`).join('');
  $('txnParties').innerHTML = `
    <div class="tiles" style="grid-template-columns:1fr 1fr">
      <div class="tile"><div class="n">${money(v.fundAmount)}</div><div class="l">Funds collected this transaction</div></div>
      <div class="tile"><div class="n">${money(v.milestoneValue)}</div><div class="l">Milestone entitlement at stake</div></div>
    </div>
    <table><thead><tr><th>Party (onboarded)</th><th>Role</th><th></th></tr></thead><tbody>${parties}</tbody></table>
    <p class="hint">Parties are onboarded once under L0 (KYC, screening, mandates) — reused across transactions.
      Every state-changing step below passes the L0 admission gate before it acts.</p>`;
}

// ---- graph (real DAG from nodes + explicit edges) ----
function renderGraph(currentStep) {
  if (!currentGraphData) return;
  $('graph').innerHTML = graphSVG(currentGraphData, currentStep);
}
function graphSVG(g, current) {
  const W = 118, H = 44;
  const byId = Object.fromEntries(g.nodes.map(n => [n.id, n]));
  const maxX = Math.max(...g.nodes.map(n => n.x)) + W + 20;
  const maxY = Math.max(...g.nodes.map(n => n.y)) + H + 34;
  const curNode = byId[current];
  const kindFill = { money: '#fdf0e6', decision: '#e8f0ec', evidence: '#eceef5', branch: '#fdf0e6' };
  const kindStroke = { money: '#b5651d', decision: '#0e6e3c', evidence: '#3a4a7a', branch: '#c2410c' };

  let s = `<svg viewBox="0 0 ${maxX} ${maxY}" width="100%" preserveAspectRatio="xMidYMin meet" font-family="ui-monospace,monospace">`;
  s += `<defs><marker id="arr" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto"><path d="M0,0 L8,4 L0,8 z" fill="#8892a0"/></marker>
        <marker id="arrb" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto"><path d="M0,0 L8,4 L0,8 z" fill="#c2410c"/></marker></defs>`;
  // edges
  g.edges.forEach(e => {
    const a = byId[e.from], b = byId[e.to]; if (!a || !b) return;
    const stroke = e.dashed ? '#c2410c' : '#8892a0';
    const marker = e.dashed ? 'url(#arrb)' : 'url(#arr)';
    const dash = e.dashed ? 'stroke-dasharray="4 3"' : '';
    let d;
    if (a.y === b.y) { // horizontal
      d = `M${a.x+W},${a.y+H/2} L${b.x},${b.y+H/2}`;
    } else if (b.y > a.y) { // down to a branch
      d = `M${a.x+W/2},${a.y+H} L${b.x+W/2},${b.y}`;
    } else { // loop back up-left
      d = `M${a.x},${a.y+H/2} C${b.x+W/2-70},${a.y+H/2} ${b.x+W/2},${b.y+H+30} ${b.x+W/2},${b.y+H}`;
    }
    s += `<path d="${d}" fill="none" stroke="${stroke}" stroke-width="1.5" ${dash} marker-end="${marker}"/>`;
    if (e.label) {
      const mx = a.y === b.y ? (a.x+W+b.x)/2 : (b.x+W/2+8);
      const my = a.y === b.y ? a.y+H/2-6 : (a.y+H+ (b.y>a.y?18:38));
      s += `<text x="${mx}" y="${my}" font-size="9.5" fill="#c2410c" text-anchor="middle">${esc(e.label)}</text>`;
    }
  });
  // nodes
  g.nodes.forEach(n => {
    const isCur = n.id === current;
    const done = n.y === 24 && curNode && n.x < curNode.x || (current === 'DONE' && n.y === 24);
    const fill = isCur ? '#38B449' : (done ? '#e3f2ea' : kindFill[n.kind] || '#eef1f5');
    const stroke = isCur ? '#0e6e3c' : (kindStroke[n.kind] || '#cfd6de');
    const col = isCur ? '#fff' : (done ? '#0e6e3c' : '#3a4250');
    s += `<rect x="${n.x}" y="${n.y}" rx="8" width="${W}" height="${H}" fill="${fill}" stroke="${stroke}" stroke-width="${isCur?2:1}"/>`;
    s += `<text x="${n.x+W/2}" y="${n.y+H/2+4}" text-anchor="middle" font-size="11" font-weight="600" fill="${col}">${esc(n.label)}</text>`;
  });
  return s + `</svg>`;
}

// ---- transactions ----
async function loadTransactions(autoSelect = true) {
  const txns = await getJSON('/api/transactions');
  $('txnChips').innerHTML = txns.length ? txns.map(t =>
    `<button class="txn-chip ${t.id === sel ? 'active' : ''} s-${esc(t.outcome)}" onclick="selectTxn('${esc(t.id)}')">
       <b>${esc(t.id)}</b><span class="txn-status">${esc(t.dealType)} · ${esc((t.outcome||t.status).replace(/_/g,' '))}</span></button>`).join('')
    : '<span class="empty">No transactions yet. Start one to run a hold → evaluate → pay cycle.</span>';
  if (autoSelect && !sel && txns.length) selectTxn(txns[0].id);
  updateNavBadge(txns.filter(t => t.outcome === 'HOLD_PENDING_APPROVAL' && !t.disbursed).length);
}
function updateNavBadge(count) {
  const b = $('navBadge'); if (!b) return;
  b.hidden = count === 0; b.textContent = count ? `● ${count} awaiting` : '';
}

async function newTransaction() {
  const t = await postJSON('/api/transactions?dealType=' + encodeURIComponent(selectedDealType));
  sel = t.id;
  await selectTxn(t.id);
  await loadTransactions();
}

async function selectTxn(id) {
  sel = id;
  $('lifecycle').hidden = false;
  $('detCard').hidden = true; $('approvalCard').hidden = true;
  $('examination').innerHTML = ''; $('replayResult').innerHTML = '';
  const view = await getJSON(`/api/transactions/${id}`);
  if (view.dealType && view.dealType !== selectedDealType) {
    selectedDealType = view.dealType; $('dealTypeSelect').value = view.dealType;
    await applyDealType(view.dealType);
  }
  const [ext, det, pend] = await Promise.all([
    getJSON(`/api/transactions/${id}/extraction`),
    getJSON(`/api/transactions/${id}/determination`),
    getJSON(`/api/transactions/${id}/pending`)
  ]);
  renderTxnParties(view);
  renderDocs(ext.documents); renderExtraction(ext);
  if (det && det.outcome) { renderExamination(det); renderDetermination(det, view.disbursed); }
  if (pend && pend.outcome === 'HOLD_PENDING_APPROVAL' && !view.disbursed) showApproval();
  applyTxnView(view);
  await Promise.all([refreshLedger(), refreshJournal()]);
}

function applyTxnView(v) {
  const step = currentStep(v);
  $('fundBtn').disabled = v.funded;
  $('earmarkBtn').disabled = !v.funded || v.earmarked;
  const intakeOpen = v.earmarked && !v.determined;
  ['sampleSelect', 'clearBtn'].forEach(id => $(id).disabled = !intakeOpen);
  $('uploadLabel').classList.toggle('disabled', !intakeOpen);
  $('extractBtn').disabled = !(intakeOpen && v.documentCount > 0);
  $('examineBtn').disabled = !(v.extracted && !v.determined);
  $('lockNote').hidden = !v.disbursed && !(v.determined && v.outcome === 'HOLD');
  $('docs').classList.toggle('muted', !v.earmarked);
  renderStepper(step, v);
  renderGraph(step);
}
function currentStep(v) {
  if (!v.funded) return 'COLLECT_FUNDS';
  if (!v.earmarked) return 'EARMARK';
  if (!v.extracted) return 'INTAKE_EVIDENCE';
  if (!v.determined) return 'EXAMINE';
  if (v.outcome === 'HOLD_PENDING_APPROVAL' && !v.disbursed) return 'SOLICIT_APPROVAL';
  if ((v.outcome === 'RELEASE' || v.outcome === 'PARTIAL_RELEASE') && !v.disbursed) return 'DISBURSE';
  return 'DONE';
}
function renderStepper(step, v) {
  const steps = [['COLLECT_FUNDS','Collect funds',v.funded],['EARMARK','Earmark to payees',v.earmarked],
    ['INTAKE_EVIDENCE','Intake evidence',v.extracted],['EXAMINE','Examine',v.determined],
    ['DETERMINE','Determine',v.determined],['DISBURSE','Split & disburse',v.disbursed]];
  $('stepper').innerHTML = steps.map(([k,label,done]) => {
    const cls = k === step ? 'cur' : (done ? 'done' : 'todo');
    return `<div class="step ${cls}"><span class="dot"></span><span>${esc(label)}</span></div>`;
  }).join('') + (v.outcome === 'HOLD_PENDING_APPROVAL' && !v.disbursed
    ? `<div class="step cur branch"><span class="dot"></span><span>Approval / objection</span></div>` : '');
}

// ---- wallet steps ----
async function fund() { try { await postJSON(`/api/transactions/${sel}/fund`); await reload(); } catch (e) { alert(e.message); } }
async function earmark() { try { await postJSON(`/api/transactions/${sel}/earmark`); await reload(); } catch (e) { alert(e.message); } }
async function disburse() {
  try { await postJSON(`/api/transactions/${sel}/disburse`);
    const view = await getJSON(`/api/transactions/${sel}`); const det = await getJSON(`/api/transactions/${sel}/determination`);
    renderDetermination(det, true); applyTxnView(view);
    await Promise.all([refreshLedger(), refreshJournal(), loadTransactions()]);
  } catch (e) { alert(e.message); }
}
async function reload() { const view = await getJSON(`/api/transactions/${sel}`); applyTxnView(view); await Promise.all([refreshLedger(), loadTransactions()]); }

// ---- documents ----
async function uploadFile(event) {
  const file = event.target.files[0]; if (!file) return;
  const fd = new FormData(); fd.append('file', file);
  try { const ext = await postJSON(`/api/transactions/${sel}/documents`, {body: fd}); renderDocs(ext.documents); renderExtraction(ext); await reload(); }
  catch (e) { alert(e.message); }
  event.target.value = '';
}
async function loadSample() {
  const s = $('sampleSelect').value; if (!s) return;
  const ext = await postJSON(`/api/transactions/${sel}/samples/${s}`); renderDocs(ext.documents); renderExtraction(ext); $('sampleSelect').value = ''; await reload();
}
async function clearDocs() { const ext = await del(`/api/transactions/${sel}/documents`); renderDocs(ext.documents); renderExtraction(ext); await reload(); }
function renderDocs(docs) {
  if (!docs.length) { $('docs').innerHTML = '<p class="empty">Earmark the wallet, then add documents.</p>'; return; }
  $('docs').innerHTML = docs.map(d => `<div class="doccard"><div class="doc-h"><span class="doctype">${esc(d.type)}</span>
    <span class="docname">${esc(d.filename)}</span><span class="docsize">${d.sizeBytes} B</span></div>
    <pre class="doctext">${esc(d.text)}</pre></div>`).join('');
}

// ---- extraction ----
async function extract() {
  try { const ext = await postJSON(`/api/transactions/${sel}/extract`); renderExtraction(ext); await reload(); }
  catch (e) { alert(e.message + '\n\nTip: switch AI extraction to Deterministic rules to continue.'); }
}
function renderExtraction(ext) {
  if (!ext.facts || !ext.facts.length) { $('extraction').innerHTML = '<p class="empty">Add documents, then extract.</p>'; return; }
  $('extraction').innerHTML = `<p class="hint">Extractor: <b>${esc(ext.extractor)}</b></p>` + ext.facts.map(f => `
    <div class="fact"><div class="fact-h"><span class="fact-name">${esc(f.field)}</span><span class="fact-val">${esc(f.value)}</span></div>
      <div class="bar"><div class="bar-fill ${band(f.confidence)}" style="width:${pct(f.confidence)}"></div></div>
      <div class="fact-meta">confidence ${pct(f.confidence)} · from ${esc(f.sourceDocument)}</div></div>`).join('');
}

// ---- examine → determination → disburse ----
async function examine() {
  try { const det = await postJSON(`/api/transactions/${sel}/examine`); renderExamination(det);
    const view = await getJSON(`/api/transactions/${sel}`); renderDetermination(det, view.disbursed); applyTxnView(view);
    await Promise.all([refreshLedger(), refreshJournal(), loadTransactions()]);
  } catch (e) { alert(e.message); }
}
function renderExamination(det) {
  $('examination').innerHTML = `<table><thead><tr><th>Condition</th><th>Verdict</th><th>Finding</th></tr></thead><tbody>${
    det.verdicts.map(v => `<tr><td class="mono">${esc(v.condition)}</td>
      <td><span class="pill ${esc(v.status)}">${esc(v.status.replace('_',' '))}</span></td>
      <td>${v.grade ? `<span class="pill grade">${esc(v.grade)}</span>` : ''}</td></tr>`).join('')}</tbody></table>`;
}
function renderDetermination(det, disbursed) {
  $('detCard').hidden = false;
  const showRetained = det.outcome === 'PARTIAL_RELEASE' || Number(det.retained.amount) > 0;
  const canDisburse = !disbursed && (det.outcome === 'RELEASE' || det.outcome === 'PARTIAL_RELEASE');
  const splits = det.disbursements.map(l =>
    `<tr><td>${esc(l.payeeParty)}</td><td class="mono">${esc(l.obligation)}</td><td class="num">${money(l.amount)}</td></tr>`).join('')
    || '<tr><td colspan="3" class="empty">Nothing to disburse</td></tr>';
  const residuals = det.residuals.length ? `<h3 style="margin-top:14px">Residual · re-earmarked</h3>
    <table><thead><tr><th>Residual obligation</th><th class="num">Retained</th></tr></thead><tbody>
    ${det.residuals.map(r => `<tr><td class="mono">${esc(r.residualObligation)}</td><td class="num">${money(r.retained)}</td></tr>`).join('')}</tbody></table>` : '';
  $('determination').innerHTML = `
    <div class="outcome"><span class="badge badge-${esc(det.outcome)}">${esc(det.outcome.replace(/_/g,' '))}</span>
      <div class="amounts"><div class="amt"><div class="n">${money(det.released)}</div><div class="l">To release</div></div>
        ${showRetained ? `<div class="amt"><div class="n">${money(det.retained)}</div><div class="l">To retain</div></div>` : ''}</div></div>
    <h3>SPLIT · payees</h3>
    <table><thead><tr><th>Payee</th><th>Obligation</th><th class="num">Amount</th></tr></thead><tbody>${splits}</tbody></table>
    ${residuals}
    <div style="margin-top:12px">${canDisburse
      ? `<button class="primary" onclick="disburse()">Disburse to payees (money plane)</button>
         <span class="hint">The determination is emitted; the money plane now acts on it.</span>`
      : (disbursed ? `<span class="pill MET">✓ Disbursed</span>` : '')}</div>
    <h3 style="margin-top:14px">Findings notice <span class="sub">— rule set ${esc(det.ruleSetVersion)} · evidence ${esc(det.evidenceDigestShort)}</span></h3>
    <pre class="notice" id="noticeText"></pre>`;
  $('noticeText').textContent = det.findingsNotice;
  if (det.outcome === 'HOLD_PENDING_APPROVAL' && !disbursed) showApproval(); else $('approvalCard').hidden = true;
}

// ---- approval ----
function showApproval() {
  $('approvalCard').hidden = false;
  $('approvalBody').innerHTML = `<p class="hint">Held pending approval. The counterparty approves on their portal — or here, for the demo.</p>
     <button class="primary" onclick="approve()">Approve (dual sign)</button>`;
}
async function approve() {
  try { const view = await postJSON(`/api/transactions/${sel}/approve`);
    $('approvalBody').innerHTML = `<ul class="steps">${view.steps.map(s =>
      `<li><span class="r ${esc(s.result)}">${esc(s.result)}</span><span>${esc(s.label)}</span><span class="d">— ${esc(s.detail)}</span></li>`).join('')}</ul>`;
    renderDetermination(view.determination, true);
    const tv = await getJSON(`/api/transactions/${sel}`); applyTxnView(tv);
    await Promise.all([refreshLedger(), refreshJournal(), loadTransactions()]);
  } catch (e) { alert(e.message); }
}

// ---- tree wallet, journal, replay ----
async function refreshLedger() {
  const l = await getJSON(`/api/transactions/${sel}/ledger`);
  const r = l.reconciliation;
  // tiles + reconciliation (near the collect/earmark actions)
  $('ledger').innerHTML = `
    <div class="tiles" style="margin-top:12px">
      <div class="tile"><div class="n">${money(l.held)}</div><div class="l">Held</div></div>
      <div class="tile"><div class="n">${money(l.disbursed)}</div><div class="l">Disbursed</div></div>
      <div class="tile"><div class="n">${money(l.reserved)}</div><div class="l">Earmarked</div></div>
      <div class="tile"><div class="n">${money(l.unallocated)}</div><div class="l">Unallocated</div></div>
    </div>
    <div class="recon ${r.balanced ? '' : 'drift'}" style="margin-top:10px"><span class="dot"></span>
      <span><b>${r.balanced ? 'RECONCILED' : 'DRIFT'}</b> · Σ entitlements ${money(r.entitlementTotal)} = Σ earmarks ${money(r.earmarkTotal)}</span></div>`;
  // tree hierarchy (its own card)
  const children = [];
  l.earmarks.forEach(e => children.push(
    `<li class="tw-bucket"><span>${esc(e.payee)} <span class="mono">· ${esc(e.obligation)}</span></span>
       <span class="tw-tag">reserved</span><span class="num">${money(e.amount)}</span></li>`));
  if (Number(l.unallocated.amount) > 0) children.push(
    `<li class="tw-pool"><span>Unallocated · future milestones / retained</span><span class="num">${money(l.unallocated)}</span></li>`);
  if (!children.length) children.push(`<li class="empty">Not yet earmarked — fund and earmark to reserve payee sub-wallets.</li>`);
  $('wallet').innerHTML = `
    <div class="tree">
      <div class="tw-root"><span>🏦 Escrow pool (this transaction) · held</span><span class="num">${money(l.held)}</span></div>
      <ul class="tw-children">${children.join('')}</ul>
      ${Number(l.disbursed.amount) > 0 ? `<div class="tw-out"><span>↳ Paid out to payees</span><span class="num">${money(l.disbursed)}</span></div>` : ''}
    </div>
    <p class="hint">Pool → per-payee sub-wallets (earmarks) → paid out. Reserved never exceeds held.</p>`;
}
async function refreshJournal() {
  const j = await getJSON(`/api/transactions/${sel}/journal`);
  if (!j.entries.length) { $('journal').innerHTML = '<p class="empty">No entries yet.</p>'; return; }
  $('journal').innerHTML = `<table><thead><tr><th>Entry</th><th>Outcome</th><th>Evidence</th><th class="num">Facts</th><th></th></tr></thead><tbody>${
    j.entries.map(e => `<tr><td class="mono">${esc(e.id)}</td><td><span class="pill grade">${esc(e.outcome)}</span></td>
      <td class="mono">${esc(e.evidenceDigestShort)}</td><td class="num">${e.factCount}</td>
      <td><button class="ghost" onclick="replay('${esc(e.determinationId)}')">Replay</button></td></tr>`).join('')}</tbody></table>`;
}
async function replay(detId) {
  try { const r = await postJSON(`/api/transactions/${sel}/replay/${detId}`);
    $('replayResult').innerHTML = `<div class="recon ${r.identical ? '' : 'drift'}" style="margin-top:12px"><span class="dot"></span>
      <span><b>${r.identical ? 'REPLAY IDENTICAL' : 'REPLAY DIVERGED'}</b> · ${esc(detId)} → ${esc(r.replayedOutcome)} ${money(r.replayedReleased)}
      <span class="d">(the model reads; the model never decides)</span></span></div>`;
  } catch (e) { alert(e.message); }
}

// ---- extraction mode ----
async function loadExtractionMode() {
  const x = await getJSON('/api/extraction');
  const status = $('extStatus'), btn = $('extBtn');
  if (x.mode === 'LLM') {
    status.className = 'pill ' + (x.liveAvailable ? 'live' : 'live-off');
    status.textContent = x.liveAvailable ? `Live AI · ${x.model}` : `Live AI · no key`;
    btn.textContent = '◂ Deterministic rules'; btn.dataset.next = 'RULES';
  } else {
    status.className = 'pill fixture'; status.textContent = 'Deterministic rules';
    btn.textContent = x.liveAvailable ? 'Use live AI ▸' : 'Use live AI ▸ (needs key)'; btn.dataset.next = 'LLM';
  }
}
async function toggleExtraction() { await postJSON('/api/extraction/' + ($('extBtn').dataset.next || 'LLM')); await loadExtractionMode(); }

init().catch(e => alert('Load failed: ' + e.message));
