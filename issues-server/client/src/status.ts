const KNOWN_STATUSES = new Set([
  "needs-triage",
  "needs-info",
  "ready-for-agent",
  "ready-for-human",
  "wontfix",
  "resolved",
  "claimed",
  "accepted",
  "superseded",
]);

export function statusClass(status: string): string {
  const normalized = status.toLowerCase();
  return KNOWN_STATUSES.has(normalized) ? `status status-${normalized}` : "status";
}
