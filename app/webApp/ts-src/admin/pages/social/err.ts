/** Best-effort human message from whatever a generated request throws on error. */
export function errorText(e: unknown): string {
  if (typeof e === "string") return e;
  if (e instanceof Error) return e.message;
  if (e && typeof e === "object") {
    const o = e as Record<string, unknown>;
    for (const key of ["error", "message", "detail", "body"]) {
      const v = o[key];
      if (typeof v === "string" && v) return v;
      if (v instanceof Error) return v.message;
    }
  }
  return "Failed";
}
