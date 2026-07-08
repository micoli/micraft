const CACHE_NAME = 'micraft-shell';
const PASSTHROUGH_PREFIXES = ['/api/', '/auth/', '/ws', '/chunks'];
const CDN_ORIGINS = ['cdn.babylonjs.com'];

self.addEventListener('install', (event) => {
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  self.clients.claim();
});

self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url);

  // WebSocket upgrades and API calls: always network, never cache
  if (
    event.request.headers.get('upgrade') === 'websocket' ||
    PASSTHROUGH_PREFIXES.some((p) => url.pathname.startsWith(p))
  ) {
    return;
  }

  // CDN assets (babylon.js): cache-first — never changes in a given session
  if (CDN_ORIGINS.includes(url.hostname)) {
    event.respondWith(
      caches.match(event.request).then((cached) => {
        if (cached) return cached;
        return fetch(event.request).then((response) => {
          if (response.ok) {
            const clone = response.clone();
            caches.open(CACHE_NAME).then((cache) => cache.put(event.request, clone));
          }
          return response;
        });
      })
    );
    return;
  }

  // Same-origin assets: network-first, fall back to cache when server is down
  event.respondWith(
    fetch(event.request)
      .then((response) => {
        if (response.ok) {
          const clone = response.clone();
          caches.open(CACHE_NAME).then((cache) => cache.put(event.request, clone));
        }
        return response;
      })
      .catch(() => caches.match(event.request))
  );
});
