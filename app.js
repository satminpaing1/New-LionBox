/* LionBox — local-only VLESS profile manager (v2rayNG-style mobile client) */
(function () {
  'use strict';

  const ICONS = {
    plus: 'M12 5v14M5 12h14',
    download: 'M12 3v12m0 0l-4-4m4 4l4-4M5 21h14',
    x: 'M6 6l12 12M18 6L6 18',
    search: 'M11 4a7 7 0 1 1 0 14 7 7 0 0 1 0-14zm5 12l4 4',
    power: 'M12 3v9M6.3 6.3a8 8 0 1 0 11.4 0',
    activity: 'M3 12h4l3-8 4 16 3-8h4',
    settings: 'M12 8a4 4 0 1 1 0 8 4 4 0 0 1 0-8zm0-5v3m0 12v3M3 12h3m12 0h3M5.6 5.6l2.1 2.1m8.6 8.6l2.1 2.1M18.4 5.6l-2.1 2.1M7.7 16.3l-2.1 2.1',
    server: 'M4 4h16v6H4zM4 14h16v6H4zM8 7h.01M8 17h.01',
    home: 'M3 11l9-8 9 8M5 10v10h5v-6h4v6h5V10',
    clock: 'M12 3a9 9 0 1 1 0 18 9 9 0 0 1 0-18zm0 4v5l3 2',
    trash: 'M4 7h16M9 7V5h6v2m-8 0l1 13h8l1-13',
    lion: 'M12 3c3.5 0 6 2.4 6 5.5 1.3.8 2 2.1 2 3.5 0 1.2-.5 2.3-1.4 3.1L17 21H7l-1.6-5.9A4.4 4.4 0 0 1 4 12c0-1.4.7-2.7 2-3.5C6 5.4 8.5 3 12 3zm-2 8.5h.01M14 11.5h.01M9.5 15.5c.7.7 1.6 1 2.5 1s1.8-.3 2.5-1'
  };

  function renderIcons(root) {
    (root || document).querySelectorAll('[data-icon]').forEach(el => {
      const path = ICONS[el.dataset.icon];
      if (!path) return;
      el.innerHTML = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="' + path + '"/></svg>';
    });
  }

  /* ---- state ---- */
  const STORE_KEY = 'lionbox.profiles';
  const LOG_KEY = 'lionbox.activity';
  let profiles = [];
  try { profiles = JSON.parse(localStorage.getItem(STORE_KEY) || '[]'); } catch (e) { profiles = []; }
  let logs = [];
  try { logs = JSON.parse(localStorage.getItem(LOG_KEY) || '[]'); } catch (e) { logs = []; }

  function saveProfiles() { localStorage.setItem(STORE_KEY, JSON.stringify(profiles)); }
  function saveLogs() { localStorage.setItem(LOG_KEY, JSON.stringify(logs.slice(0, 100))); }

  function addLog(text) {
    logs.unshift({ text, at: new Date().toISOString() });
    saveLogs();
    renderActivity();
  }

  /* ---- helpers ---- */
  const $ = id => document.getElementById(id);

  function toast(msg) {
    const t = $('toast');
    t.textContent = msg;
    t.hidden = false;
    clearTimeout(t._timer);
    t._timer = setTimeout(() => { t.hidden = true; }, 2600);
  }

  function parseVLESS(raw) {
    const line = raw.trim();
    if (!line.startsWith('vless://')) return null;
    try {
      const hashAt = line.indexOf('#');
      const base = hashAt === -1 ? line : line.slice(0, hashAt);
      const frag = hashAt === -1 ? '' : line.slice(hashAt + 1);
      const name = frag ? decodeURIComponent(frag) : 'Unnamed server';
      const u = new URL(base);
      const security = u.searchParams.get('security') || 'tls';
      const sni = u.searchParams.get('sni') || u.searchParams.get('host') || '';
      return { name, address: u.hostname, port: u.port || '443', uuid: u.username, security, sni, raw: line };
    } catch (e) { return null; }
  }

  function serverCard(p, i) {
    const div = document.createElement('div');
    div.className = 'server-card';
    const bits = ['******', p.security.toUpperCase()];
    if (p.sni) bits.push('SNI ******');
    const name = [p.address, p.sni].filter(Boolean).reduce((text, address) => text.split(address).join('******'), p.name);
    div.innerHTML = '<span class="server-icon"><span data-icon="lion"></span></span>' +
      '<div class="info"><strong>' + escapeHtml(name) + '</strong><span class="muted">' + bits.map(escapeHtml).join(' · ') + '</span></div>' +
      '<button class="del" data-del="' + i + '" aria-label="Server ဖျက်မည်"><span data-icon="trash"></span></button>';
    renderIcons(div);
    return div;
  }

  function escapeHtml(s) {
    return String(s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  }

  /* ---- pages ---- */
  let currentPage = 'dashboard';

  function showPage(page) {
    currentPage = page;
    $('import-fab').hidden = page !== 'servers';
    document.querySelectorAll('.page').forEach(el => { el.hidden = el.id !== 'page-' + page; });
    document.querySelectorAll('.bottom-nav .nav-item').forEach(b =>
      b.classList.toggle('active', b.dataset.page === page));
    if (page === 'servers') renderAllServers();
    if (page === 'logs') renderActivity();
  }

  document.querySelectorAll('.bottom-nav .nav-item').forEach(b =>
    b.addEventListener('click', () => showPage(b.dataset.page)));

  /* ---- rendering ---- */
  function renderCounts() {
    const n = profiles.length;
    $('nav-count').textContent = n;
    $('server-count').textContent = n;
  }

  function renderDashboardServers() {
    const wrap = $('dashboard-servers');
    wrap.innerHTML = '';
    if (!profiles.length) {
      wrap.innerHTML = '<p class="empty">Server မရှိသေးပါ။ VLESS key ထည့်ပါ။</p>';
      return;
    }
    profiles.slice(0, 3).forEach((p, i) => wrap.appendChild(serverCard(p, i)));
    bindDelete(wrap);
  }

  function renderAllServers() {
    const wrap = $('all-servers');
    wrap.innerHTML = '';
    const q = ($('search').value || '').toLowerCase();
    const list = profiles.filter(p =>
      !q || p.name.toLowerCase().includes(q) || p.address.toLowerCase().includes(q));
    if (!list.length) {
      wrap.innerHTML = '<p class="empty">' + (q ? 'ရှာမတွေ့ပါ။' : 'Server မရှိသေးပါ။ VLESS key ထည့်ပါ။') + '</p>';
      renderCounts();
      return;
    }
    list.forEach(p => wrap.appendChild(serverCard(p, profiles.indexOf(p))));
    bindDelete(wrap);
    renderCounts();
  }

  function bindDelete(wrap) {
    wrap.querySelectorAll('[data-del]').forEach(btn =>
      btn.addEventListener('click', () => removeProfile(Number(btn.dataset.del))));
  }

  function removeProfile(i) {
    const p = profiles[i];
    if (!p) return;
    if ($('confirm-delete').checked && !confirm('“' + p.name + '” ကို ဖျက်မလား?')) return;
    profiles.splice(i, 1);
    saveProfiles();
    addLog('Removed server “' + p.name + '”');
    renderCounts();
    renderDashboardServers();
    if (currentPage === 'servers') renderAllServers();
    toast('Server ဖျက်ပြီးပါပြီ');
  }

  function renderActivity() {
    const wrap = $('activity-list');
    if (!wrap) return;
    wrap.innerHTML = '';
    if (!logs.length) {
      wrap.innerHTML = '<p class="empty">မှတ်တမ်း မရှိသေးပါ။</p>';
      return;
    }
    logs.forEach(l => {
      const row = document.createElement('div');
      row.className = 'log-row';
      const d = new Date(l.at);
      row.innerHTML = '<span data-icon="activity"></span><div><span>' + escapeHtml(l.text) + '</span><small>' + d.toLocaleString() + '</small></div>';
      renderIcons(row);
      wrap.appendChild(row);
    });
  }

  /* ---- import dialog ---- */
  $('import-fab').addEventListener('click', () => {
    $('import-error').textContent = '';
    $('import-dialog').showModal();
  });

  document.querySelectorAll('.close-dialog').forEach(b =>
    b.addEventListener('click', () => $('import-dialog').close()));

  $('import-form').addEventListener('submit', e => {
    e.preventDefault();
    const raw = $('vless-input').value;
    const parsed = raw.split('\n').map(l => l.trim()).filter(Boolean).map(parseVLESS);
    if (!parsed.length || parsed.some(p => !p)) {
      $('import-error').textContent = 'VLESS key မမှန်ပါ။ vless:// ဖြင့် စတင်ရမည်။';
      return;
    }
    let added = 0;
    parsed.forEach(p => {
      if (!profiles.some(x => x.raw === p.raw)) { profiles.push(p); added++; }
    });
    saveProfiles();
    addLog('Imported ' + added + ' server' + (added === 1 ? '' : 's'));
    $('import-form').reset();
    $('import-dialog').close();
    renderCounts();
    renderDashboardServers();
    toast(added + ' server ထည့်ပြီးပါပြီ');
  });

  /* ---- connection ---- */
  let connected = false;
  $('connect-button').addEventListener('click', () => {
    if (!profiles.length) { toast('အရင်ဆုံး VLESS key ထည့်ပါ'); $('import-dialog').showModal(); return; }
    connected = !connected;
    const card = document.querySelector('.status-card');
    card.dataset.state = connected ? 'on' : 'off';
    $('status-label').textContent = connected ? 'ချိတ်ဆက်ထားပါသည်' : 'မချိတ်ရသေးပါ';
    $('connect-button').setAttribute('aria-label', connected ? 'VPN ပိတ်မည်' : 'VPN ချိတ်ဆက်မည်');
    const p = profiles[0];
    $('status-server').textContent = p ? [p.address, p.sni].filter(Boolean).reduce((text, address) => text.split(address).join('******'), p.name) : 'Server ရွေးပါ';
    $('status-latency').textContent = connected ? 'Latency — (native app တွင် တိုင်းမည်)' : 'Latency —';
    $('stat-traffic').textContent = connected ? '—' : '—';
    $('stat-time').textContent = connected ? '၀၀:၀၀' : '—';
    addLog(connected ? 'Connection started (demo)' : 'Connection stopped');
    toast(connected ? 'ချိတ်ဆက်ပြီးပါပြီ' : 'ပိတ်လိုက်ပါပြီ');
  });

  $('view-all').addEventListener('click', () => showPage('servers'));
  $('search').addEventListener('input', renderAllServers);

  $('clear-log').addEventListener('click', () => {
    logs = [];
    saveLogs();
    renderActivity();
  });

  $('export-all').addEventListener('click', () => {
    if (!profiles.length) { toast('Export လုပ်ရန် profile မရှိပါ'); return; }
    const text = profiles.map(p => p.raw).join('\n');
    const url = URL.createObjectURL(new Blob([text], { type: 'text/plain' }));
    const a = document.createElement('a');
    a.href = url;
    a.download = 'lionbox-keys.txt';
    a.click();
    URL.revokeObjectURL(url);
  });

  ['download-apk', 'download-banner', 'download-apk-settings'].forEach(id => {
    const el = $(id);
    if (el) el.addEventListener('click', () => addLog('Downloaded the LionBox APK'));
  });

  /* ---- init ---- */
  renderIcons(document);
  renderCounts();
  renderDashboardServers();
  renderActivity();
  showPage('dashboard');
})();
