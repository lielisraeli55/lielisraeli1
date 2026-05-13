// === שגיא ישראלי · interactions ===

(function () {
  'use strict';

  // year
  var y = document.getElementById('year');
  if (y) y.textContent = new Date().getFullYear();

  // ---- Animated counters ----
  function animateCount(el) {
    var target = parseFloat(el.dataset.count);
    if (isNaN(target)) return;
    var duration = 1400;
    var start = performance.now();
    var startVal = 0;

    function tick(now) {
      var elapsed = now - start;
      var p = Math.min(elapsed / duration, 1);
      // easeOutCubic
      var eased = 1 - Math.pow(1 - p, 3);
      var current = Math.round(startVal + (target - startVal) * eased);
      el.textContent = formatNum(current);
      if (p < 1) requestAnimationFrame(tick);
      else el.textContent = formatNum(target);
    }
    requestAnimationFrame(tick);
  }

  function formatNum(n) {
    if (n >= 1000) return n.toLocaleString('he-IL');
    return String(n);
  }

  // ---- IntersectionObserver: reveal + count + fill bars ----
  var io = new IntersectionObserver(function (entries) {
    entries.forEach(function (entry) {
      if (!entry.isIntersecting) return;
      var el = entry.target;

      if (el.classList.contains('stat-num')) {
        animateCount(el);
      }
      if (el.classList.contains('bars__fill') || el.classList.contains('ab__fill')) {
        el.classList.add('is-visible');
      }
      if (el.classList.contains('reveal')) {
        el.classList.add('is-visible');
      }

      io.unobserve(el);
    });
  }, { threshold: 0.25, rootMargin: '0px 0px -40px 0px' });

  document.querySelectorAll('.stat-num').forEach(function (n) { io.observe(n); });
  document.querySelectorAll('.bars__fill, .ab__fill').forEach(function (n) { io.observe(n); });

  // Mark sections for reveal
  document.querySelectorAll('.section-head, .stat-card, .tl__item, .match, .trf, .bio__grid, .bars, .abilities').forEach(function (n) {
    n.classList.add('reveal');
    io.observe(n);
  });

  // ---- Smooth scroll with offset for sticky topbar ----
  document.querySelectorAll('a[href^="#"]').forEach(function (a) {
    a.addEventListener('click', function (e) {
      var id = a.getAttribute('href');
      if (id.length < 2) return;
      var target = document.querySelector(id);
      if (!target) return;
      e.preventDefault();
      var topbar = document.querySelector('.topbar');
      var offset = (topbar ? topbar.offsetHeight : 0) + 12;
      var y = target.getBoundingClientRect().top + window.scrollY - offset;
      window.scrollTo({ top: y, behavior: 'smooth' });
    });
  });

  // ---- Parallax on hero crest "28" ----
  var crest = document.querySelector('.hero__crest');
  if (crest) {
    window.addEventListener('scroll', function () {
      var y = window.scrollY;
      if (y > window.innerHeight) return;
      crest.style.transform = 'translateY(calc(-50% + ' + (y * 0.18) + 'px))';
    }, { passive: true });
  }

  // ---- Subtle cursor highlight on hero ----
  var hero = document.querySelector('.hero');
  if (hero && window.matchMedia('(pointer:fine)').matches) {
    hero.addEventListener('mousemove', function (e) {
      var rect = hero.getBoundingClientRect();
      var px = ((e.clientX - rect.left) / rect.width) * 100;
      var py = ((e.clientY - rect.top) / rect.height) * 100;
      hero.style.backgroundImage =
        'radial-gradient(600px circle at ' + px + '% ' + py + '%, rgba(255,213,0,.06), transparent 60%)';
    });
    hero.addEventListener('mouseleave', function () {
      hero.style.backgroundImage = '';
    });
  }

  // ---- Console signature ----
  if (typeof console !== 'undefined' && console.log) {
    console.log('%cSAGI ISRAELI · 28', 'color:#ffd500;font:900 22px/1 "Frank Ruhl Libre",serif;padding:8px 0;');
    console.log('%cירושלים תמיד. נבנה ביד.', 'color:#888;font:italic 13px Heebo,sans-serif;');
  }
})();
