const CACHE_V2 = 'micraft-v2';
const PASSTHROUGH_PREFIXES = ['/api/', '/auth/', '/ws', '/chunks'];
const CDN_ORIGINS = ['cdn.babylonjs.com'];
const MANIFEST_URL = '/api/assets/manifest';
const MANIFEST_KEY = '/__installed_manifest__';

// In-memory copy of the manifest the SW booted with. May be empty after the browser
// terminates the worker — getInstalledManifest() lazily reloads it from the cache.
let manifest = null;

function signatureOf(m) {
  return Object.entries(m)
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([k, v]) => `${k}:${v}`)
    .join(',');
}

async function loadInstalledManifest() {
  const cache = await caches.open(CACHE_V2);
  const cached = await cache.match(MANIFEST_KEY);
  if (!cached) return {};
  try {
    return await cached.json();
  } catch {
    return {};
  }
}

async function getInstalledManifest() {
  if (manifest === null) {
    manifest = await loadInstalledManifest();
  }
  return manifest;
}

async function persistManifest(m) {
  manifest = m;
  const cache = await caches.open(CACHE_V2);
  await cache.put(MANIFEST_KEY, new Response(JSON.stringify(m), { headers: { 'Content-Type': 'application/json' } }));
}

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

// Drop cached asset entries whose md5 key is no longer referenced by the manifest.
async function pruneStale(m) {
  const cache = await caches.open(CACHE_V2);
  const valid = new Set(Object.entries(m).map(([filename, hash]) => `/${filename}?_md5=${hash}`));
  const requests = await cache.keys();
  await Promise.all(
    requests.map(async (req) => {
      const url = new URL(req.url);
      const path = url.pathname + url.search;
      if (path === MANIFEST_KEY) return;
      if (url.search.startsWith('?_md5=') && !valid.has(path)) {
        await cache.delete(req);
      }
    })
  );
}

// Refetch the manifest, cache new assets, prune stale ones, then tell clients to reload.
async function updateAssets() {
  const response = await fetch(MANIFEST_URL, { cache: 'no-cache' });
  const m = await response.json();
  await preCacheAll(m);
  await persistManifest(m);
  await pruneStale(m);
  const clients = await self.clients.matchAll();
  clients.forEach((client) => client.postMessage({ type: 'ASSETS_UPDATED' }));
}

self.addEventListener('install', (event) => {
  event.waitUntil(
    fetch(MANIFEST_URL, { cache: 'no-cache' })
      .then((r) => r.json())
      .then((m) => preCacheAll(m).then(() => persistManifest(m)))
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

// The page relays the server-published signature; the SW is the comparator.
self.addEventListener('message', (event) => {
  const data = event.data || {};
  if (data.type === 'CHECK_VERSION') {
    event.waitUntil(
      getInstalledManifest().then((m) => {
        if (signatureOf(m) !== data.sig) return updateAssets();
      })
    );
  } else if (data.type === 'FORCE_UPDATE') {
    event.waitUntil(updateAssets());
  }
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

async function handleFetch(request) {
  const url = new URL(request.url);
  const filename = url.pathname.slice(1);
  const m = await getInstalledManifest();
  const hash = m[filename];
  if (hash) return serveFromManifest(filename, hash, request.url);
  return networkFirst(request);
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

  event.respondWith(handleFetch(event.request));
});

// Asset version changes are published by the server via /ws (AssetNotifyController);
// the page relays the signature here and this SW compares + refreshes the cache.
