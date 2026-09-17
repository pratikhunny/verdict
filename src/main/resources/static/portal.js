// Verdict counterparty portal — the buyer's view. Shares backend state with the ops console.

const $ = (id) => document.getElementById(id);
const esc = (s) => String(s ?? '').replace(/[&<>"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
const money = (m) => m ? `${m.currency} ${Number(m.amount).toLocaleString('en-US', {minimumFractionDigits: 2})}` : '—';

async function apiError(r) {
  const ct = r.headers.get('content-type') || '';
  if (ct.includes('application/json')) { try { return (await r.json()).error || r.statusText; } catch (e) {} }
  return `HTTP ${r.status}. Open this app at http://localhost:8080 (run ./scripts/run-app.sh) — the IDE file preview has no /api backend.`;
}
async function getJSON(p) { const r = await fetch(p); if (!r.ok) throw new Error(await apiError(r)); return r.json(); }
async function postJSON(p) { const r = await fetch(p, {method: 'POST'}); if (!r.ok) throw new Error(await apiError(r)); return r.json(); }

let awaitingTxn = null;

async function init() { await refresh(); }

async function refresh() {
  const txns = await getJSON('/api/transactions');
  const pending = txns.filter(t => t.outcome === 'HOLD_PENDING_APPROVAL' && !t.disbursed);
  const t = pending[0];
  awaitingTxn = t ? t.id : null;
  const badge = $('navBadge');
  if (badge) { badge.hidden = pending.length === 0; badge.textContent = pending.length ? `● ${pending.length} awaiting` : ''; }

  if (!awaitingTxn) {
    $('pending').innerHTML = '<p class="empty">Nothing is awaiting your approval right now.</p>' +
      '<p class="hint">When the bank examines a submission with a waivable finding, the request appears here.</p>';
    $('ledger').innerHTML = '<p class="empty">—</p>';
    return;
  }
  const p = await getJSON(`/api/transactions/${awaitingTxn}/pending`);
  $('pending').innerHTML = `
    <div class="outcome"><span class="badge badge-HOLD_PENDING_APPROVAL">HOLD PENDING APPROVAL</span>
      <span class="role-pill">${esc(t.id)} · ${esc(t.dealType)}</span></div>
    <div class="tiles" style="grid-template-columns:1fr 1fr">
      <div class="tile hero"><div class="n">${money(t.fundAmount)}</div><div class="l">Funds in escrow</div></div>
      <div class="tile"><div class="n">${money(t.milestoneValue)}</div><div class="l">At stake this milestone</div></div>
    </div>
    <p class="hint">A finding requires your approval before release. Your Treasury Manager may approve up to
      USD 50,000 alone; above that, the CFO must countersign (four-eyes).</p>
    <pre class="notice" id="noticeText"></pre>
    <button class="primary" style="margin-top:14px" onclick="approve()">Approve — Treasury &amp; CFO (dual sign)</button>`;
  $('noticeText').textContent = p.findingsNotice;
  await refreshLedger(awaitingTxn);
}

async function approve() {
  try {
    const view = await postJSON(`/api/transactions/${awaitingTxn}/approve`);
    const steps = view.steps.map(s =>
      `<li><span class="r ${esc(s.result)}">${esc(s.result)}</span><span>${esc(s.label)}</span>
           <span class="d">— ${esc(s.detail)}</span></li>`).join('');
    $('pending').innerHTML = `
      <div class="outcome"><span class="badge badge-RELEASE">RELEASED</span>
        <div class="amounts"><div class="amt"><div class="n">${money(view.determination.released)}</div><div class="l">Released</div></div></div></div>
      <p class="hint">Your approval entered the engine as evidence; it re-examined and released. It did not go around the engine.</p>
      <ul class="steps">${steps}</ul>`;
    await refreshLedger(awaitingTxn);
  } catch (e) { alert(e.message); }
}

async function refreshLedger(txn) {
  const l = await getJSON(`/api/transactions/${txn}/ledger`);
  const r = l.reconciliation;
  $('ledger').innerHTML = `
    <div class="tiles">
      <div class="tile"><div class="n">${money(l.held)}</div><div class="l">Held</div></div>
      <div class="tile"><div class="n">${money(l.disbursed)}</div><div class="l">Disbursed</div></div>
      <div class="tile"><div class="n">${money(l.reserved)}</div><div class="l">Earmarked</div></div>
      <div class="tile"><div class="n">${money(l.unallocated)}</div><div class="l">Unallocated</div></div>
    </div>
    <div class="recon ${r.balanced ? '' : 'drift'}"><span class="dot"></span>
      <span><b>${r.balanced ? 'RECONCILED' : 'DRIFT'}</b> · Σ entitlements ${money(r.entitlementTotal)} = Σ earmarks ${money(r.earmarkTotal)}</span></div>`;
}

init().catch(e => alert('Load failed: ' + e.message));
