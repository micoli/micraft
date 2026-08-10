const SIDEBAR_STORAGE_KEY = "micraft-admin-sidebar-collapsed";

/**
 * Whether the admin nav is collapsed to icons.
 *
 * Persisted, like the simulator's layer and renderer choices: the sidebar is chrome, and an operator
 * who reclaimed the width for a wide page (configEditor editor, world simulator) should not have to reclaim
 * it again on every navigation. Unreadable storage falls back to expanded — the labelled nav is the
 * discoverable state, so it is the safe default.
 */
export function loadSidebarCollapsed(): boolean {
  try {
    return localStorage.getItem(SIDEBAR_STORAGE_KEY) === "1";
  } catch {
    return false;
  }
}

export function saveSidebarCollapsed(collapsed: boolean) {
  try {
    localStorage.setItem(SIDEBAR_STORAGE_KEY, collapsed ? "1" : "0");
  } catch {
    /* storage unavailable — the choice stays session-only */
  }
}
