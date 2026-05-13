// Tombstone service worker.
// The old Air Touch app used to live at this scope (/lielisraeli1/) and
// registered a service worker that cached its assets. The Sagi Israeli
// profile now occupies this scope. This SW exists only to unregister itself,
// purge any leftover caches, and reload controlled clients so visitors get
// the new site instead of the stale Air Touch HTML.

self.addEventListener('install', () => {
    self.skipWaiting();
});

self.addEventListener('activate', (event) => {
    event.waitUntil((async () => {
        const keys = await caches.keys();
        await Promise.all(keys.map((k) => caches.delete(k)));

        await self.registration.unregister();

        const clients = await self.clients.matchAll({ type: 'window' });
        clients.forEach((client) => {
            try { client.navigate(client.url); } catch (_) {}
        });
    })());
});

self.addEventListener('fetch', (event) => {
    event.respondWith(fetch(event.request));
});
