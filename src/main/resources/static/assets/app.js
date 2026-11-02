/* SeatLock front end: shared helpers for every page (no build step, no external scripts). */
(function () {
  const store = {
    get(key, fallback) { try { return JSON.parse(localStorage.getItem(key)) ?? fallback; } catch { return fallback; } },
    set(key, value) { try { value === null ? localStorage.removeItem(key) : localStorage.setItem(key, JSON.stringify(value)); } catch { /* private mode */ } },
  };

  // ---------------------------------------------------------------- auth
  const auth = {
    get() {
      const a = store.get('seatlock.auth', null);
      return a && new Date(a.expiresAt) > new Date() && a.user && !a.user.guest ? a : null;
    },
    set(value) { store.set('seatlock.auth', value); },
    clear() { store.set('seatlock.auth', null); },
    isAdmin() { const a = auth.get(); return !!a && (a.user.roles || []).includes('ADMIN'); },
    /** Sends anyone who isn't logged in to the login page, and back here afterwards. */
    require() {
      if (auth.get()) return auth.get();
      location.replace('/login.html?next=' + encodeURIComponent(location.pathname + location.search));
      return null;
    },
  };

  /** Only same-site paths are allowed as a "next" target (no open redirects). */
  function safeNext(value) {
    return value && value.startsWith('/') && !value.startsWith('//') && !value.startsWith('/\\') ? value : '/';
  }

  // ---------------------------------------------------------------- API
  async function api(method, path, body, opts = {}) {
    const headers = { ...(opts.headers || {}) };
    if (body !== undefined && body !== null) headers['Content-Type'] = 'application/json';
    const a = auth.get();
    if (a && opts.auth !== false) headers.Authorization = 'Bearer ' + a.token;
    let res;
    try {
      res = await fetch(path, { method, headers, body: body == null ? undefined : JSON.stringify(body) });
    } catch {
      return { ok: false, status: 0, data: { detail: 'Can’t reach SeatLock right now. Check your connection and try again.' } };
    }
    let data = null;
    try { data = await res.json(); } catch { /* empty body */ }
    if (res.status === 401 && a && opts.auth !== false) {
      auth.clear();
      location.replace('/login.html?expired=1&next=' + encodeURIComponent(location.pathname + location.search));
    }
    return { ok: res.ok, status: res.status, data };
  }
  const problem = (res) => (res.data && (res.data.detail || res.data.title)) || 'Something went wrong (HTTP ' + res.status + ')';

  // ---------------------------------------------------------------- formatting
  const inr = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 });
  const TZ = { timeZone: 'Asia/Kolkata' };   // venues are in India: show their local time to everyone
  const fmt = {
    money: (cents) => inr.format(Math.round((cents || 0) / 100)),
    date: (iso) => new Date(iso).toLocaleDateString('en-IN', { ...TZ, weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' }),
    time: (iso) => new Date(iso).toLocaleTimeString('en-IN', { ...TZ, hour: 'numeric', minute: '2-digit' }),
    when: (iso) => fmt.date(iso) + ' · ' + fmt.time(iso),
    day: (iso) => new Date(iso).toLocaleDateString('en-IN', { ...TZ, day: '2-digit' }),
    month: (iso) => new Date(iso).toLocaleDateString('en-IN', { ...TZ, month: 'short' }).toUpperCase(),
    seat: (s) => `${s.section} ${s.rowLabel}${s.seatNumber}`,
    initials: (name) => (name || '?').split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0].toUpperCase()).join(''),
    countdown: (ms) => { const t = Math.max(0, Math.floor(ms / 1000)); return `${Math.floor(t / 60)}:${String(t % 60).padStart(2, '0')}`; },
  };

  // ---------------------------------------------------------------- tiny DOM helper
  function h(tag, attrs = {}, ...children) {
    const el = document.createElement(tag);
    for (const [k, v] of Object.entries(attrs || {})) {
      if (v === false || v == null) continue;
      if (k === 'class') el.className = v;
      else if (k === 'text') el.textContent = v;
      else if (k === 'html') el.innerHTML = v;   // only ever used with constant markup (icons)
      else if (k.startsWith('on')) el.addEventListener(k.slice(2), v);
      else if (k === 'style' && typeof v === 'object') Object.assign(el.style, v);
      else el.setAttribute(k, v === true ? '' : v);
    }
    for (const c of children.flat()) if (c != null && c !== false) el.append(c.nodeType ? c : document.createTextNode(String(c)));
    return el;
  }

  // ---------------------------------------------------------------- icons (inline SVG, constant markup)
  const icons = {
    logo: '<svg viewBox="0 0 32 32" fill="none" aria-hidden="true"><rect width="32" height="32" rx="9" fill="#3b2a8c"/><path d="M8 11.5a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v1.6a2.4 2.4 0 0 0 0 4.8v1.6a2 2 0 0 1-2 2H10a2 2 0 0 1-2-2v-1.6a2.4 2.4 0 0 0 0-4.8z" fill="#e8b04b"/><path d="M14 13.6v-1a2 2 0 0 1 4 0v1" stroke="#3b2a8c" stroke-width="1.6" stroke-linecap="round"/><rect x="13" y="13.6" width="6" height="5" rx="1.2" fill="#3b2a8c"/></svg>',
    pin: '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M12 21s7-5.6 7-11a7 7 0 1 0-14 0c0 5.4 7 11 7 11z"/><circle cx="12" cy="10" r="2.5"/></svg>',
    cal: '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><rect x="3" y="5" width="18" height="16" rx="2"/><path d="M3 10h18M8 3v4M16 3v4"/></svg>',
    search: '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/></svg>',
    check: '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="3" aria-hidden="true"><path d="m5 12 5 5L20 7"/></svg>',
    clock: '<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></svg>',
    shield: '<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M12 3 4 6v6c0 5 3.5 8 8 9 4.5-1 8-4 8-9V6z"/><path d="m9 12 2 2 4-4"/></svg>',
    x: '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.5" aria-hidden="true"><path d="M6 6l12 12M18 6 6 18"/></svg>',
  };
  const art = {
    CONCERT: '<svg viewBox="0 0 100 100" fill="#fff" aria-hidden="true"><path d="M38 14v52a12 12 0 1 1-6-10.4V24l44-10v42a12 12 0 1 1-6-10.4V23z"/></svg>',
    COMEDY: '<svg viewBox="0 0 100 100" fill="#fff" aria-hidden="true"><circle cx="50" cy="50" r="40"/><circle cx="36" cy="40" r="6" fill="#0003"/><circle cx="64" cy="40" r="6" fill="#0003"/><path d="M28 56c6 14 38 14 44 0z" fill="#0003"/></svg>',
    SPORTS: '<svg viewBox="0 0 100 100" fill="#fff" aria-hidden="true"><circle cx="50" cy="50" r="40"/><path d="m50 30 14 10-5 17H41l-5-17z" fill="#0003"/></svg>',
    THEATRE: '<svg viewBox="0 0 100 100" fill="#fff" aria-hidden="true"><path d="M10 14h50v28c0 18-11 30-25 30S10 60 10 42z"/><path d="M44 34h46v26c0 17-10 28-23 28-8 0-15-4-19-11z" opacity=".75"/></svg>',
    OTHER: '<svg viewBox="0 0 100 100" fill="#fff" aria-hidden="true"><path d="m50 8 12 28 30 3-23 20 7 30-26-16-26 16 7-30L8 39l30-3z"/></svg>',
  };
  const palettes = {
    CONCERT: ['#5b2a9e', '#c2357e'], COMEDY: ['#d9662b', '#e8b04b'], SPORTS: ['#0f6b5a', '#2aa36b'],
    THEATRE: ['#7a1f3d', '#c2362f'], OTHER: ['#3b2a8c', '#4f7bd9'],
  };
  const categoryLabel = { CONCERT: 'Concert', COMEDY: 'Comedy', SPORTS: 'Sports', THEATRE: 'Theatre', OTHER: 'Event' };

  /** Generated poster artwork: a gradient and an icon per category. */
  function poster(event, el) {
    const [a, b] = palettes[event.category] || palettes.OTHER;
    el.classList.add('poster');
    el.style.background = `linear-gradient(135deg, ${a} 0%, ${b} 100%)`;
    el.prepend(h('div', { class: 'art', html: art[event.category] || art.OTHER }));
    return el;
  }

  // ---------------------------------------------------------------- toasts
  function toast(message, kind = '') {
    let box = document.querySelector('.toasts');
    if (!box) { box = h('div', { class: 'toasts', role: 'status', 'aria-live': 'polite' }); document.body.append(box); }
    const t = h('div', { class: 'toast ' + kind }, message);
    box.append(t);
    setTimeout(() => t.remove(), 5000);
  }

  // ---------------------------------------------------------------- header & footer
  function header(active) {
    const a = auth.get();
    const el = document.getElementById('topbar');
    if (!el) return;
    const links = [['events', '/', 'Events'], ['bookings', '/bookings.html', 'My bookings']];
    if (auth.isAdmin()) links.push(['checkin', '/checkin.html', 'Check-in']);
    const nav = h('nav', { class: 'nav', 'aria-label': 'Main' },
      links.map(([key, href, label]) => h('a', { href, class: key === active ? 'active' : '' }, label)));
    let right = null;
    if (a) {
      const drop = h('div', { class: 'dropdown', hidden: true, role: 'menu' },
        h('div', { class: 'who' }, h('b', { text: a.user.name }), h('span', { class: 'muted small', text: a.user.email || '' })),
        h('a', { href: '/bookings.html', role: 'menuitem' }, 'My bookings & tickets'),
        auth.isAdmin() ? h('a', { href: '/checkin.html', role: 'menuitem' }, 'Organizer check-in') : null,
        h('button', { role: 'menuitem', onclick: () => { auth.clear(); location.href = '/login.html'; } }, 'Log out'));
      const btn = h('button', { 'aria-haspopup': 'menu', 'aria-expanded': 'false', onclick: (e) => {
        e.stopPropagation();
        drop.hidden = !drop.hidden;
        btn.setAttribute('aria-expanded', String(!drop.hidden));
      } }, h('span', { class: 'avatar', text: fmt.initials(a.user.name) }), h('span', { class: 'name', text: a.user.name.split(' ')[0] }));
      document.addEventListener('click', () => { drop.hidden = true; btn.setAttribute('aria-expanded', 'false'); });
      right = h('div', { class: 'usermenu' }, btn, drop);
    } else {
      right = h('a', { class: 'btn btn-primary', href: '/login.html' }, 'Log in');
    }
    el.className = 'topbar';
    el.replaceChildren(h('div', { class: 'container' },
      h('a', { class: 'logo', href: '/', html: icons.logo + '<span>SeatLock</span>' }), nav, right));
  }

  function footer() {
    const el = document.getElementById('footer');
    if (!el) return;
    el.className = 'footer';
    el.replaceChildren(h('div', { class: 'container' },
      h('span', { class: 'testmode' }, h('b', { text: 'TEST MODE' }), 'Demo site: no real payments are taken.'),
      h('nav', { 'aria-label': 'Footer' },
        h('a', { href: '/demo.html' }, 'Live concurrency demo'),
        h('a', { href: 'https://github.com/HemashreeVI/SeatLock/blob/main/DESIGN.md', rel: 'noopener' }, 'How it works'),
        h('a', { href: 'https://github.com/HemashreeVI/SeatLock', rel: 'noopener' }, 'GitHub'))));
  }

  window.SeatLock = { store, auth, api, problem, fmt, h, icons, poster, categoryLabel, toast, header, footer, safeNext };
})();
