const CACHE_NAME = 'air-touch-v4';
const ASSETS = [
    './',
    './index.html',
    './style.css',
    './script.js',
    './manifest.webmanifest',
    './icons/icon-192.png',
    './icons/icon-512.png',
    './icons/icon-maskable-192.png',
    './icons/icon-maskable-512.png',
    './icons/apple-touch-icon.png',
    './icons/favicon-32.png',
];

self.addEventListener('install', (event) => {
    event.waitUntil(
        caches.open(CACHE_NAME).then((cache) =>
            cache.addAll(ASSETS).catch((err) => {
                console.warn('SW precache failed for some assets:', err);
            })
        ).then(() => self.skipWaiting())
    );
});

self.addEventListener('activate', (event) => {
    event.waitUntil(
        caches.keys().then((keys) =>
            Promise.all(keys.filter((k) => k !== CACHE_NAME).map((k) => caches.delete(k)))
        ).then(() => self.clients.claim())
    );
});

self.addEventListener('fetch', (event) => {
    const req = event.request;
    if (req.method !== 'GET') return;

    const url = new URL(req.url);
    const isMediapipe = url.hostname.includes('cdn.jsdelivr.net') &&
                        url.pathname.includes('@mediapipe');

    if (isMediapipe) {
        event.respondWith(
            caches.open(CACHE_NAME).then(async (cache) => {
                const cached = await cache.match(req);
                if (cached) return cached;
                try {
                    const fresh = await fetch(req);
                    if (fresh.ok) cache.put(req, fresh.clone());
                    return fresh;
                } catch (e) {
                    return cached || Response.error();
                }
            })
        );
        return;
    }

    if (url.origin === self.location.origin) {
        event.respondWith(
            (async () => {
                try {
                    const fresh = await fetch(req);
                    if (fresh.ok) {
                        const cache = await caches.open(CACHE_NAME);
                        cache.put(req, fresh.clone());
                    }
                    return fresh;
                } catch (e) {
                    const cached = await caches.match(req);
                    if (cached) return cached;
                    if (req.mode === 'navigate') {
                        return caches.match('./index.html');
                    }
                    throw e;
                }
            })()
        );
    }
});
