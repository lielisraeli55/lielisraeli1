const APPS = [
    { id: 'clock',    name: 'שעון',         icon: '⏰', color: '#00c8ff' },
    { id: 'camera',   name: 'מצלמה',        icon: '📷', color: '#ff66cc' },
    { id: 'messages', name: 'הודעות',       icon: '💬', color: '#00ff88' },
    { id: 'calls',    name: 'שיחות',        icon: '📞', color: '#44ddff' },
    { id: 'music',    name: 'מוזיקה',       icon: '🎵', color: '#ff8844' },
    { id: 'maps',     name: 'מפות',         icon: '🗺️', color: '#88ff44' },
    { id: 'mail',     name: 'דואר',         icon: '✉️', color: '#ffaa00' },
    { id: 'photos',   name: 'גלריה',        icon: '🖼️', color: '#ff5577' },
    { id: 'settings', name: 'הגדרות',       icon: '⚙️', color: '#aaaaaa' },
    { id: 'contacts', name: 'אנשי קשר',     icon: '👥', color: '#33ddaa' },
    { id: 'notes',    name: 'פתקים',        icon: '📝', color: '#ffdd44' },
    { id: 'weather',  name: 'מזג אוויר',    icon: '⛅', color: '#66bbff' },
];

const PINCH_ON  = 0.18;
const PINCH_OFF = 0.30;
const PINCH_STABILITY = 2;
const SMOOTH = 0.35;

const grid = document.getElementById('apps-grid');
const indCam = document.getElementById('ind-cam');
const indHand = document.getElementById('ind-hand');
const indPinch = document.getElementById('ind-pinch');
const startBtn = document.getElementById('start-btn');
const stopBtn = document.getElementById('stop-btn');
const errorEl = document.getElementById('error');
const openCountEl = document.getElementById('open-count');
const pointer = document.getElementById('pointer');
const camPreview = document.getElementById('cam-preview');
const video = document.getElementById('video');
const camCanvas = document.getElementById('cam-canvas');
const camCtx = camCanvas.getContext('2d');
const modalBackdrop = document.getElementById('modal-backdrop');
const modal = document.getElementById('modal');
const modalIcon = document.getElementById('modal-icon');
const modalTitle = document.getElementById('modal-title');
const modalContent = document.getElementById('modal-content');
const modalClose = document.getElementById('modal-close');

let hands = null;
let camera = null;
let running = false;
let target = { x: window.innerWidth / 2, y: window.innerHeight / 2, valid: false };
let pos = { x: target.x, y: target.y };
let isPinched = false;
let wasPinched = false;
let pinchOnFrames = 0;
let pinchOffFrames = 0;
let hoverEl = null;
let openCount = 0;
let audioCtx = null;

const HAND_CONNECTIONS = [
    [0,1],[1,2],[2,3],[3,4],
    [0,5],[5,6],[6,7],[7,8],
    [5,9],[9,10],[10,11],[11,12],
    [9,13],[13,14],[14,15],[15,16],
    [13,17],[0,17],[17,18],[18,19],[19,20],
];

/* ---------- APPS GRID ---------- */

APPS.forEach(app => {
    const el = document.createElement('div');
    el.className = 'app';
    el.dataset.app = app.id;
    el.style.setProperty('--app-color', app.color);
    el.innerHTML = `
        <div class="app-icon">${app.icon}</div>
        <div class="app-name">${app.name}</div>
    `;
    el.addEventListener('click', () => openApp(app.id));
    grid.appendChild(el);
});

/* ---------- AUDIO ---------- */

function ensureAudio() {
    if (!audioCtx) {
        try { audioCtx = new (window.AudioContext || window.webkitAudioContext)(); }
        catch (_) { /* ignore */ }
    }
    if (audioCtx?.state === 'suspended') audioCtx.resume();
}

function beep(freq, duration, type = 'sine', volume = 0.18) {
    if (!audioCtx) return;
    const osc = audioCtx.createOscillator();
    const gain = audioCtx.createGain();
    osc.type = type;
    osc.frequency.value = freq;
    gain.gain.setValueAtTime(0.0001, audioCtx.currentTime);
    gain.gain.exponentialRampToValueAtTime(volume, audioCtx.currentTime + 0.01);
    gain.gain.exponentialRampToValueAtTime(0.0001, audioCtx.currentTime + duration);
    osc.connect(gain).connect(audioCtx.destination);
    osc.start();
    osc.stop(audioCtx.currentTime + duration + 0.02);
}

/* ---------- HOVER / CLICK ---------- */

function getAppUnder(x, y) {
    const els = document.elementsFromPoint(x, y);
    for (const el of els) {
        const app = el.closest && el.closest('.app');
        if (app) return app;
        const close = el.closest && el.closest('.modal-close');
        if (close) return close;
    }
    return null;
}

function setHover(el) {
    if (hoverEl === el) return;
    if (hoverEl) hoverEl.classList.remove('hovering');
    hoverEl = el;
    if (hoverEl) {
        hoverEl.classList.add('hovering');
        beep(900, 0.05, 'sine', 0.08);
    }
}

function pinchClick() {
    if (!hoverEl) {
        pointer.classList.add('miss');
        setTimeout(() => pointer.classList.remove('miss'), 200);
        return;
    }
    pointer.classList.add('clicked');
    setTimeout(() => pointer.classList.remove('clicked'), 250);
    beep(1320, 0.12, 'triangle', 0.25);
    setTimeout(() => beep(1760, 0.12, 'triangle', 0.2), 90);
    if (hoverEl.classList.contains('app')) {
        openApp(hoverEl.dataset.app);
    } else if (hoverEl.classList.contains('modal-close')) {
        closeModal();
    }
}

/* ---------- APP MODAL ---------- */

const appContents = {
    clock:    () => `השעה כעת: ${new Date().toLocaleTimeString('he-IL')}`,
    camera:   () => 'מצלמה פעילה. (זו אפליקציה דמה)',
    messages: () => 'אין הודעות חדשות.',
    calls:    () => 'אין שיחות שלא נענו.',
    music:    () => 'מנגן: שיר אקראי - אמן דמיוני',
    maps:     () => 'מיקום: ישראל. (הדגמה)',
    mail:     () => '0 הודעות לא נקראו.',
    photos:   () => '143 תמונות בגלריה. (הדגמה)',
    settings: () => 'גרסה 1.0 — בקרת אצבע.',
    contacts: () => '12 אנשי קשר.',
    notes:    () => 'אין פתקים שמורים.',
    weather:  () => '24°C, מעונן חלקית. (הדגמה)',
};

function openApp(id) {
    const app = APPS.find(a => a.id === id);
    if (!app) return;
    openCount++;
    openCountEl.textContent = openCount;
    modalIcon.textContent = app.icon;
    modalIcon.style.color = app.color;
    modalTitle.textContent = app.name;
    modalContent.textContent = (appContents[id] || (() => 'אפליקציה נפתחה.'))();
    modal.style.setProperty('--app-color', app.color);
    modalBackdrop.classList.remove('hidden');
}

function closeModal() {
    modalBackdrop.classList.add('hidden');
}

modalBackdrop.addEventListener('click', (e) => {
    if (e.target === modalBackdrop) closeModal();
});
modalClose.addEventListener('click', closeModal);

/* ---------- HAND TRACKING ---------- */

function handScale(lm) {
    const a = lm[0], b = lm[9];
    const dx = a.x - b.x, dy = a.y - b.y;
    return Math.hypot(dx, dy);
}

function pinchRatio(lm) {
    const t = lm[4], i = lm[8];
    const dx = t.x - i.x;
    const dy = t.y - i.y;
    const dz = (t.z || 0) - (i.z || 0);
    const dist3d = Math.sqrt(dx * dx + dy * dy + dz * dz);
    return dist3d / Math.max(handScale(lm), 0.001);
}

function drawPreview(landmarks) {
    camCtx.clearRect(0, 0, camCanvas.width, camCanvas.height);
    if (!landmarks) return;
    const w = camCanvas.width, h = camCanvas.height;

    camCtx.lineWidth = 2;
    camCtx.strokeStyle = isPinched ? '#ffaa00' : '#00ff88';
    camCtx.shadowColor = camCtx.strokeStyle;
    camCtx.shadowBlur = 6;
    HAND_CONNECTIONS.forEach(([a, b]) => {
        camCtx.beginPath();
        camCtx.moveTo(landmarks[a].x * w, landmarks[a].y * h);
        camCtx.lineTo(landmarks[b].x * w, landmarks[b].y * h);
        camCtx.stroke();
    });
    camCtx.shadowBlur = 0;

    landmarks.forEach((lm, i) => {
        const x = lm.x * w, y = lm.y * h;
        let radius = 2.5, fill = '#ffffff';
        if (i === 4) { radius = 4; fill = '#ffaa00'; }
        if (i === 8) { radius = 4; fill = '#00c8ff'; }
        camCtx.fillStyle = fill;
        camCtx.beginPath();
        camCtx.arc(x, y, radius, 0, Math.PI * 2);
        camCtx.fill();
    });

    const t = landmarks[4], idx = landmarks[8];
    camCtx.strokeStyle = isPinched ? '#ffaa00' : 'rgba(255,255,255,0.4)';
    camCtx.lineWidth = isPinched ? 3 : 1.5;
    camCtx.beginPath();
    camCtx.moveTo(t.x * w, t.y * h);
    camCtx.lineTo(idx.x * w, idx.y * h);
    camCtx.stroke();
}

function onResults(results) {
    if (camCanvas.width !== video.videoWidth || camCanvas.height !== video.videoHeight) {
        if (video.videoWidth) {
            camCanvas.width = video.videoWidth;
            camCanvas.height = video.videoHeight;
        }
    }

    const list = results.multiHandLandmarks || [];
    if (list.length === 0) {
        target.valid = false;
        indHand.classList.remove('on');
        drawPreview(null);
        wasPinched = false;
        isPinched = false;
        pinchOnFrames = 0;
        pinchOffFrames = 0;
        indPinch.classList.remove('on');
        pointer.classList.remove('pinched', 'approaching');
        pointer.style.setProperty('--closeness', '0');
        return;
    }

    const lm = list[0];
    const idx = lm[8];
    const screenX = (1 - idx.x) * window.innerWidth;
    const screenY = idx.y * window.innerHeight;
    target.x = screenX;
    target.y = screenY;
    target.valid = true;
    indHand.classList.add('on');

    const ratio = pinchRatio(lm);
    if (ratio < PINCH_ON) {
        pinchOnFrames++;
        pinchOffFrames = 0;
        if (!isPinched && pinchOnFrames >= PINCH_STABILITY) isPinched = true;
    } else if (ratio > PINCH_OFF) {
        pinchOffFrames++;
        pinchOnFrames = 0;
        if (isPinched && pinchOffFrames >= PINCH_STABILITY) isPinched = false;
    } else {
        pinchOnFrames = 0;
        pinchOffFrames = 0;
    }

    if (isPinched && !wasPinched) pinchClick();
    wasPinched = isPinched;

    const closeness = Math.max(0, Math.min(1, (PINCH_OFF - ratio) / (PINCH_OFF - PINCH_ON)));
    pointer.style.setProperty('--closeness', closeness.toFixed(2));

    indPinch.classList.toggle('on', isPinched);
    pointer.classList.toggle('pinched', isPinched);
    pointer.classList.toggle('approaching', !isPinched && closeness > 0.4);

    drawPreview(lm);
}

function loop() {
    pos.x += (target.x - pos.x) * SMOOTH;
    pos.y += (target.y - pos.y) * SMOOTH;
    pointer.style.transform = `translate(${pos.x - 30}px, ${pos.y - 30}px)`;
    pointer.classList.toggle('inactive', !target.valid);

    if (target.valid) {
        const el = getAppUnder(pos.x, pos.y);
        setHover(el);
    } else {
        setHover(null);
    }

    requestAnimationFrame(loop);
}
requestAnimationFrame(loop);

/* ---------- LIFECYCLE ---------- */

async function start() {
    errorEl.classList.add('hidden');
    ensureAudio();
    startBtn.disabled = true;
    startBtn.textContent = 'מתחבר...';
    try {
        hands = new Hands({
            locateFile: (file) => `https://cdn.jsdelivr.net/npm/@mediapipe/hands@0.4.1646424915/${file}`,
        });
        hands.setOptions({
            maxNumHands: 1,
            modelComplexity: 1,
            minDetectionConfidence: 0.7,
            minTrackingConfidence: 0.65,
        });
        hands.onResults(onResults);

        camera = new Camera(video, {
            onFrame: async () => {
                if (running && hands) await hands.send({ image: video });
            },
            width: 640,
            height: 480,
            facingMode: 'user',
        });
        running = true;
        await camera.start();
        indCam.classList.add('on');
        camPreview.classList.add('active');
        startBtn.classList.add('hidden');
        stopBtn.classList.remove('hidden');
        startBtn.textContent = 'הפעל מצלמה';
        startBtn.disabled = false;
    } catch (err) {
        console.error(err);
        const msg = err?.message || String(err);
        if (msg.includes('Permission') || msg.includes('NotAllowed')) {
            showError('נדרשת הרשאה למצלמה. אשר ונסה שוב.');
        } else if (msg.includes('NotFound')) {
            showError('לא נמצאה מצלמה.');
        } else {
            showError('שגיאה: ' + msg);
        }
        startBtn.textContent = 'הפעל מצלמה';
        startBtn.disabled = false;
    }
}

async function stop() {
    running = false;
    target.valid = false;
    isPinched = false;
    wasPinched = false;
    if (camera) { try { await camera.stop(); } catch (_) {} camera = null; }
    if (hands) { hands.close(); hands = null; }
    if (video.srcObject) {
        video.srcObject.getTracks().forEach(t => t.stop());
        video.srcObject = null;
    }
    camCtx.clearRect(0, 0, camCanvas.width, camCanvas.height);
    indCam.classList.remove('on');
    indHand.classList.remove('on');
    indPinch.classList.remove('on');
    camPreview.classList.remove('active');
    startBtn.classList.remove('hidden');
    stopBtn.classList.add('hidden');
}

function showError(msg) {
    errorEl.textContent = msg;
    errorEl.classList.remove('hidden');
}

startBtn.addEventListener('click', start);
stopBtn.addEventListener('click', stop);

if (!navigator.mediaDevices?.getUserMedia) {
    showError('הדפדפן לא תומך בגישה למצלמה.');
    startBtn.disabled = true;
}

/* ---------- PWA INSTALL ---------- */

const installBtn = document.getElementById('install-btn');
let deferredPrompt = null;

window.addEventListener('beforeinstallprompt', (e) => {
    e.preventDefault();
    deferredPrompt = e;
    installBtn.classList.remove('hidden');
});

installBtn.addEventListener('click', async () => {
    if (!deferredPrompt) return;
    installBtn.disabled = true;
    deferredPrompt.prompt();
    const { outcome } = await deferredPrompt.userChoice;
    deferredPrompt = null;
    if (outcome === 'accepted') {
        installBtn.classList.add('hidden');
    }
    installBtn.disabled = false;
});

window.addEventListener('appinstalled', () => {
    installBtn.classList.add('hidden');
    deferredPrompt = null;
});

if ('serviceWorker' in navigator) {
    window.addEventListener('load', () => {
        navigator.serviceWorker.register('service-worker.js').catch((err) => {
            console.warn('Service worker registration failed:', err);
        });
    });
}
