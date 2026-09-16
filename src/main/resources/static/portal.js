// Verdict counterparty portal — the buyer's view. Shares the same backend state as the ops console.

const $ = (id) => document.getElementById(id);
const esc = (s) => String(s).replace(/[&<>"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
const money = (m) => m ? `${m.currency} ${Number(m.amount).toLocaleString('en-US', {minimumFractionDigits: 2})}` : '—';

async function getJSON(p) { const r = await fetch(p); if (!r.ok) throw new Error((await r.json()).error || r.statusText); return r.json(); }
async function postJSON(p) { const r = await fetch(p, {method: 'POST'}); if (!r.ok) throw new Error((await r.json()).error || r.statusText); return r.json(); }

async function init() {
  const deal = await getJSON('/api/deal');
  $('dealChips').innerHTML = [
    `<span class="chip"><b>${esc(deal.id)}</b></span>`,
    `<span class="chip">Value <b>${money(deal.contractValue)}</b></span>`,
    `<span class="chip"><b>${deal.quantity.toLocaleString()}</b> units · ${esc(deal.goods)}</span>`
  ].join('');
  await Promise.all([refreshPending(), refreshLedger()]);
}

async function refreshPending() {
  const p = await getJSON('/api/pending');
  if (p.pending === false || p.outcome !== 'HOLD_PENDING_APPROVAL') {
    $('pending').innerHTML = '<p class="empty">Nothing is awaiting your approval right now.</p>' +
      '<p class="hint">When the ops team examines a submission with a waivable finding, the request appears here.</p>';
    return;
  }
  $('pending').innerHTML = `
    <div class="outcome">
      <span class="badge badge-HOLD_PENDING_APPROVAL">HOLD PENDING APPROVAL</span>
    </div>
    <p class="hint">A finding on the shipment tranche requires your approval before release.
      Your Treasury Manager may approve up to USD 50,000 alone; above that, the CFO must countersign.</p>
    <pre class="notice" id="noticeText"></pre>
    <button class="primary" style="margin-top:14px" onclick="approve()">Approve — Treasury &amp; CFO (dual sign)</button>`;
  $('noticeText').textContent = p.findingsNotice;
}

async function approve() {
  try {
    const view = await postJSON('/api/approve');
    const steps = view.steps.map(s =>
      `<li><span class="r ${esc(s.result)}">${esc(s.result)}</span>
           <span>${esc(s.label)}</span><span class="d">— ${esc(s.detail)}</span></li>`).join('');
    $('pending').innerHTML = `
      <div class="outcome"><span class="badge badge-RELEASE">RELEASED</span>
        <div class="amounts"><div class="amt"><div class="n">${money(view.determination.released)}</div><div class="l">Released</div></div></div>
      </div>
      <p class="hint">Your approval entered the engine as evidence; it re-examined and released.
        It did not go around the engine.</p>
      <ul class="steps">${steps}</ul>`;
    await refreshLedger();
  } catch (e) { alert(e.message); }
}

async function refreshLedger() {
  const l = await getJSON('/api/ledger');
  const r = l.reconciliation;
  $('ledger').innerHTML = `
    <div class="tiles">
      <div class="tile"><div class="n">${money(l.held)}</div><div class="l">Held balance</div></div>
      <div class="tile"><div class="n">${money(l.disbursed)}</div><div class="l">Disbursed</div></div>
      <div class="tile"><div class="n">${money(l.reserved)}</div><div class="l">Earmarked</div></div>
      <div class="tile"><div class="n">${money(l.unallocated)}</div><div class="l">Unallocated</div></div>
    </div>
    <div class="recon ${r.balanced ? '' : 'drift'}"><span class="dot"></span>
      <span><b>${r.balanced ? 'RECONCILED' : 'DRIFT'}</b> · Σ entitlements ${money(r.entitlementTotal)} = Σ earmarks ${money(r.earmarkTotal)}</span></div>`;
}

init().catch(e => alert('Load failed: ' + e.message));
