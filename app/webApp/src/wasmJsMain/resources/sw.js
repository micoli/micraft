// Navigation-fallback service worker.
// Only job: when the user reloads the page while the server is down, serve the
// pre-cached maintenance page instead of the browser's native error screen.
// It never caches app assets (WASM / JS / API / WebSocket are always network).

const CACHE = "micraft-shell-v1";
const FALLBACK = "/maintenance.html";

self.addEventListener("install", (e) => {
  e.waitUntil(
    caches.open(CACHE).then((c) => c.add(FALLBACK)).then(() => self.skipWaiting())
  );
});

self.addEventListener("activate", (e) => {
  e.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", (e) => {
  const req = e.request;
  if (req.mode !== "navigate") return;

  e.respondWith(
    fetch(req)
      .then((res) => {
        if (res.ok || (res.status >= 200 && res.status < 400)) return res;
        throw new Error("server " + res.status);
      })
      .catch(() => caches.match(FALLBACK, { cacheName: CACHE }))
  );
});

// Let the maintenance page ask for a fresh copy of itself after a deploy.
self.addEventListener("message", (e) => {
  if (e.data === "refresh-shell") {
    caches.open(CACHE).then((c) => c.add(FALLBACK));
  }
});
