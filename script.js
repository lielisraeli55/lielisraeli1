const video = document.getElementById('video');
const canvas = document.getElementById('canvas');
const ctx = canvas.getContext('2d');
const startBtn = document.getElementById('start-btn');
const stopBtn = document.getElementById('stop-btn');
const statusEl = document.getElementById('status');
const handCountEl = document.getElementById('hand-count');
const fpsEl = document.getElementById('fps');
const errorEl = document.getElementById('error');
const mirrorCb = document.getElementById('mirror');
const showLandmarksCb = document.getElementById('show-landmarks');
const showConnectionsCb = document.getElementById('show-connections');
const videoContainer = document.querySelector('.video-container');

let hands = null;
let camera = null;
let running = false;
let lastFrameTime = performance.now();
let frameCount = 0;
let fpsAccumulator = 0;

const HAND_CONNECTIONS = [
    [0, 1], [1, 2], [2, 3], [3, 4],
    [0, 5], [5, 6], [6, 7], [7, 8],
    [5, 9], [9, 10], [10, 11], [11, 12],
    [9, 13], [13, 14], [14, 15], [15, 16],
    [13, 17], [0, 17], [17, 18], [18, 19], [19, 20],
];

function setStatus(text, isError = false) {
    statusEl.textContent = text;
    statusEl.style.color = isError ? '#ff6666' : '#00ff88';
}

function showError(message) {
    errorEl.textContent = message;
    errorEl.classList.remove('hidden');
}

function clearError() {
    errorEl.classList.add('hidden');
}

function resizeCanvas() {
    canvas.width = video.videoWidth || 1280;
    canvas.height = video.videoHeight || 720;
}

function drawResults(results) {
    ctx.clearRect(0, 0, canvas.width, canvas.height);

    const landmarksList = results.multiHandLandmarks || [];
    handCountEl.textContent = landmarksList.length;

    landmarksList.forEach((landmarks, idx) => {
        const handedness = results.multiHandedness?.[idx]?.label || 'Hand';
        const color = handedness === 'Left' ? '#00ff88' : '#00c8ff';
        const glowColor = handedness === 'Left' ? 'rgba(0,255,136,0.8)' : 'rgba(0,200,255,0.8)';

        if (showConnectionsCb.checked) {
            ctx.strokeStyle = color;
            ctx.lineWidth = 4;
            ctx.shadowColor = glowColor;
            ctx.shadowBlur = 12;
            ctx.lineCap = 'round';

            HAND_CONNECTIONS.forEach(([a, b]) => {
                const p1 = landmarks[a];
                const p2 = landmarks[b];
                ctx.beginPath();
                ctx.moveTo(p1.x * canvas.width, p1.y * canvas.height);
                ctx.lineTo(p2.x * canvas.width, p2.y * canvas.height);
                ctx.stroke();
            });
            ctx.shadowBlur = 0;
        }

        if (showLandmarksCb.checked) {
            landmarks.forEach((lm, i) => {
                const x = lm.x * canvas.width;
                const y = lm.y * canvas.height;
                const isJoint = i === 0 || i === 4 || i === 8 || i === 12 || i === 16 || i === 20;
                const radius = isJoint ? 8 : 5;

                ctx.fillStyle = '#ffffff';
                ctx.shadowColor = glowColor;
                ctx.shadowBlur = 15;
                ctx.beginPath();
                ctx.arc(x, y, radius, 0, Math.PI * 2);
                ctx.fill();

                ctx.strokeStyle = color;
                ctx.lineWidth = 2;
                ctx.shadowBlur = 0;
                ctx.beginPath();
                ctx.arc(x, y, radius, 0, Math.PI * 2);
                ctx.stroke();
            });
        }

        const wrist = landmarks[0];
        const labelX = wrist.x * canvas.width;
        const labelY = wrist.y * canvas.height + 30;

        ctx.save();
        if (mirrorCb.checked) {
            ctx.translate(canvas.width, 0);
            ctx.scale(-1, 1);
            ctx.translate(-labelX * 2, 0);
        }
        ctx.font = 'bold 16px "Courier New", monospace';
        ctx.fillStyle = color;
        ctx.shadowColor = glowColor;
        ctx.shadowBlur = 10;
        ctx.textAlign = 'center';
        ctx.fillText(`[${handedness.toUpperCase()}]`, labelX, labelY);
        ctx.restore();

        const xs = landmarks.map(l => l.x * canvas.width);
        const ys = landmarks.map(l => l.y * canvas.height);
        const minX = Math.min(...xs) - 20;
        const minY = Math.min(...ys) - 20;
        const maxX = Math.max(...xs) + 20;
        const maxY = Math.max(...ys) + 20;

        ctx.strokeStyle = color;
        ctx.setLineDash([8, 6]);
        ctx.lineWidth = 1.5;
        ctx.shadowBlur = 0;
        ctx.strokeRect(minX, minY, maxX - minX, maxY - minY);
        ctx.setLineDash([]);
    });

    updateFps();
}

function updateFps() {
    const now = performance.now();
    const delta = now - lastFrameTime;
    lastFrameTime = now;
    fpsAccumulator += delta;
    frameCount++;
    if (fpsAccumulator >= 500) {
        const fps = Math.round((frameCount * 1000) / fpsAccumulator);
        fpsEl.textContent = fps;
        fpsAccumulator = 0;
        frameCount = 0;
    }
}

async function start() {
    clearError();
    setStatus('LOADING MODEL...');
    startBtn.disabled = true;

    try {
        hands = new Hands({
            locateFile: (file) => `https://cdn.jsdelivr.net/npm/@mediapipe/hands@0.4.1646424915/${file}`,
        });

        hands.setOptions({
            maxNumHands: 2,
            modelComplexity: 1,
            minDetectionConfidence: 0.6,
            minTrackingConfidence: 0.5,
        });

        hands.onResults((results) => {
            if (canvas.width !== video.videoWidth || canvas.height !== video.videoHeight) {
                resizeCanvas();
            }
            drawResults(results);
            if (setStatus && statusEl.textContent !== 'TRACKING') {
                setStatus('TRACKING');
            }
        });

        setStatus('REQUESTING CAMERA...');

        camera = new Camera(video, {
            onFrame: async () => {
                if (running && hands) {
                    await hands.send({ image: video });
                }
            },
            width: 1280,
            height: 720,
        });

        await camera.start();
        running = true;
        resizeCanvas();
        setStatus('TRACKING');
        stopBtn.disabled = false;
    } catch (err) {
        console.error(err);
        setStatus('ERROR', true);
        const msg = err?.message || String(err);
        if (msg.includes('Permission') || msg.includes('NotAllowed')) {
            showError('נדרשת הרשאה למצלמה. אנא אפשר גישה למצלמה ונסה שוב.');
        } else if (msg.includes('NotFound')) {
            showError('לא נמצאה מצלמה במכשיר.');
        } else {
            showError('שגיאה: ' + msg);
        }
        startBtn.disabled = false;
    }
}

async function stop() {
    running = false;
    if (camera) {
        await camera.stop();
        camera = null;
    }
    if (hands) {
        hands.close();
        hands = null;
    }
    if (video.srcObject) {
        video.srcObject.getTracks().forEach(t => t.stop());
        video.srcObject = null;
    }
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    setStatus('STOPPED');
    handCountEl.textContent = '0';
    fpsEl.textContent = '0';
    startBtn.disabled = false;
    stopBtn.disabled = true;
}

startBtn.addEventListener('click', start);
stopBtn.addEventListener('click', stop);

mirrorCb.addEventListener('change', () => {
    videoContainer.classList.toggle('mirror', mirrorCb.checked);
});

videoContainer.classList.toggle('mirror', mirrorCb.checked);

if (!navigator.mediaDevices?.getUserMedia) {
    showError('הדפדפן שלך לא תומך בגישה למצלמה.');
    startBtn.disabled = true;
    setStatus('UNSUPPORTED', true);
} else {
    setStatus('READY');
}
