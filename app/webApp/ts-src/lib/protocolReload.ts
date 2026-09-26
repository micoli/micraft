const RELOAD_AT_KEY = "micraft_protocol_reload_at";

// A reload that did not cure the mismatch means the served bundle itself is out of step with the
// server (e.g. a deploy in progress): past this window, stop reloading and tell the player instead.
const MIN_RELOAD_INTERVAL_MS = 60_000;

export function shouldReloadForProtocolMismatch(lastReloadAt: number | null, now: number): boolean {
  return lastReloadAt === null || now - lastReloadAt >= MIN_RELOAD_INTERVAL_MS;
}

/**
 * The server speaks another protocol version (PROTOCOL_FINGERPRINT differs): reload to fetch the
 * matching client, at most once per MIN_RELOAD_INTERVAL_MS. Returns false when the reload was
 * suppressed to avoid a loop.
 */
export function reloadForProtocolMismatch(
  storage: Pick<Storage, "getItem" | "setItem"> = sessionStorage,
  reload: () => void = () => window.location.reload(),
  now: number = Date.now(),
): boolean {
  const raw = storage.getItem(RELOAD_AT_KEY);
  if (!shouldReloadForProtocolMismatch(raw === null ? null : Number(raw), now)) return false;
  storage.setItem(RELOAD_AT_KEY, String(now));
  reload();
  return true;
}
