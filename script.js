const MODEL_URL = 'https://cdn.jsdelivr.net/npm/@vladmandic/face-api@1.7.14/model/';
const MATCH_THRESHOLD = 0.55;
const DETECT_INTERVAL_MS = 300;
const SPEECH_COOLDOWN_MS = 3500;

const loadingEl = document.getElementById('loading');
const loadingText = document.getElementById('loading-text');
const personName = document.getElementById('person-name');
const fileInput = document.getElementById('file-input');
const enrolledList = document.getElementById('enrolled-list');
const enrollHint = document.getElementById('enroll-hint');

const video = document.getElementById('video');
const canvas = document.getElementById('canvas');
const ctx = canvas.getContext('2d');
const videoPlaceholder = document.getElementById('video-placeholder');
const statusBanner = document.getElementById('status-banner');
const statusText = document.getElementById('status-text');
const statusSub = document.getElementById('status-sub');
const startBtn = document.getElementById('start-btn');
const stopBtn = document.getElementById('stop-btn');
const errorEl = document.getElementById('error');

let modelsLoaded = false;
let enrolled = [];   // [{ id, name, descriptor, thumbnail }]
let stream = null;
let scanning = false;
let detectTimer = null;
let audioCtx = null;
const lastSpokenAt = { authorized: 0, denied: 0, noface: 0 };
const lastSpokenName = { authorized: '' };

/* ---------- UI HELPERS ---------- */

function showError(msg) {
    errorEl.textContent = msg;
    errorEl.classList.remove('hidden');
}
function clearError() { errorEl.classList.add('hidden'); }

function setStatus(state, main, sub = '') {
    statusBanner.dataset.state = state;
    statusText.textContent = main;
    statusSub.textContent = sub;
}

function ensureAudio() {
    if (!audioCtx) {
        try { audioCtx = new (window.AudioContext || window.webkitAudioContext)(); }
        catch (_) { /* ignore */ }
    }
    if (audioCtx && audioCtx.state === 'suspended') audioCtx.resume();
}

function beep(freq, duration, type = 'square', volume = 0.25) {
    if (!audioCtx) return;
    const osc = audioCtx.createOscillator();
    const gain = audioCtx.createGain();
    osc.type = type;
    osc.frequency.value = freq;
    gain.gain.setValueAtTime(0.0001, audioCtx.currentTime);
    gain.gain.exponentialRampToValueAtTime(volume, audioCtx.currentTime + 0.015);
    gain.gain.exponentialRampToValueAtTime(0.0001, audioCtx.currentTime + duration);
    osc.connect(gain).connect(audioCtx.destination);
    osc.start();
    osc.stop(audioCtx.currentTime + duration + 0.02);
}

function speak(text) {
    try {
        if (!window.speechSynthesis) return;
        window.speechSynthesis.cancel();
        const u = new SpeechSynthesisUtterance(text);
        u.lang = 'he-IL';
        u.rate = 1.0;
        u.volume = 1.0;
        window.speechSynthesis.speak(u);
    } catch (_) { /* ignore */ }
}

function triggerAuthorized(name) {
    const now = performance.now();
    if (name === lastSpokenName.authorized && now - lastSpokenAt.authorized < SPEECH_COOLDOWN_MS) return;
    lastSpokenAt.authorized = now;
    lastSpokenName.authorized = name;
    beep(880, 0.18, 'sine', 0.2);
    setTimeout(() => beep(1320, 0.22, 'sine', 0.2), 150);
    speak(`אדם מורשה. ${name}`);
}

function triggerDenied() {
    const now = performance.now();
    if (now - lastSpokenAt.denied < SPEECH_COOLDOWN_MS) return;
    lastSpokenAt.denied = now;
    lastSpokenName.authorized = '';
    beep(440, 0.25, 'square', 0.3);
    setTimeout(() => beep(330, 0.3, 'square', 0.3), 200);
    speak('אדם לא מורשה');
}

function triggerNoFace() {
    const now = performance.now();
    if (now - lastSpokenAt.noface < SPEECH_COOLDOWN_MS) return;
    lastSpokenAt.noface = now;
    lastSpokenName.authorized = '';
    beep(660, 0.18, 'triangle', 0.2);
    speak('נא גלה את הפנים');
}

/* ---------- MODEL LOADING ---------- */

async function loadModels() {
    loadingText.textContent = 'טוען מודלים... (ייתכן שייקח רגע)';
    try {
        await Promise.all([
            faceapi.nets.tinyFaceDetector.loadFromUri(MODEL_URL),
            faceapi.nets.faceLandmark68Net.loadFromUri(MODEL_URL),
            faceapi.nets.faceRecognitionNet.loadFromUri(MODEL_URL),
        ]);
        modelsLoaded = true;
        loadingEl.classList.add('hidden');
        startBtn.disabled = false;
        setStatus('idle', 'מוכן', 'לחץ "הפעל סריקה" כדי להתחיל');
    } catch (err) {
        console.error(err);
        loadingText.textContent = 'שגיאה בטעינת המודלים. רענן את הדף.';
        loadingEl.classList.add('error-state');
    }
}

/* ---------- ENROLLMENT ---------- */

function renderEnrolled() {
    if (enrolled.length === 0) {
        enrolledList.innerHTML = '<p class="empty-msg">עדיין לא נוספו אנשים — כל פנים יזוהו כ"לא מורשה".</p>';
        return;
    }
    enrolledList.innerHTML = '';
    enrolled.forEach(p => {
        const card = document.createElement('div');
        card.className = 'person-card';
        card.innerHTML = `
            <img src="${p.thumbnail}" alt="${p.name}">
            <span class="person-name">${p.name}</span>
            <button class="person-remove" data-id="${p.id}" aria-label="הסר">×</button>
        `;
        enrolledList.appendChild(card);
    });
    enrolledList.querySelectorAll('.person-remove').forEach(btn => {
        btn.addEventListener('click', () => {
            const id = Number(btn.dataset.id);
            enrolled = enrolled.filter(p => p.id !== id);
            renderEnrolled();
        });
    });
}

function fileToImage(file) {
    return new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => {
            const img = new Image();
            img.onload = () => resolve(img);
            img.onerror = reject;
            img.src = reader.result;
        };
        reader.onerror = reject;
        reader.readAsDataURL(file);
    });
}

function cropThumb(img, box, size = 120) {
    const c = document.createElement('canvas');
    c.width = c.height = size;
    const cx = c.getContext('2d');
    const pad = Math.max(box.width, box.height) * 0.25;
    const sx = Math.max(0, box.x - pad);
    const sy = Math.max(0, box.y - pad);
    const sw = Math.min(img.width - sx, box.width + pad * 2);
    const sh = Math.min(img.height - sy, box.height + pad * 2);
    cx.drawImage(img, sx, sy, sw, sh, 0, 0, size, size);
    return c.toDataURL('image/jpeg', 0.8);
}

async function enrollFile(file) {
    const name = personName.value.trim();
    if (!name) {
        enrollHint.textContent = 'הכנס שם לפני בחירת תמונה';
        enrollHint.classList.add('hint-error');
        personName.focus();
        return;
    }
    enrollHint.classList.remove('hint-error');
    enrollHint.textContent = 'מעבד תמונה...';
    try {
        const img = await fileToImage(file);
        const result = await faceapi
            .detectSingleFace(img, new faceapi.TinyFaceDetectorOptions({ inputSize: 416, scoreThreshold: 0.5 }))
            .withFaceLandmarks()
            .withFaceDescriptor();
        if (!result) {
            enrollHint.textContent = 'לא זוהו פנים בתמונה. נסה תמונה אחרת.';
            enrollHint.classList.add('hint-error');
            return;
        }
        const thumbnail = cropThumb(img, result.detection.box);
        enrolled.push({
            id: Date.now() + Math.random(),
            name,
            descriptor: result.descriptor,
            thumbnail,
        });
        renderEnrolled();
        personName.value = '';
        enrollHint.textContent = 'נוסף בהצלחה. אפשר להוסיף עוד.';
        setTimeout(() => {
            if (enrollHint.textContent === 'נוסף בהצלחה. אפשר להוסיף עוד.') {
                enrollHint.textContent = 'הכנס שם ולחץ לבחירת תמונה. התמונה תעובד ותישמר בדפדפן בלבד.';
            }
        }, 3000);
    } catch (err) {
        console.error(err);
        enrollHint.textContent = 'שגיאה בעיבוד התמונה.';
        enrollHint.classList.add('hint-error');
    }
}

fileInput.addEventListener('change', async (e) => {
    const file = e.target.files[0];
    if (!file) return;
    await enrollFile(file);
    fileInput.value = '';
});

/* ---------- SCANNER ---------- */

function resizeCanvas() {
    canvas.width = video.videoWidth || 640;
    canvas.height = video.videoHeight || 480;
}

function findMatch(descriptor) {
    let best = { name: null, distance: Infinity };
    for (const p of enrolled) {
        const d = faceapi.euclideanDistance(descriptor, p.descriptor);
        if (d < best.distance) best = { name: p.name, distance: d };
    }
    return best.distance < MATCH_THRESHOLD ? best : null;
}

function drawFaceBox(box, color, label, sub) {
    ctx.strokeStyle = color;
    ctx.lineWidth = 3;
    ctx.shadowColor = color;
    ctx.shadowBlur = 14;
    const corner = Math.min(box.width, box.height) * 0.25;
    const x = box.x, y = box.y, w = box.width, h = box.height;
    const lines = [
        [x, y + corner, x, y, x + corner, y],
        [x + w - corner, y, x + w, y, x + w, y + corner],
        [x + w, y + h - corner, x + w, y + h, x + w - corner, y + h],
        [x + corner, y + h, x, y + h, x, y + h - corner],
    ];
    lines.forEach(([a, b, c, d, e, f]) => {
        ctx.beginPath();
        ctx.moveTo(a, b); ctx.lineTo(c, d); ctx.lineTo(e, f);
        ctx.stroke();
    });
    ctx.shadowBlur = 0;

    const paddingX = 10, paddingY = 6;
    ctx.font = 'bold 18px "Segoe UI", sans-serif';
    const labelWidth = ctx.measureText(label).width;
    const subWidth = sub ? ctx.measureText(sub).width : 0;
    const boxWidth = Math.max(labelWidth, subWidth) + paddingX * 2;
    const boxHeight = sub ? 46 : 28;
    const labelY = y + h + 8;

    ctx.fillStyle = 'rgba(0,0,0,0.75)';
    ctx.fillRect(x, labelY, boxWidth, boxHeight);
    ctx.fillStyle = color;
    ctx.fillText(label, x + paddingX, labelY + 20);
    if (sub) {
        ctx.font = '13px "Segoe UI", sans-serif';
        ctx.fillStyle = '#ddd';
        ctx.fillText(sub, x + paddingX, labelY + 40);
    }
}

async function detectOnce() {
    if (!scanning || !modelsLoaded) return;
    if (video.readyState < 2) return;

    if (canvas.width !== video.videoWidth || canvas.height !== video.videoHeight) {
        resizeCanvas();
    }

    let results = [];
    try {
        if (enrolled.length > 0) {
            results = await faceapi
                .detectAllFaces(video, new faceapi.TinyFaceDetectorOptions({ inputSize: 320, scoreThreshold: 0.5 }))
                .withFaceLandmarks()
                .withFaceDescriptors();
        } else {
            const raw = await faceapi.detectAllFaces(
                video, new faceapi.TinyFaceDetectorOptions({ inputSize: 320, scoreThreshold: 0.5 })
            );
            results = raw.map(d => ({ detection: d, descriptor: null }));
        }
    } catch (err) {
        console.error(err);
        return;
    }

    ctx.clearRect(0, 0, canvas.width, canvas.height);

    if (results.length === 0) {
        setStatus('noface', 'אין זיהוי פנים', 'גלה את פניך למצלמה');
        triggerNoFace();
        return;
    }

    let deniedCount = 0;
    let authorizedNames = [];

    for (const res of results) {
        const box = res.detection.box;
        let color, label, sub = '';
        if (res.descriptor) {
            const match = findMatch(res.descriptor);
            if (match) {
                color = '#00ff88';
                label = '✓ מורשה';
                sub = `${match.name} · ${(1 - match.distance).toFixed(2)}`;
                authorizedNames.push(match.name);
            } else {
                color = '#ff4444';
                label = '✕ לא מורשה';
                deniedCount++;
            }
        } else {
            color = '#ff4444';
            label = '✕ לא מורשה';
            sub = 'לא נוספו אנשים מורשים';
            deniedCount++;
        }
        drawFaceBox(box, color, label, sub);
    }

    if (deniedCount > 0) {
        const denyMsg = results.length === 1
            ? 'אדם לא מורשה'
            : `${deniedCount} מתוך ${results.length} לא מורשים`;
        setStatus('denied', denyMsg, '');
        triggerDenied();
    } else {
        const nameList = [...new Set(authorizedNames)].join(', ');
        setStatus('authorized', 'אדם מורשה', nameList);
        triggerAuthorized(nameList);
    }
}

async function startScan() {
    clearError();
    ensureAudio();
    if (!modelsLoaded) return;
    try {
        stream = await navigator.mediaDevices.getUserMedia({
            video: { facingMode: 'user', width: { ideal: 640 }, height: { ideal: 480 } },
            audio: false,
        });
        video.srcObject = stream;
        await video.play();
        videoPlaceholder.classList.add('hidden');
        scanning = true;
        resizeCanvas();
        startBtn.disabled = true;
        stopBtn.disabled = false;
        setStatus('scanning', 'מחפש פנים...', '');
        detectTimer = setInterval(detectOnce, DETECT_INTERVAL_MS);
    } catch (err) {
        console.error(err);
        const msg = err?.message || String(err);
        if (msg.includes('Permission') || msg.includes('NotAllowed')) {
            showError('נדרשת הרשאה למצלמה. אשר גישה ונסה שוב.');
        } else if (msg.includes('NotFound')) {
            showError('לא נמצאה מצלמה במכשיר.');
        } else {
            showError('שגיאה: ' + msg);
        }
    }
}

function stopScan() {
    scanning = false;
    if (detectTimer) { clearInterval(detectTimer); detectTimer = null; }
    if (stream) { stream.getTracks().forEach(t => t.stop()); stream = null; }
    video.srcObject = null;
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    videoPlaceholder.classList.remove('hidden');
    startBtn.disabled = false;
    stopBtn.disabled = true;
    setStatus('idle', 'הופסק', 'לחץ "הפעל סריקה" כדי להתחיל שוב');
}

startBtn.addEventListener('click', startScan);
stopBtn.addEventListener('click', stopScan);

/* ---------- INIT ---------- */

if (!navigator.mediaDevices?.getUserMedia) {
    showError('הדפדפן לא תומך בגישה למצלמה.');
}
if (typeof faceapi === 'undefined') {
    loadingText.textContent = 'לא הצלחתי לטעון את ספריית face-api.';
} else {
    loadModels();
}
