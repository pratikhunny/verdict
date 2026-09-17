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
  if (!$('orderForm').hidden) cancelNewOrder();
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
  renderTerms(c); renderParties(c); renderParticipants(c);
  currentGraphData = g;
  renderGraph();
  $('dealTypeNote').textContent = g.name.startsWith('Marketplace')
    ? 'Marketplace trade — pro-rata partial release, three payees.'
    : 'Construction retention (RERA) — milestone-binary release on an engineer certificate.';
  $('sampleSelect').innerHTML = '<option value="">Load a sample…</option>' +
    samples.map(s => `<option value="${esc(s.key)}">${esc(s.label)}</option>`).join('');
}

function payeeRole(id) {
  return (id || '').replace(/^PAYEE-/, '').toLowerCase().replace(/\b\w/g, m => m.toUpperCase()).replace(/-/g, ' ');
}
function renderTerms(c) {
  const chips = c.chips.map(ch => `<div class="term"><div class="term-l">${esc(ch.label)}</div><div class="term-v">${esc(ch.value)}</div></div>`).join('');
  const payees = c.payees.map(o =>
    `<tr><td class="mono">${esc(o.id)}</td><td><span class="role-pill">${esc(payeeRole(o.payee))}</span></td><td>${esc(o.rule)}</td>
         <td>${o.severable ? '<span class="tag sev">severable</span>' : '<span class="tag whole">whole</span>'}</td></tr>`).join('');
  const conds = c.conditions.map(cd => `<span class="node-chip">${esc(cd.kind.replace(/_/g,' '))}</span>`).join(' ');
  $('terms').innerHTML = `
    <div class="terms-grid">${chips}</div>
    <h3 style="margin-top:16px">Obligations &amp; split rules <span class="sub">— by role, in %, not amounts</span></h3>
    <table><thead><tr><th>Obligation</th><th>Payee role</th><th>Split rule</th><th>Severability</th></tr></thead><tbody>${payees}</tbody></table>
    <h3 style="margin-top:14px">Conditions the engine examines</h3><div class="cond-row">${conds}</div>
    <h3 style="margin-top:16px">Roles &amp; mandates <span class="sub">— the authority each role carries, defined by the agreement</span></h3>
    <table class="resp"><thead><tr><th>Role</th><th>Signing authority (mandate)</th></tr></thead><tbody>${
      c.responsibilities.map(r => `<tr><td><span class="role-pill">${esc(r.role)}</span></td><td class="muted">${esc(r.mandate)}</td></tr>`).join('')}</tbody></table>
    <p class="hint">Tolerance rule: ${esc(c.tolerance)}. Roles &amp; mandates are fixed by the agreement; which company fills each role is §4; amounts &amp; quantities are per transaction.</p>`;
}

function initial(name) { return (name || '?').trim().charAt(0).toUpperCase(); }
function typeClass(t) { return /bank/i.test(t) ? 'bank' : (/regulator|authority/i.test(t) ? 'reg' : (/individual/i.test(t) ? 'ind' : 'corp')); }

function renderParties(c) {
  $('parties').innerHTML = `<div class="party-grid">${c.parties.map(p => `
    <div class="party-card">
      <div class="avatar ${typeClass(p.type)}">${esc(initial(p.name))}</div>
      <div class="party-body"><div class="party-name">${esc(p.name)}</div>
        <div class="party-meta">${esc(p.type)} · ${esc(p.jurisdiction)}</div></div>
      <span class="screen ok">✓ ${esc(p.screening)}</span>
    </div>`).join('')}</div>
    <p class="hint">Onboarding is a one-time L0 step (identity, sanctions/CDD screening). Screening is re-checked at every instruction — fail-closed.</p>`;
}

function renderParticipants(c) {
  $('responsibilities').innerHTML = `
    <table class="resp"><thead><tr><th>Onboarded entity (§3)</th><th></th><th>Fills role (§2)</th></tr></thead><tbody>${
    c.responsibilities.map(r => `<tr><td><b>${esc(r.party)}</b></td><td class="muted" style="width:24px">→</td>
      <td><span class="role-pill">${esc(r.role)}</span></td></tr>`).join('')}</tbody></table>
    <p class="hint">Just the assignment — an entity to a role. The role's authority is defined in §2. Fixed for every transaction under this master agreement.</p>`;
}

// Per-transaction: amount + order terms (quantity/date). Parties are fixed under §4, shown as a reference.
function renderTxnParties(v) {
  if (!currentContract) { $('txnParties').innerHTML = ''; return; }
  const resp = currentContract.responsibilities || [];
  const payer = resp.find(r => /payer/i.test(r.role)), payee = resp.find(r => /payee/i.test(r.role));
  const between = payer && payee
    ? `<div class="between"><span class="p-ent">${esc(payer.party)}</span><span class="p-role">Payer</span>
         <span class="arrow">→</span><span class="p-ent">${esc(payee.party)}</span><span class="p-role">Payee</span></div>` : '';
  const terms = (v.orderTerms || []).map(ch =>
    `<div class="term"><div class="term-l">${esc(ch.label)}</div><div class="term-v">${esc(ch.value)}</div></div>`).join('');
  $('txnParties').innerHTML = `
    ${between}
    <div class="tiles" style="grid-template-columns:1fr 1fr">
      <div class="tile hero"><div class="n">${money(v.fundAmount)}</div><div class="l">Order amount — to be collected</div></div>
      <div class="tile"><div class="n">${money(v.milestoneValue)}</div><div class="l">Milestone entitlement at stake</div></div>
    </div>
    <p class="hint" style="margin-top:2px">Planned figures for this order. Nothing is collected until the wallet is funded (Ⓑ) — the actual balance is <b>Held</b>, below.</p>
    <h3 style="margin-top:12px">Order terms <span class="sub">— set for this order, not the agreement</span></h3>
    <div class="terms-grid">${terms}</div>
    <p class="hint">The counterparties are the fixed deal participants (§4); only the amount, quantity, dates and evidence change per transaction.</p>`;
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
  const kindFill = { money: '#faf2e2', decision: '#e8f3ec', evidence: '#edeffb', branch: '#fbeede' };
  const kindStroke = { money: '#B0770E', decision: '#0E6E3C', evidence: '#3B4CA0', branch: '#C2410C' };

  let s = `<svg viewBox="0 0 ${maxX} ${maxY}" width="100%" preserveAspectRatio="xMidYMin meet" font-family="-apple-system,system-ui,sans-serif">`;
  s += `<defs>
        <marker id="arr" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto"><path d="M0,0 L8,4 L0,8 z" fill="#9aa4b2"/></marker>
        <marker id="arrb" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto"><path d="M0,0 L8,4 L0,8 z" fill="#C2410C"/></marker>
        <filter id="nsh" x="-20%" y="-20%" width="140%" height="150%"><feDropShadow dx="0" dy="1" stdDeviation="1.2" flood-color="#1f2a44" flood-opacity="0.12"/></filter>
        <filter id="glow" x="-60%" y="-60%" width="220%" height="220%"><feDropShadow dx="0" dy="0" stdDeviation="5" flood-color="#38B449" flood-opacity="0.55"/></filter>
        </defs>`;
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
    const filter = isCur ? 'filter="url(#glow)"' : 'filter="url(#nsh)"';
    s += `<rect x="${n.x}" y="${n.y}" rx="10" width="${W}" height="${H}" fill="${fill}" stroke="${stroke}" stroke-width="${isCur?2:1.25}" ${filter}/>`;
    s += `<text x="${n.x+W/2}" y="${n.y+H/2+4}" text-anchor="middle" font-size="11" font-weight="700" letter-spacing="0.2" fill="${col}">${esc(n.label)}</text>`;
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

let currentOrderForm = null;

async function openNewOrder() {
  const spec = await getJSON('/api/orderform?dealType=' + encodeURIComponent(selectedDealType));
  currentOrderForm = spec;
  $('ofTitle').textContent = spec.title + ' · ' + spec.dealType;
  $('ofError').textContent = '';
  $('ofFields').innerHTML = spec.fields.map(f => {
    const t = f.type === 'money' ? 'text' : (f.type === 'number' ? 'text' : f.type);
    const suffix = f.suffix ? `<span class="of-suffix">${esc(f.suffix)}</span>` : '';
    return `<div class="of-field">
      <label>${esc(f.label)}</label>
      <div class="of-input ${f.suffix ? 'has-suffix' : ''}">
        <input type="${t}" data-key="${esc(f.key)}" data-kind="${esc(f.type)}" placeholder="${esc(f.demo)}"/>${suffix}
      </div>
      <span class="of-help">${esc(f.help || '')}</span></div>`;
  }).join('');
  $('orderForm').hidden = false;
  $('orderForm').scrollIntoView({block: 'nearest'});
}

function fillDemo() {
  if (!currentOrderForm) return;
  const byKey = Object.fromEntries(currentOrderForm.fields.map(f => [f.key, f.demo]));
  $('ofFields').querySelectorAll('input[data-key]').forEach(i => { i.value = byKey[i.dataset.key] || ''; });
  $('ofError').textContent = '';
}

function cancelNewOrder() { $('orderForm').hidden = true; currentOrderForm = null; }

async function submitNewOrder() {
  const inputs = {};
  let missing = null;
  $('ofFields').querySelectorAll('input[data-key]').forEach(i => {
    const val = i.value.trim();
    if (!val && !missing) missing = i;
    inputs[i.dataset.key] = val;
  });
  if (missing) {
    $('ofError').textContent = 'Fill every field — or click "Fill for demo".';
    missing.focus(); return;
  }
  try {
    const t = await postJSON('/api/transactions?dealType=' + encodeURIComponent(selectedDealType),
      {headers: {'Content-Type': 'application/json'}, body: JSON.stringify(inputs)});
    $('orderForm').hidden = true; currentOrderForm = null;
    sel = t.id;
    await selectTxn(t.id);
    await loadTransactions();
  } catch (e) { $('ofError').textContent = e.message; }
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
function outcomeMeaning(det) {
  const total = money({currency: det.released.currency,
    amount: (Number(det.released.amount) + Number(det.retained.amount)).toString()});
  switch (det.outcome) {
    case 'RELEASE':
      return `Every condition is met. The full tranche of <b>${money(det.released)}</b> releases to the payees — no human touched it.`;
    case 'PARTIAL_RELEASE':
      return `A <b>severable</b> condition fell short. <b>${money(det.released)}</b> of ${total} releases now, pro-rated to the evidence; the remainder is re-earmarked as a residual obligation — <b>not refused</b>. No system does this automatically; every escrow officer does it by hand.`;
    case 'HOLD_PENDING_APPROVAL':
      return `A finding is <b>waivable, but not by the engine</b>. Release is held and an approval request is drafted for the counterparty. Their approval re-enters as evidence and the engine re-examines — it never goes around the rules.`;
    case 'HOLD':
      return `A <b>substantive</b> condition failed. Nothing releases; the funds stay held pending resolution. Fail-closed.`;
    default:
      return '';
  }
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
    <div class="verdict-meaning">${outcomeMeaning(det)}</div>
    <h3>Split · payees</h3>
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
