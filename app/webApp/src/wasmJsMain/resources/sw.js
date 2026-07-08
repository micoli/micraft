const CACHE_VERSION = 'micraft-v2';
const PRECACHE_ASSETS = ['/', '/main.css', '/mc_bindings.js', '/webApp.js', '/favicon.svg'];
const PASSTHROUGH_PREFIXES = ['/api/', '/auth/', '/ws', '/chunks'];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_VERSION).then((cache) => cache.addAll(PRECACHE_ASSETS))
  );
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((k) => k !== CACHE_VERSION).map((k) => caches.delete(k)))
    )
  );
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

  // Cache-first for same-origin static assets + CDN babylon
  event.respondWith(
    caches.match(event.request).then((cached) => {
      if (cached) return cached;
      return fetch(event.request).then((response) => {
        if (response.ok) {
          const clone = response.clone();
          caches.open(CACHE_VERSION).then((cache) => cache.put(event.request, clone));
        }
        return response;
      });
    })
  );
});
