// === מאגר השחקנים · search + render ===

(function () {
  'use strict';

  var year = document.getElementById('year');
  if (year) year.textContent = new Date().getFullYear();

  var qInput = document.getElementById('q');
  var resultsEl = document.getElementById('results');
  var clearBtn = document.getElementById('clear-btn');
  var profileEl = document.getElementById('profile');
  var chips = document.querySelectorAll('.chip-pop[data-name]');

  var PLAYERS = window.PLAYERS || [];

  // --- normalize for fuzzy search ---
  function norm(s) {
    return (s || '')
      .toLowerCase()
      .replace(/["'״׳`’]/g, '')
      .replace(/\s+/g, ' ')
      .trim();
  }

  function score(player, q) {
    var n = norm(q);
    if (!n) return 0;
    var hay = [player.name].concat(player.aliases || []).map(norm);
    var best = 0;
    for (var i = 0; i < hay.length; i++) {
      var h = hay[i];
      if (h === n) { best = Math.max(best, 100); continue; }
      if (h.indexOf(n) === 0) { best = Math.max(best, 80); continue; }
      if (h.indexOf(n) !== -1) { best = Math.max(best, 60); continue; }
      var tokens = n.split(' ');
      var allFound = tokens.every(function (t) { return h.indexOf(t) !== -1; });
      if (allFound) best = Math.max(best, 50);
    }
    return best;
  }

  function search(q) {
    if (!q || !q.trim()) return [];
    return PLAYERS
      .map(function (p) { return { p: p, s: score(p, q) }; })
      .filter(function (x) { return x.s > 0; })
      .sort(function (a, b) { return b.s - a.s; })
      .slice(0, 8)
      .map(function (x) { return x.p; });
  }

  // --- search results dropdown ---
  function renderResults(list, q) {
    resultsEl.innerHTML = '';
    if (!list.length) {
      if (q && q.trim()) {
        var li = document.createElement('li');
        li.className = 'result result--empty';
        li.innerHTML = '<div class="result__name">לא נמצא במאגר</div>' +
          '<div class="result__hint">השחקן עוד לא נוסף. נסה שם אחר או בקש להוסיף.</div>';
        resultsEl.appendChild(li);
        resultsEl.hidden = false;
      } else {
        resultsEl.hidden = true;
      }
      return;
    }
    list.forEach(function (p) {
      var li = document.createElement('li');
      li.className = 'result';
      li.tabIndex = 0;
      var sub = [p.position, p.onLoanTo ? (p.club + ' (בהשאלה ל' + p.onLoanTo + ')') : p.club].filter(Boolean).join(' · ');
      li.innerHTML =
        '<div class="result__main">' +
          '<div class="result__name">' + escapeHtml(p.name) + '</div>' +
          '<div class="result__sub">' + escapeHtml(sub) + '</div>' +
        '</div>' +
        '<div class="result__age">' + (p.birthDate ? ageFrom(p.birthDate) : '') + '</div>';
      li.addEventListener('click', function () { selectPlayer(p); });
      li.addEventListener('keydown', function (e) {
        if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); selectPlayer(p); }
      });
      resultsEl.appendChild(li);
    });
    resultsEl.hidden = false;
  }

  function selectPlayer(p) {
    qInput.value = p.name;
    resultsEl.hidden = true;
    renderProfile(p);
    setTimeout(function () {
      profileEl.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }, 60);
  }

  // --- utilities ---
  function escapeHtml(s) {
    return String(s == null ? '' : s)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  function ageFrom(iso) {
    if (!iso) return '';
    var d = new Date(iso);
    if (isNaN(d.getTime())) return '';
    var now = new Date();
    var age = now.getFullYear() - d.getFullYear();
    if (now.getMonth() < d.getMonth() || (now.getMonth() === d.getMonth() && now.getDate() < d.getDate())) age--;
    return age;
  }

  function fmtDateHe(iso) {
    if (!iso) return '';
    var d = new Date(iso);
    if (isNaN(d.getTime())) return iso;
    var months = ['בינואר','בפברואר','במרץ','באפריל','במאי','ביוני','ביולי','באוגוסט','בספטמבר','באוקטובר','בנובמבר','בדצמבר'];
    return d.getDate() + ' ' + months[d.getMonth()] + ' ' + d.getFullYear();
  }

  function shortDate(iso) {
    if (!iso) return '';
    var d = new Date(iso);
    if (isNaN(d.getTime())) return iso;
    return d.getDate() + '.' + (d.getMonth() + 1) + '.' + d.getFullYear();
  }

  // --- profile render ---
  function renderProfile(p) {
    var jersey = p.jerseyNo != null ? p.jerseyNo : (p.jerseyAtParent != null ? p.jerseyAtParent : '');
    var age = p.birthDate ? ageFrom(p.birthDate) : '';

    var html = '';

    // Hero header
    html += '<section class="p-hero">';
    html += '  <div class="p-hero__inner">';
    if (jersey !== '') html += '    <div class="p-hero__no" aria-hidden="true">' + escapeHtml(jersey) + '</div>';
    html += '    <div class="p-hero__body">';
    html += '      <span class="p-kicker">כרטיס שחקן</span>';
    html += '      <h2 class="p-name">' + escapeHtml(p.name) + '</h2>';
    var subParts = [p.position, p.club];
    if (p.onLoanTo) subParts.push('בהשאלה ל' + p.onLoanTo);
    html += '      <p class="p-sub">' + escapeHtml(subParts.filter(Boolean).join(' · ')) + '</p>';
    html += '      <div class="p-chips">';
    if (p.birthDate) html += '        <span class="chip"><b>' + shortDate(p.birthDate) + '</b>תאריך לידה</span>';
    if (age !== '')  html += '        <span class="chip"><b>' + age + '</b>גיל</span>';
    if (jersey !== '') html += '        <span class="chip"><b>' + escapeHtml(jersey) + '</b>חולצה</span>';
    if (p.foot)      html += '        <span class="chip"><b>' + escapeHtml(p.foot) + '</b>רגל</span>';
    if (p.height)    html += '        <span class="chip"><b>' + p.height + ' ס״מ</b>גובה</span>';
    if (p.nationality) html += '      <span class="chip"><b>' + escapeHtml(p.nationality) + '</b>אזרחות</span>';
    html += '      </div>';
    html += '    </div>';
    html += '  </div>';
    html += '</section>';

    // Two-column: bio + vcard
    html += '<section class="section p-section">';
    html += '  <div class="container">';
    html += '    <div class="p-grid">';

    // Bio
    html += '      <article class="prose">';
    html += '        <span class="eyebrow">סיפור</span>';
    html += '        <h3>הקריירה במבט מהיר</h3>';
    if (p.bio && p.bio.length) {
      p.bio.forEach(function (para, i) {
        html += '<p' + (i === 0 ? ' class="lede"' : '') + '>' + escapeHtml(para) + '</p>';
      });
    } else {
      html += '<p class="lede">אין עדיין ביוגרפיה מפורטת עבור ' + escapeHtml(p.name) + ' במאגר.</p>';
      html += '<p>הקלף הזה ימשיך להתעדכן ככל שיתווספו פרטים. בינתיים — הסטטיסטיקה והפרטים הבסיסיים בכרטיס מימין.</p>';
    }
    html += '      </article>';

    // VCard
    html += '      <aside class="vcard">';
    html += '        <div class="vcard__head">';
    html += '          <span class="vcard__no">' + (jersey !== '' ? '#' + escapeHtml(jersey) : '—') + '</span>';
    html += '          <span class="vcard__title">' + escapeHtml(p.position || '') + '</span>';
    html += '        </div>';
    html += '        <dl class="vcard__list">';
    html += '          <div><dt>שם מלא</dt><dd>' + escapeHtml(p.name) + '</dd></div>';
    if (p.nationality) html += '<div><dt>אזרחות</dt><dd>' + escapeHtml(p.nationality) + '</dd></div>';
    if (p.birthDate) html += '<div><dt>תאריך לידה</dt><dd>' + escapeHtml(fmtDateHe(p.birthDate)) + '</dd></div>';
    if (p.position) html += '<div><dt>עמדה</dt><dd>' + escapeHtml(p.position) + '</dd></div>';
    if (p.foot) html += '<div><dt>רגל חזקה</dt><dd>' + escapeHtml(p.foot) + '</dd></div>';
    if (p.height) html += '<div><dt>גובה</dt><dd>' + p.height + ' ס״מ</dd></div>';
    if (p.club) html += '<div><dt>מועדון</dt><dd>' + escapeHtml(p.club) + '</dd></div>';
    if (p.onLoanTo) html += '<div><dt>בהשאלה ב־</dt><dd>' + escapeHtml(p.onLoanTo) + '</dd></div>';
    if (p.jerseyNo != null) html += '<div><dt>חולצה (נוכחית)</dt><dd>' + p.jerseyNo + '</dd></div>';
    if (p.onLoanTo && p.jerseyAtParent != null) html += '<div><dt>חולצה במועדון האם</dt><dd>' + p.jerseyAtParent + '</dd></div>';
    if (p.contractUntil) html += '<div><dt>חוזה עד</dt><dd>' + escapeHtml(fmtDateHe(p.contractUntil)) + '</dd></div>';
    html += '        </dl>';
    html += '      </aside>';

    html += '    </div>';
    html += '  </div>';
    html += '</section>';

    // Season stats
    if (p.season) {
      var s = p.season;
      html += '<section class="section p-stats">';
      html += '  <div class="container">';
      html += '    <span class="eyebrow">' + escapeHtml(s.year || 'עונה נוכחית') + (s.league ? ' · ' + escapeHtml(s.league) : '') + '</span>';
      html += '    <h3>סטטיסטיקה עונתית</h3>';
      html += '    <div class="stat-grid">';
      function statCard(num, label, accent) {
        return '<div class="stat-card' + (accent ? ' stat-card--accent' : '') + '">' +
          '<div class="stat-num">' + (num == null ? '—' : num) + '</div>' +
          '<div class="stat-label">' + label + '</div>' +
          '</div>';
      }
      html += statCard(s.appearances, 'הופעות', true);
      html += statCard(s.goals, 'שערים', false);
      html += statCard(s.assists, 'בישולים', false);
      html += statCard(s.minutes, 'דקות', false);
      html += statCard(s.yellowCards, 'צהובים', false);
      html += statCard(s.redCards, 'אדומים', false);
      html += '    </div>';
      html += '  </div>';
      html += '</section>';
    }

    // Transfers
    if (p.transfers && p.transfers.length) {
      html += '<section class="section p-transfers">';
      html += '  <div class="container">';
      html += '    <span class="eyebrow">העברות</span>';
      html += '    <h3>מסלול קריירה</h3>';
      html += '    <ol class="trf-list">';
      p.transfers.forEach(function (t) {
        var label = ({
          transfer: 'העברה',
          loan: 'השאלה',
          extension: 'הארכת חוזה',
          promotion: 'קידום פנימי',
          return: 'חזרה מהשאלה'
        })[t.type] || 'מהלך';
        var arrow = '';
        if (t.from && t.to) arrow = t.from + ' ← ' + t.to;
        else if (t.to) arrow = t.to;
        html += '<li class="trf">';
        html += '  <div class="trf__when">';
        html += '    <span class="trf__date">' + escapeHtml(shortDate(t.date)) + '</span>';
        html += '    <span class="trf__type trf__type--' + escapeHtml(t.type || '') + '">' + escapeHtml(label) + '</span>';
        html += '  </div>';
        html += '  <div class="trf__main">';
        html += '    <h4>' + escapeHtml(arrow) + '</h4>';
        if (t.note) html += '<p>' + escapeHtml(t.note) + '</p>';
        html += '  </div>';
        html += '</li>';
      });
      html += '    </ol>';
      html += '  </div>';
      html += '</section>';
    }

    // Notable matches
    if (p.notableMatches && p.notableMatches.length) {
      html += '<section class="section p-matches">';
      html += '  <div class="container">';
      html += '    <span class="eyebrow">משחקים בולטים</span>';
      html += '    <h3>רגעים שכדאי לזכור</h3>';
      html += '    <div class="match-grid">';
      p.notableMatches.forEach(function (m) {
        html += '<article class="match">';
        html += '  <header class="match__head">';
        html += '    <span class="match__comp">' + escapeHtml(m.comp || '') + '</span>';
        html += '    <span class="match__date">' + escapeHtml(shortDate(m.date)) + '</span>';
        html += '  </header>';
        html += '  <div class="match__score">';
        html += '    <div class="match__team match__team--away"><span class="match__name">' + escapeHtml(m.opponent || '') + '</span><span class="match__goals">' + (m.scoreThem != null ? m.scoreThem : '?') + '</span></div>';
        html += '    <span class="match__sep">:</span>';
        html += '    <div class="match__team"><span class="match__goals">' + (m.scoreUs != null ? m.scoreUs : '?') + '</span><span class="match__name">' + escapeHtml(m.us || p.club || '') + '</span></div>';
        html += '  </div>';
        if (m.events && m.events.length) {
          html += '<ul class="match__events">';
          m.events.forEach(function (ev) { html += '<li>' + escapeHtml(ev) + '</li>'; });
          html += '</ul>';
        }
        html += '</article>';
      });
      html += '    </div>';
      html += '  </div>';
      html += '</section>';
    }

    profileEl.innerHTML = html;
  }

  // --- bind events ---
  function debounce(fn, delay) {
    var t;
    return function () {
      var args = arguments, ctx = this;
      clearTimeout(t);
      t = setTimeout(function () { fn.apply(ctx, args); }, delay);
    };
  }

  var doSearch = debounce(function () {
    var q = qInput.value;
    renderResults(search(q), q);
  }, 120);

  qInput.addEventListener('input', doSearch);
  qInput.addEventListener('focus', function () {
    if (qInput.value.trim()) doSearch();
  });

  qInput.addEventListener('keydown', function (e) {
    if (e.key === 'Enter') {
      var matches = search(qInput.value);
      if (matches.length) selectPlayer(matches[0]);
    } else if (e.key === 'Escape') {
      resultsEl.hidden = true;
    } else if (e.key === 'ArrowDown') {
      var first = resultsEl.querySelector('.result');
      if (first) first.focus();
    }
  });

  document.addEventListener('click', function (e) {
    if (!e.target.closest('#search')) resultsEl.hidden = true;
  });

  if (clearBtn) {
    clearBtn.addEventListener('click', function () {
      qInput.value = '';
      qInput.focus();
      resultsEl.hidden = true;
    });
  }

  chips.forEach(function (chip) {
    chip.addEventListener('click', function () {
      var name = chip.dataset.name;
      qInput.value = name;
      var matches = search(name);
      if (matches.length) selectPlayer(matches[0]);
    });
  });

  // --- initial state: open Sagi by default if no #hash ---
  function openInitial() {
    var sagi = PLAYERS.find(function (p) { return p.id === 132446; });
    if (sagi) renderProfile(sagi);
  }
  openInitial();

  if (typeof console !== 'undefined' && console.log) {
    console.log('%cמאגר השחקנים', 'color:#ffd500;font:900 22px/1 "Frank Ruhl Libre",serif;padding:8px 0;');
  }
})();
