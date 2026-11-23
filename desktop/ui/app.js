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
  const $ = id => document.getElementById(id);
  const STORE_KEY = 'lionbox.profiles';
  const LOG_KEY = 'lionbox.activity';
  let desktopState = 'off';
  let currentPage = 'dashboard';
  let selectedRaw = null;

  function renderIcons(root = document) {
    root.querySelectorAll('[data-icon]').forEach(el => {
      const path = ICONS[el.dataset.icon];
      if (path) el.innerHTML = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="' + path + '"/></svg>';
    });
  }

  function toast(msg) {
    const t = $('toast');
    t.textContent = msg;
    t.hidden = false;
    document.getElementById('main').classList.add('toast-shown');
    clearTimeout(t._timer);
    t._timer = setTimeout(() => {
      t.hidden = true;
      document.getElementById('main').classList.remove('toast-shown');
    }, 4000);
  }

  function readStore(key) {
    try {
      const value = JSON.parse(localStorage.getItem(key) || '[]');
      if (!Array.isArray(value)) throw new Error('Invalid storage');
      return value;
    } catch (e) {
      toast('သိမ်းထားသော data ကို မဖတ်နိုင်ပါ။ Browser storage ကို စစ်ပါ။');
      return [];
    }
  }

  let profiles = readStore(STORE_KEY).filter(p => p && typeof p.raw === 'string' && typeof p.name === 'string' && typeof p.address === 'string' && typeof p.security === 'string');
  let logs = readStore(LOG_KEY).filter(l => l && typeof l.text === 'string' && typeof l.at === 'string');

  function store(key, value) {
    localStorage.setItem(key, JSON.stringify(value));
  }

  function addLog(text) {
    logs.unshift({ text, at: new Date().toISOString() });
    logs = logs.slice(0, 100);
    try { store(LOG_KEY, logs); } catch (e) { toast('မှတ်တမ်းကို မသိမ်းနိုင်ပါ။ Browser storage ကို စစ်ပါ။'); }
    renderActivity();
  }

  function decodeBase64(value) {
    const normalized = value.replace(/-/g, '+').replace(/_/g, '/');
    return new TextDecoder('utf-8', { fatal: true }).decode(Uint8Array.from(atob(normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=')), c => c.charCodeAt(0)));
  }

  function parseProfile(raw) {
    const line = raw.trim();
    try {
      if (line.startsWith('{')) {
        const config = JSON.parse(line);
        if (typeof config.server !== 'string' || !config.server.trim() || !Number.isInteger(Number(config.server_port)) || Number(config.server_port) < 1 || Number(config.server_port) > 65535 || typeof config.method !== 'string' || !config.method || typeof config.password !== 'string' || !config.password) return null;
        return { name: typeof config.remarks === 'string' && config.remarks ? config.remarks : 'Shadowsocks server', address: config.server, port: String(config.server_port), security: config.method, protocol: 'Shadowsocks', sni: '', raw: line };
      }
      if (line.startsWith('vless://')) {
        const u = new URL(line);
        if (!u.hostname || !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(u.username)) return null;
        return { name: u.hash ? decodeURIComponent(u.hash.slice(1)) : 'Unnamed server', address: u.hostname, port: u.port || '443', uuid: u.username, security: u.searchParams.get('security') || 'tls', sni: u.searchParams.get('sni') || u.searchParams.get('host') || '', protocol: 'VLESS', raw: line };
      }
      if (line.startsWith('ss://')) {
        const hashAt = line.indexOf('#');
        const name = hashAt === -1 ? 'Shadowsocks server' : decodeURIComponent(line.slice(hashAt + 1));
        let body = (hashAt === -1 ? line.slice(5) : line.slice(5, hashAt)).split('?')[0];
        if (!body.includes('@')) body = decodeBase64(body);
        const at = body.lastIndexOf('@');
        if (at < 1) return null;
        let credentials = decodeURIComponent(body.slice(0, at));
        if (!credentials.includes(':')) credentials = decodeBase64(credentials);
        const colon = credentials.indexOf(':');
        if (colon < 1 || !credentials.slice(colon + 1)) return null;
        const u = new URL('http://' + body.slice(at + 1));
        if (!u.hostname || !u.port || u.pathname !== '/') return null;
        return { name, address: u.hostname, port: u.port, security: credentials.slice(0, colon), sni: '', protocol: 'Shadowsocks', raw: line };
      }
    } catch (e) { return null; }
    return null;
  }

  function escapeHtml(s) {
    return String(s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  }

  function displayName(p) {
    return [p.address, p.sni].filter(Boolean).reduce((text, address) => text.split(address).join('******'), p.name);
  }

  function selectedProfile() {
    return profiles.find(p => p.raw === selectedRaw) || profiles[0];
  }

  function renderStatus() {
    const p = selectedProfile();
    $('status-server').textContent = p ? displayName(p) : 'Server ရွေးပါ';
    $('status-proto').textContent = p ? p.protocol || 'VLESS' : 'VLESS';
  }

  function serverCard(p) {
    const div = document.createElement('div');
    div.className = 'server-card';
    const selected = selectedProfile() === p;
    div.dataset.selected = selected;
    div.innerHTML = '<span class="server-icon"><span data-icon="lion"></span></span>' +
      '<button class="info select-server" aria-pressed="' + selected + '"><strong>' + escapeHtml(displayName(p)) + '</strong><span class="muted">' + escapeHtml(p.protocol || 'VLESS') + ' · ****** · ' + escapeHtml(p.security.toUpperCase()) + '</span></button>' +
      '<button class="del" aria-label="Server ဖျက်မည်"><span data-icon="trash"></span></button>';
    div.querySelector('.select-server').addEventListener('click', () => { selectedRaw = p.raw; renderAll(); });
    div.querySelector('.del').addEventListener('click', () => removeProfile(p));
    renderIcons(div);
    return div;
  }

  function showPage(page) {
    currentPage = page;
    $('import-fab').hidden = page !== 'servers';
    document.querySelectorAll('.page').forEach(el => { el.hidden = el.id !== 'page-' + page; });
    document.querySelectorAll('.nav-item').forEach(b => {
      b.classList.toggle('active', b.dataset.page === page);
      if (b.dataset.page === page) b.setAttribute('aria-current', 'page');
      else b.removeAttribute('aria-current');
    });
    renderAll();
  }

  function renderList(wrap, list, searching = false) {
    wrap.innerHTML = '';
    if (!list.length) {
      const empty = document.createElement('p');
      empty.className = 'empty';
      empty.textContent = searching ? 'ရှာမတွေ့ပါ။' : 'Server မရှိသေးပါ။ Key / config ထည့်ပါ။';
      wrap.appendChild(empty);
      if (!searching) {
        const button = document.createElement('button');
        button.className = 'btn ghost';
        button.textContent = 'Key / config ထည့်မည်';
        button.addEventListener('click', openImport);
        wrap.appendChild(button);
      }
      return;
    }
    list.forEach(p => wrap.appendChild(serverCard(p)));
  }

  function renderAll() {
    $('nav-count').textContent = profiles.length;
    $('server-count').textContent = profiles.length;
    renderStatus();
    renderList($('dashboard-servers'), profiles.slice(0, 3));
    const q = $('search').value.toLowerCase();
    renderList($('all-servers'), profiles.filter(p => !q || p.name.toLowerCase().includes(q) || p.address.toLowerCase().includes(q)), Boolean(q));
  }

  function removeProfile(p) {
    if ($('confirm-delete').checked && !confirm('“' + displayName(p) + '” ကို ဖျက်မလား?')) return;
    const next = profiles.filter(item => item !== p);
    try { store(STORE_KEY, next); } catch (e) { toast('Server ကို မဖျက်နိုင်ပါ။ Browser storage ကို စစ်ပါ။'); return; }
    profiles = next;
    addLog('Removed server “' + displayName(p) + '”');
    renderAll();
    toast('Server ဖျက်ပြီးပါပြီ');
  }

  function renderActivity() {
    const wrap = $('activity-list');
    wrap.innerHTML = '';
    if (!logs.length) { wrap.innerHTML = '<p class="empty">မှတ်တမ်း မရှိသေးပါ။</p>'; return; }
    logs.forEach(l => {
      const row = document.createElement('div');
      row.className = 'log-row';
      row.innerHTML = '<span data-icon="activity"></span><div><span>' + escapeHtml(l.text) + '</span><small>' + escapeHtml(new Date(l.at).toLocaleString()) + '</small></div>';
      renderIcons(row);
      wrap.appendChild(row);
    });
  }

  function openImport() {
    $('import-error').textContent = '';
    $('vless-input').removeAttribute('aria-invalid');
    $('import-dialog').showModal();
    $('vless-input').focus();
  }

  $('import-fab').addEventListener('click', openImport);
  document.querySelectorAll('.close-dialog').forEach(b => b.addEventListener('click', () => $('import-dialog').close()));
  $('import-form').addEventListener('submit', async e => {
    e.preventDefault();
    const button = $('import-submit');
    button.disabled = true;
    button.textContent = 'သိမ်းနေပါသည်…';
    $('import-form').setAttribute('aria-busy', 'true');
    await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)));
    try {
      const raw = $('vless-input').value.trim();
      const parsed = (raw.startsWith('{') ? [raw] : raw.split('\n').map(l => l.trim()).filter(Boolean)).map(parseProfile);
      if (!parsed.length || parsed.some(p => !p)) throw new Error('VLESS / Shadowsocks config မမှန်ပါ။ Key နဲ့ server အချက်အလက်ကို စစ်ပါ။');
      const next = [...profiles];
      parsed.forEach(p => { if (!next.some(x => x.raw === p.raw)) next.push(p); });
      const added = next.length - profiles.length;
      try { store(STORE_KEY, next); } catch (e) { throw new Error('Config ကို မသိမ်းနိုင်ပါ။ Browser storage ကို စစ်ပါ။'); }
      profiles = next;
      addLog('Imported ' + added + ' server' + (added === 1 ? '' : 's'));
      $('import-form').reset();
      $('import-dialog').close();
      renderAll();
      toast(added ? added + ' server ထည့်ပြီးပါပြီ' : 'ဤ config ကို ထည့်ထားပြီးပါပြီ');
    } catch (e) {
      $('import-error').textContent = e.message;
      $('vless-input').setAttribute('aria-invalid', 'true');
      $('vless-input').focus();
    } finally {
      button.disabled = false;
      button.textContent = 'Import';
      $('import-form').removeAttribute('aria-busy');
    }
  });

  $('connect-button').addEventListener('click', async () => {
    if (!profiles.length) { openImport(); return; }
    const desktop = window.lionbox;
    const p = selectedProfile();
    if (!desktop) {
      $('status-label').textContent = 'Android app လိုအပ်ပါသည်';
      $('status-latency').textContent = 'Latency — (Android app တွင် စစ်ပါ)';
      toast('VPN ချိတ်ဆက်ရန် LionBox APK ကို Android ဖုန်းတွင် အသုံးပြုပါ။');
      return;
    }
    const button = $('connect-button');
    try {
      if (desktopState === 'connected' || desktopState === 'connecting') {
        await desktop.disconnect();
      } else {
        button.disabled = true;
        $('status-label').textContent = 'ချိတ်ဆက်နေသည်…';
        await desktop.connect(p);
      }
    } catch (e) {
      toast('ချိတ်ဆက်မှု မအောင်မြင်ပါ: ' + (e.message || e));
      $('status-label').textContent = 'ချိတ်ဆက်မထားပါ';
    } finally {
      button.disabled = false;
    }
  });

  function renderDesktopStatus(s) {
    desktopState = s.state;
    const label = $('status-label');
    const latency = $('status-latency');
    if (s.state === 'connected') {
      label.textContent = 'ချိတ်ဆက်ထားသည် — ' + (s.server || '');
      latency.textContent = 'SOCKS 127.0.0.1:' + s.socksPort + ' · system proxy ဖွင့်ထားသည်';
    } else if (s.state === 'connecting') {
      label.textContent = 'ချိတ်ဆက်နေသည်…';
      latency.textContent = '—';
    } else {
      label.textContent = s.error ? 'ချိတ်ဆက်မထားပါ (' + s.error + ')' : 'ချိတ်ဆက်မထားပါ';
      latency.textContent = '—';
    }
  }

  if (window.lionbox) {
    window.lionbox.onState(renderDesktopStatus);
    window.lionbox.status().then(renderDesktopStatus).catch(() => {});
  }
  document.querySelectorAll('.nav-item').forEach(b => b.addEventListener('click', () => showPage(b.dataset.page)));
  $('view-all').addEventListener('click', () => showPage('servers'));
  $('search').addEventListener('input', renderAll);
  $('clear-log').addEventListener('click', () => {
    try { store(LOG_KEY, []); } catch (e) { toast('မှတ်တမ်းကို မဖျက်နိုင်ပါ။ Browser storage ကို စစ်ပါ။'); return; }
    logs = [];
    renderActivity();
  });
  $('export-all').addEventListener('click', () => {
    if (!profiles.length) { toast('Export လုပ်ရန် profile မရှိပါ'); return; }
    const url = URL.createObjectURL(new Blob([profiles.map(p => p.raw).join('\n')], { type: 'text/plain' }));
    const a = document.createElement('a');
    a.href = url;
    a.download = 'lionbox-keys.txt';
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  });

  renderIcons();
  renderActivity();
  showPage('dashboard');
})();
