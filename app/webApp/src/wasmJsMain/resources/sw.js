const CACHE_V2 = 'micraft-v2';
const PASSTHROUGH_PREFIXES = ['/api/', '/auth/', '/ws', '/chunks'];
const CDN_ORIGINS = ['cdn.babylonjs.com'];
const MANIFEST_URL = '/api/assets/manifest';

let manifest = {};

async function preCacheAll(m) {
  const cache = await caches.open(CACHE_V2);
  await Promise.all(
    Object.entries(m).map(async ([filename, hash]) => {
      const key = `/${filename}?_md5=${hash}`;
      if (await cache.match(key)) return;
      try {
        const response = await fetch(`/${filename}`, { cache: 'no-cache' });
        if (response.ok) await cache.put(key, response);
      } catch {
        /* offline — skip, will fetch on demand */
      }
    })
  );
}

self.addEventListener('install', (event) => {
  event.waitUntil(
    fetch(MANIFEST_URL, { cache: 'no-cache' })
      .then((r) => r.json())
      .then((m) => {
        manifest = m;
        return preCacheAll(m);
      })
      .then(() => self.skipWaiting())
      .catch(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) =>
        Promise.all(keys.filter((k) => k !== CACHE_V2).map((k) => caches.delete(k)))
      )
      .then(() => self.clients.claim())
  );
});

async function serveFromManifest(filename, hash, url) {
  const cache = await caches.open(CACHE_V2);
  const key = `/${filename}?_md5=${hash}`;
  const cached = await cache.match(key);
  if (cached) return cached;
  const response = await fetch(url, { cache: 'no-cache' });
  if (response.ok) await cache.put(key, response.clone());
  return response;
}

async function cacheThenNetwork(request) {
  const cache = await caches.open(CACHE_V2);
  const cached = await cache.match(request);
  if (cached) return cached;
  const response = await fetch(request);
  if (response.ok) await cache.put(request, response.clone());
  return response;
}

async function networkFirst(request) {
  try {
    const response = await fetch(request);
    if (response.ok) {
      const cache = await caches.open(CACHE_V2);
      cache.put(request, response.clone());
    }
    return response;
  } catch {
    const cached = await caches.match(request);
    if (cached) return cached;
    throw new Error('offline and no cache for ' + request.url);
  }
}

self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url);

  if (
    event.request.headers.get('upgrade') === 'websocket' ||
    PASSTHROUGH_PREFIXES.some((p) => url.pathname.startsWith(p))
  ) {
    return;
  }

  if (CDN_ORIGINS.includes(url.hostname)) {
    event.respondWith(cacheThenNetwork(event.request));
    return;
  }

  const filename = url.pathname.slice(1);
  const hash = manifest[filename];
  if (hash) {
    event.respondWith(serveFromManifest(filename, hash, event.request.url));
    return;
  }

  event.respondWith(networkFirst(event.request));
});

// Asset version changes are pushed by the server via /ws (AssetNotifyController).
// The SW's role is caching by MD5 hash — no polling needed.
