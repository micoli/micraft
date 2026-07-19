// Self-unregistering service worker — clears all caches and removes itself.
// Deployed after the old caching SW was removed, to force clients to a clean state.
self.addEventListener("install", () => self.skipWaiting());
self.addEventListener("activate", (e) => {
  e.waitUntil(
    caches.keys().then((keys) => Promise.all(keys.map((k) => caches.delete(k))))
      .then(() => self.registration.unregister())
  );
});
