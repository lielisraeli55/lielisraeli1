const fpPad = document.getElementById('fp-pad');
const fpBar = document.getElementById('fp-bar');
const fpHint = document.getElementById('fp-hint');

const video = document.getElementById('video');
const canvas = document.getElementById('canvas');
const ctx = canvas.getContext('2d');
const cdBar = document.getElementById('cd-bar');
const cdText = document.getElementById('cd-text');
const faceStatus = document.getElementById('face-status');
const faceDot = document.getElementById('face-dot');
const warning = document.getElementById('warning');
const restartBtn = document.getElementById('restart-btn');
const errorEl = document.getElementById('error');

const stageFingerprint = document.getElementById('stage-fingerprint');
const stageFace = document.getElementById('stage-face');
const stageSuccess = document.getElementById('stage-success');
const stepEls = document.querySelectorAll('.stage-indicator .step');

const FP_HOLD_MS = 2000;
const FACE_REQUIRED_MS = 4000;
const WARNING_COOLDOWN_MS = 2500;
const FACE_LOST_GRACE_MS = 250;

let fpProgress = 0;
let fpHolding = false;
let fpStart = 0;
let fpRaf = null;

let faceDetection = null;
let camera = null;
let scanning = false;
let faceAccumulated = 0;
let lastTick = 0;
let faceVisible = false;
let faceLostSince = 0;
let lastWarningAt = 0;
let audioCtx = null;

const FP_CIRC = 2 * Math.PI * 92;
fpBar.style.strokeDasharray = FP_CIRC;
fpBar.style.strokeDashoffset = FP_CIRC;

const CD_CIRC = 2 * Math.PI * 54;
cdBar.style.strokeDasharray = CD_CIRC;
cdBar.style.strokeDashoffset = CD_CIRC;

function showStage(which) {
    [stageFingerprint, stageFace, stageSuccess].forEach(s => s.classList.remove('active'));
    which.classList.add('active');

    stepEls.forEach(el => el.classList.remove('active', 'done'));
    if (which === stageFingerprint) {
        stepEls[0].classList.add('active');
    } else if (which === stageFace) {
        stepEls[0].classList.add('done');
        stepEls[1].classList.add('active');
    } else if (which === stageSuccess) {
        stepEls[0].classList.add('done');
        stepEls[1].classList.add('done');
        stepEls[2].classList.add('active');
    }
}

function showError(msg) {
    errorEl.textContent = msg;
    errorEl.classList.remove('hidden');
}

function clearError() {
    errorEl.classList.add('hidden');
}

function ensureAudio() {
    if (!audioCtx) {
        try {
            audioCtx = new (window.AudioContext || window.webkitAudioContext)();
        } catch (_) { /* ignore */ }
    }
    if (audioCtx && audioCtx.state === 'suspended') audioCtx.resume();
}

function beep() {
    if (!audioCtx) return;
    const osc = audioCtx.createOscillator();
    const gain = audioCtx.createGain();
    osc.type = 'square';
    osc.frequency.setValueAtTime(880, audioCtx.currentTime);
    osc.frequency.exponentialRampToValueAtTime(440, audioCtx.currentTime + 0.18);
    gain.gain.setValueAtTime(0.0001, audioCtx.currentTime);
    gain.gain.exponentialRampToValueAtTime(0.25, audioCtx.currentTime + 0.02);
    gain.gain.exponentialRampToValueAtTime(0.0001, audioCtx.currentTime + 0.35);
    osc.connect(gain).connect(audioCtx.destination);
    osc.start();
    osc.stop(audioCtx.currentTime + 0.4);
}

function speakWarning() {
    try {
        if (!window.speechSynthesis) return;
        window.speechSynthesis.cancel();
        const u = new SpeechSynthesisUtterance('נא לא לכסות את הפנים');
        u.lang = 'he-IL';
        u.rate = 1.0;
        u.volume = 1.0;
        window.speechSynthesis.speak(u);
    } catch (_) { /* ignore */ }
}

function triggerWarning() {
    const now = performance.now();
    if (now - lastWarningAt < WARNING_COOLDOWN_MS) return;
    lastWarningAt = now;
    beep();
    speakWarning();
}

/* ---------- STAGE 1: FINGERPRINT ---------- */

function setFpProgress(p) {
    fpProgress = Math.max(0, Math.min(1, p));
    fpBar.style.strokeDashoffset = FP_CIRC * (1 - fpProgress);
    fpPad.classList.toggle('holding', fpProgress > 0);
}

function fpTick() {
    if (!fpHolding) return;
    const elapsed = performance.now() - fpStart;
    const p = elapsed / FP_HOLD_MS;
    setFpProgress(p);
    if (p >= 1) {
        fpHolding = false;
        fpPad.classList.add('done');
        fpHint.textContent = 'טביעה אומתה ✓';
        setTimeout(() => {
            startFaceScan();
        }, 500);
    } else {
        fpRaf = requestAnimationFrame(fpTick);
    }
}

function startHold(e) {
    e.preventDefault();
    ensureAudio();
    if (fpPad.classList.contains('done')) return;
    fpHolding = true;
    fpStart = performance.now() - fpProgress * FP_HOLD_MS;
    fpHint.textContent = 'מעבד...';
    fpRaf = requestAnimationFrame(fpTick);
}

function endHold() {
    if (!fpHolding) return;
    fpHolding = false;
    cancelAnimationFrame(fpRaf);
    if (fpProgress < 1) {
        fpHint.textContent = 'הוסר מוקדם מדי — נסה שוב';
        const decay = () => {
            if (fpHolding) return;
            setFpProgress(fpProgress - 0.05);
            if (fpProgress > 0) requestAnimationFrame(decay);
            else fpHint.textContent = 'לחץ והחזק 2 שניות';
        };
        requestAnimationFrame(decay);
    }
}

fpPad.addEventListener('mousedown', startHold);
fpPad.addEventListener('touchstart', startHold, { passive: false });
window.addEventListener('mouseup', endHold);
window.addEventListener('touchend', endHold);
window.addEventListener('touchcancel', endHold);

/* ---------- STAGE 2: FACE SCAN ---------- */

function resizeCanvas() {
    canvas.width = video.videoWidth || 1280;
    canvas.height = video.videoHeight || 720;
}

function drawFace(results) {
    ctx.clearRect(0, 0, canvas.width, canvas.height);

    const detections = results.detections || [];
    const now = performance.now();

    if (detections.length > 0) {
        const det = detections[0];
        const bbox = det.boundingBox;
        const w = bbox.width * canvas.width;
        const h = bbox.height * canvas.height;
        const cx = bbox.xCenter * canvas.width;
        const cy = bbox.yCenter * canvas.height;
        const x = cx - w / 2;
        const y = cy - h / 2;

        ctx.strokeStyle = '#00ff88';
        ctx.lineWidth = 3;
        ctx.shadowColor = 'rgba(0,255,136,0.9)';
        ctx.shadowBlur = 14;
        const corner = Math.min(w, h) * 0.25;
        [
            [x, y + corner, x, y, x + corner, y],
            [x + w - corner, y, x + w, y, x + w, y + corner],
            [x + w, y + h - corner, x + w, y + h, x + w - corner, y + h],
            [x + corner, y + h, x, y + h, x, y + h - corner],
        ].forEach(([a, b, c, d, e, f]) => {
            ctx.beginPath();
            ctx.moveTo(a, b);
            ctx.lineTo(c, d);
            ctx.lineTo(e, f);
            ctx.stroke();
        });

        if (det.landmarks) {
            ctx.fillStyle = '#00ff88';
            det.landmarks.forEach(lm => {
                ctx.beginPath();
                ctx.arc(lm.x * canvas.width, lm.y * canvas.height, 4, 0, Math.PI * 2);
                ctx.fill();
            });
        }
        ctx.shadowBlur = 0;

        faceVisible = true;
        faceLostSince = 0;
        faceStatus.textContent = 'פנים זוהו';
        faceDot.style.background = '#00ff88';
    } else {
        if (faceVisible) {
            if (faceLostSince === 0) faceLostSince = now;
            if (now - faceLostSince > FACE_LOST_GRACE_MS) {
                faceVisible = false;
            }
        }
        if (!faceVisible) {
            faceStatus.textContent = 'אין זיהוי';
            faceDot.style.background = '#ff4444';
        }
    }

    warning.classList.toggle('hidden', faceVisible || !scanning);
    if (!faceVisible && scanning) triggerWarning();

    if (scanning) {
        const dt = lastTick ? now - lastTick : 0;
        lastTick = now;
        if (faceVisible) {
            faceAccumulated = Math.min(FACE_REQUIRED_MS, faceAccumulated + dt);
        } else {
            faceAccumulated = Math.max(0, faceAccumulated - dt * 0.5);
        }
        const p = faceAccumulated / FACE_REQUIRED_MS;
        cdBar.style.strokeDashoffset = CD_CIRC * (1 - p);
        const remaining = Math.max(0, (FACE_REQUIRED_MS - faceAccumulated) / 1000);
        cdText.textContent = remaining.toFixed(1);

        if (faceAccumulated >= FACE_REQUIRED_MS) {
            scanning = false;
            stopFaceScan().then(() => showStage(stageSuccess));
        }
    }
}

async function startFaceScan() {
    clearError();
    showStage(stageFace);
    faceAccumulated = 0;
    lastTick = 0;
    faceVisible = false;
    faceLostSince = 0;
    cdBar.style.strokeDashoffset = CD_CIRC;
    cdText.textContent = '4.0';

    try {
        faceDetection = new FaceDetection({
            locateFile: (file) => `https://cdn.jsdelivr.net/npm/@mediapipe/face_detection@0.4.1646425229/${file}`,
        });
        faceDetection.setOptions({
            model: 'short',
            minDetectionConfidence: 0.6,
        });
        faceDetection.onResults((results) => {
            if (canvas.width !== video.videoWidth || canvas.height !== video.videoHeight) {
                resizeCanvas();
            }
            drawFace(results);
        });

        camera = new Camera(video, {
            onFrame: async () => {
                if (scanning && faceDetection) {
                    await faceDetection.send({ image: video });
                }
            },
            width: 1280,
            height: 720,
            facingMode: 'user',
        });

        scanning = true;
        await camera.start();
        resizeCanvas();
    } catch (err) {
        console.error(err);
        scanning = false;
        const msg = err?.message || String(err);
        if (msg.includes('Permission') || msg.includes('NotAllowed')) {
            showError('נדרשת הרשאה למצלמה. אשר ורענן את הדף.');
        } else if (msg.includes('NotFound')) {
            showError('לא נמצאה מצלמה.');
        } else {
            showError('שגיאה: ' + msg);
        }
        showStage(stageFingerprint);
        resetFingerprint();
    }
}

async function stopFaceScan() {
    scanning = false;
    try {
        if (camera) { await camera.stop(); camera = null; }
        if (faceDetection) { faceDetection.close(); faceDetection = null; }
        if (video.srcObject) {
            video.srcObject.getTracks().forEach(t => t.stop());
            video.srcObject = null;
        }
    } catch (_) { /* ignore */ }
    ctx.clearRect(0, 0, canvas.width, canvas.height);
}

/* ---------- RESTART ---------- */

function resetFingerprint() {
    fpHolding = false;
    setFpProgress(0);
    fpPad.classList.remove('done');
    fpHint.textContent = 'לחץ והחזק 2 שניות';
}

restartBtn.addEventListener('click', () => {
    resetFingerprint();
    showStage(stageFingerprint);
});

/* ---------- INIT ---------- */

if (!navigator.mediaDevices?.getUserMedia) {
    showError('הדפדפן לא תומך בגישה למצלמה.');
}
showStage(stageFingerprint);
