import { useEffect, useRef, useState } from "react";

// PUT /blocks resolves normally even on a 4xx (fetch only rejects on network errors), so a
// rejected placement/break (e.g. slot full, zone disabled, out of bounds) would otherwise fail
// silently — the ghost just vanishes with no feedback. Surfaces the server's rejection reason
// briefly instead. Shared by the Instance and Scene editors.
export function useActionError() {
  const [actionError, setActionError] = useState<string | null>(null);
  const timeout = useRef<ReturnType<typeof setTimeout> | null>(null);

  function flashActionError(message: string) {
    setActionError(message);
    if (timeout.current) clearTimeout(timeout.current);
    timeout.current = setTimeout(() => setActionError(null), 4000);
  }

  useEffect(() => {
    return () => {
      if (timeout.current) clearTimeout(timeout.current);
    };
  }, []);

  return { actionError, flashActionError };
}
