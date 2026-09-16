// Verdict ops console — dependency-free. Drives the real engine via the REST API.

const $ = (id) => document.getElementById(id);
const esc = (s) => String(s).replace(/[&<>"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
const money = (m) => m ? `${m.currency} ${Number(m.amount).toLocaleString('en-US', {minimumFractionDigits: 2})}` : '—';

async function getJSON(path) { const r = await fetch(path); if (!r.ok) throw new Error((await r.json()).error || r.statusText); return r.json(); }
async function postJSON(path) { const r = await fetch(path, {method: 'POST'}); if (!r.ok) throw new Error((await r.json()).error || r.statusText); return r.json(); }

let currentDetId = null;

async function init() {
  const deal = await getJSON('/api/deal');
  $('dealChips').innerHTML = [
    `<span class="chip"><b>${esc(deal.id)}</b></span>`,
    `<span class="chip">Value <b>${money(deal.contractValue)}</b></span>`,
    `<span class="chip"><b>${deal.quantity.toLocaleString()}</b> units · ${esc(deal.goods)}</span>`,
    `<span class="chip">Latest shipment <b>${esc(deal.latestShipmentDate)}</b></span>`,
    `<span class="chip">Buyer MY · Supplier VN · SCB agent</span>`
  ].join('');
  await Promise.all([loadGraphs(), refreshLedger(), refreshJournal(), loadExtraction()]);
}

async function loadExtraction() {
  const x = await getJSON('/api/extraction');
  const status = $('extStatus'), btn = $('extBtn');
  if (x.mode === 'LIVE') {
    status.className = 'pill ' + (x.liveAvailable ? 'live' : 'live-off');
    status.textContent = x.liveAvailable ? `Live AI · ${x.model}` : `Live AI · no key (${x.model})`;
    btn.textContent = '◂ Fall back to fixtures';
  } else {
    status.className = 'pill fixture';
    status.textContent = 'Fixtures · deterministic';
    btn.textContent = x.liveAvailable ? 'Use live AI ▸' : 'Use live AI ▸ (needs key)';
  }
  btn.dataset.next = x.mode === 'LIVE' ? 'FIXTURE' : 'LIVE';
}

async function toggleExtraction() {
  const next = $('extBtn').dataset.next || 'LIVE';
  await postJSON('/api/extraction/' + next);
  await loadExtraction();
}

async function runPack(pack) {
  try {
    const det = await postJSON('/api/packs/' + pack);
    renderDetermination(det);
    await Promise.all([refreshLedger(), refreshJournal()]);
  } catch (e) {
    alert(e.message + '\n\nTip: switch AI extraction back to Fixtures to continue.');
  }
}

async function resetDeal() {
  await postJSON('/api/reset');
  $('determination').innerHTML = '<p class="empty">Run an evidence pack to examine it.</p>';
  $('approvalCard').hidden = true;
  $('replayResult').innerHTML = '';
  currentDetId = null;
  await Promise.all([refreshLedger(), refreshJournal()]);
}

async function approve() {
  try {
    const view = await postJSON('/api/approve');
    const steps = view.steps.map(s =>
      `<li><span class="r ${esc(s.result)}">${esc(s.result)}</span>
           <span>${esc(s.label)}</span><span class="d">— ${esc(s.detail)}</span></li>`).join('');
    $('approvalBody').innerHTML = `<ul class="steps">${steps}</ul>`;
    renderDetermination(view.determination);
    await Promise.all([refreshLedger(), refreshJournal()]);
  } catch (e) { alert(e.message); }
}

function renderDetermination(det) {
  currentDetId = det.id;
  const showRetained = det.outcome === 'PARTIAL_RELEASE' || Number(det.retained.amount) > 0;
  const splits = det.disbursements.map(l =>
    `<tr><td>${esc(l.payeeParty)}</td><td class="mono">${esc(l.obligation)}</td>
         <td class="num">${money(l.amount)}</td></tr>`).join('') || '<tr><td colspan="3" class="empty">Nothing disbursed</td></tr>';
  const residuals = det.residuals.length ? `
    <h2 style="margin-top:16px">Residual · re-earmarked</h2>
    <table><thead><tr><th>Residual obligation</th><th class="num">Retained</th></tr></thead><tbody>
    ${det.residuals.map(r => `<tr><td class="mono">${esc(r.residualObligation)}</td><td class="num">${money(r.retained)}</td></tr>`).join('')}
    </tbody></table>` : '';
  const verdicts = det.verdicts.map(v =>
    `<tr><td class="mono">${esc(v.condition)}</td>
         <td><span class="pill ${esc(v.status)}">${esc(v.status)}</span></td>
         <td>${v.grade ? `<span class="pill grade">${esc(v.grade)}</span>` : ''}</td></tr>`).join('');

  $('determination').innerHTML = `
    <div class="outcome">
      <span class="badge badge-${esc(det.outcome)}">${esc(det.outcome.replace(/_/g,' '))}</span>
      <div class="amounts">
        <div class="amt"><div class="n">${money(det.released)}</div><div class="l">Released</div></div>
        ${showRetained ? `<div class="amt"><div class="n">${money(det.retained)}</div><div class="l">Retained</div></div>` : ''}
      </div>
    </div>
    <h2>Conditions</h2>
    <table><thead><tr><th>Condition</th><th>Verdict</th><th>Finding</th></tr></thead><tbody>${verdicts}</tbody></table>
    <h2 style="margin-top:16px">Disbursement · multi-payee split</h2>
    <table><thead><tr><th>Payee</th><th>Obligation</th><th class="num">Amount</th></tr></thead><tbody>${splits}</tbody></table>
    ${residuals}
    <h2 style="margin-top:16px">Findings notice <span class="sub">— rule set ${esc(det.ruleSetVersion)} · evidence ${esc(det.evidenceDigestShort)}</span></h2>
    <pre class="notice" id="noticeText"></pre>`;
  $('noticeText').textContent = det.findingsNotice;

  $('approvalCard').hidden = det.outcome !== 'HOLD_PENDING_APPROVAL';
  if (det.outcome === 'HOLD_PENDING_APPROVAL') {
    $('approvalBody').innerHTML =
      `<p class="hint">Late shipment held pending approval. The buyer approves on the counterparty
       portal — or here, for the demo.</p>
       <button class="primary" onclick="approve()">Approve as buyer (Treasury → CFO)</button>`;
  }
}

async function refreshLedger() {
  const l = await getJSON('/api/ledger');
  const earmarks = l.earmarks.map(e =>
    `<tr><td class="mono">${esc(e.obligation)}</td><td class="num">${money(e.amount)}</td></tr>`).join('')
    || '<tr><td colspan="2" class="empty">No active earmarks</td></tr>';
  const r = l.reconciliation;
  $('ledger').innerHTML = `
    <div class="tiles">
      <div class="tile"><div class="n">${money(l.held)}</div><div class="l">Held balance</div></div>
      <div class="tile"><div class="n">${money(l.disbursed)}</div><div class="l">Disbursed</div></div>
      <div class="tile"><div class="n">${money(l.reserved)}</div><div class="l">Earmarked</div></div>
      <div class="tile"><div class="n">${money(l.unallocated)}</div><div class="l">Unallocated</div></div>
    </div>
    <table><thead><tr><th>Earmark (obligation)</th><th class="num">Reserved</th></tr></thead><tbody>${earmarks}</tbody></table>
    <div class="recon ${r.balanced ? '' : 'drift'}">
      <span class="dot"></span>
      <span><b>${r.balanced ? 'RECONCILED' : 'DRIFT'}</b> · Σ entitlements ${money(r.entitlementTotal)} = Σ earmarks ${money(r.earmarkTotal)}</span>
    </div>`;
}

async function refreshJournal() {
  const j = await getJSON('/api/journal');
  if (!j.entries.length) { $('journal').innerHTML = '<p class="empty">No entries yet.</p>'; return; }
  const rows = j.entries.map(e =>
    `<tr><td class="mono">${esc(e.id)}</td>
         <td><span class="pill grade">${esc(e.outcome)}</span></td>
         <td class="mono">${esc(e.evidenceDigestShort)}</td>
         <td class="num">${e.factCount}</td>
         <td><button class="ghost" onclick="replay('${esc(e.determinationId)}')">Replay</button></td></tr>`).join('');
  $('journal').innerHTML = `
    <table><thead><tr><th>Entry</th><th>Outcome</th><th>Evidence</th><th class="num">Facts</th><th></th></tr></thead>
    <tbody>${rows}</tbody></table>`;
}

async function replay(detId) {
  try {
    const r = await postJSON('/api/replay/' + detId);
    $('replayResult').innerHTML = `
      <div class="recon ${r.identical ? '' : 'drift'}" style="margin-top:12px">
        <span class="dot"></span>
        <span><b>${r.identical ? 'REPLAY IDENTICAL' : 'REPLAY DIVERGED'}</b> ·
          ${esc(detId)} → ${esc(r.replayedOutcome)} ${money(r.replayedReleased)}
          <span class="d">(the model reads; the model never decides)</span></span>
      </div>`;
  } catch (e) { alert(e.message); }
}

async function loadGraphs() {
  const graphs = await getJSON('/api/graphs');
  $('graphs').innerHTML = graphs.map((g, i) => {
    const nodes = g.nodes.map(n => `<span class="node-chip">${esc(n.replace(/_/g,' '))}</span>`).join('<span class="arrow">›</span>');
    return `<div class="graph-row ${i === 0 ? 'hero' : ''}"><span class="gname">${esc(g.name)}</span>${nodes}</div>`;
  }).join('');
}

init().catch(e => alert('Load failed: ' + e.message));
