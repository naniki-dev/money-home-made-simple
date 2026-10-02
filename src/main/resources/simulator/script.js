let API = '';

// Dynamically auto-detect backend server port
async function detectApi() {
    const forced = new URLSearchParams(location.search).get('api');
    if (forced) { API = forced.replace(/\/$/, ''); return; }

    const candidates = location.protocol === 'file:'
        ? ['http://localhost:7070', 'http://localhost:8080']
        : ['', 'http://localhost:7070', 'http://localhost:8080'];

    for (const base of candidates) {
        try {
            const r = await fetch(base + '/health', { cache: 'no-store' });
            if (r.ok) { API = base; return; }
        } catch (e) {}
    }
    API = 'http://localhost:7070';
}

const $ = (id) => document.getElementById(id);
const esc = (s) => String(s || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
const money = (v) => Number(v || 0).toFixed(2);

let rateHistory = [];

async function refreshDashboard() {
    try {
        // 1. Fetch live rate from DemoController (/api/rate)
        const rateRes = await fetch(API + '/api/rate');
        if (rateRes.ok) {
            const rateData = await rateRes.json();
            const ourRate = parseFloat(rateData.rate || 0);
            const midMarket = parseFloat(rateData.midMarket || 0);
            const margin = (ourRate - midMarket).toFixed(2);

            if ($('fxMid'))$('fxMid').innerText = 'R' + money(midMarket);
            if ($('fxOurs'))$('fxOurs').innerText = 'R' + money(ourRate);
            if ($('fxMargin'))$('fxMargin').innerText = 'R' + money(margin);

            rateHistory.push(ourRate);
            if (rateHistory.length > 30) rateHistory.shift();
            drawSparkline();
        }

        // 2. Fetch live transfers from DemoController (/api/transfers)
        const transferRes = await fetch(API + '/api/transfers');
        if (transferRes.ok) {
            const transfers = await transferRes.json();
            renderTransfers(transfers);
        }

        // 3. Fetch recent SMS outbox from AdminController (/api/sms)
        const smsRes = await fetch(API + '/api/sms');
        if (smsRes.ok) {
            const smsList = await smsRes.json();
            renderSms(smsList);
        }

        setOnlineStatus(true);
    } catch (e) {
        setOnlineStatus(false);
    }
}

function renderTransfers(transfers) {
    const el = $('trackerList');
    if (!el) return;
    if (!transfers || transfers.length === 0) {
        el.innerHTML = '<div class="text-xs text-slate-500 italic py-6 text-center">No transfers yet. Dial *120# to send money.</div>';
        return;
    }

    el.innerHTML = transfers.map(t => `
        <div class="bg-slate-800/70 border border-slate-700 rounded-xl p-3">
            <div class="flex items-center justify-between mb-1">
                <span class="font-mono font-bold text-emerald-300 text-sm">${esc(t.reference)}</span>
                <span class="text-[10px] font-bold text-amber-300 bg-amber-500/20 px-2 py-0.5 rounded border border-amber-500/30">${esc(t.status)}</span>
            </div>
            <div class="text-xs text-slate-300 font-mono">
                Paid R${money(t.amountZar)} &rarr; <strong class="text-white">$${money(t.receiveUsd)}</strong>
            </div>
            <div class="text-[10px] text-slate-500 font-mono mt-1">From: ${esc(t.from)} | To: ${esc(t.to)}</div>
        </div>
    `).join('');
}

function renderSms(smsList) {
    const el = $('smsList');
    if (!el) return;
    if ($('smsTotal'))$('smsTotal').innerText = smsList.length;
    if (!smsList || smsList.length === 0) {
        el.innerHTML = '<div class="text-xs text-slate-500 italic py-6 text-center">No SMS sent yet.</div>';
        return;
    }

    el.innerHTML = smsList.map(m => `
        <div class="bg-slate-800/70 border border-slate-700 rounded-lg p-2.5">
            <div class="text-[10px] font-mono text-slate-400 mb-1">&rarr; To: ${esc(m.to)}</div>
            <div class="text-xs text-slate-200">${esc(m.text)}</div>
        </div>
    `).join('');
}

function drawSparkline() {
    const spark = $('fxSpark');
    if (!spark || rateHistory.length < 2) return;
    const min = Math.min(...rateHistory);
    const max = Math.max(...rateHistory);
    const span = Math.max(max - min, 0.05);

    const points = rateHistory.map((v, i) => {
        const x = (i / (rateHistory.length - 1)) * 200;
        const y = 36 - ((v - min) / span) * 32;
        return `${x.toFixed(1)},${y.toFixed(1)}`;
    }).join(' ');

    spark.setAttribute('points', points);
}

function setOnlineStatus(isOnline) {
    const b = $('serverBadge');
    if (!b) return;
    b.innerText = isOnline ? 'backend online' : 'backend offline';
    b.className = 'text-[10px] font-mono px-2 py-0.5 rounded border ' +
        (isOnline ? 'border-emerald-500/40 text-emerald-300 bg-emerald-500/10' : 'border-rose-500/40 text-rose-300 bg-rose-500/10');
}

// Start auto-refresh polling loop
detectApi().then(() => {
    refreshDashboard();
    setInterval(refreshDashboard, 2000);
});