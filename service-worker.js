// Tombstone service worker.
// Replaces the old Air Touch service worker that used to live at this scope.
// Takes control of existing clients, purges every cache, navigates them away
// with a cache-busting query so the browser HTTP cache is bypassed, then
// unregisters itself.

self.addEventListener('install', (event) => {
    self.skipWaiting();
});

self.addEventListener('activate', (event) => {
    event.waitUntil((async () => {
        try { await self.clients.claim(); } catch (_) {}

        const keys = await caches.keys();
        await Promise.all(keys.map((k) => caches.delete(k)));

        const clients = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
        clients.forEach((client) => {
            try {
                const u = new URL(client.url);
                u.searchParams.set('_fresh', Date.now().toString());
                client.navigate(u.toString());
            } catch (_) {}
        });

        try { await self.registration.unregister(); } catch (_) {}
    })());
});

// Bypass the browser HTTP cache for every controlled fetch — any stale Air
// Touch assets the browser had cached at this scope get superseded by fresh
// network responses (i.e. the new player profile site).
self.addEventListener('fetch', (event) => {
    if (event.request.method !== 'GET') return;
    event.respondWith((async () => {
        try {
            return await fetch(event.request, { cache: 'reload' });
        } catch (_) {
            return fetch(event.request);
        }
    })());
});
