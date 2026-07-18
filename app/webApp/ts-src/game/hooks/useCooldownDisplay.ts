import { useEffect, useRef, useState } from "react";

export function useCooldownDisplay(serverRemainingMs: number): number {
  const [display, setDisplay] = useState(serverRemainingMs);
  const currentRef = useRef(serverRemainingMs);
  const lastTimeRef = useRef(0);
  const ivRef = useRef<ReturnType<typeof setInterval> | undefined>(undefined);

  useEffect(() => {
    if (serverRemainingMs > currentRef.current + 50) {
      clearInterval(ivRef.current);
      currentRef.current = serverRemainingMs;
      lastTimeRef.current = performance.now();
      setDisplay(serverRemainingMs);

      ivRef.current = setInterval(() => {
        const now = performance.now();
        const delta = now - lastTimeRef.current;
        lastTimeRef.current = now;
        currentRef.current = Math.max(0, currentRef.current - delta);
        setDisplay(currentRef.current);
        if (currentRef.current <= 0) clearInterval(ivRef.current);
      }, 200);
    }
    return () => clearInterval(ivRef.current);
  }, [serverRemainingMs]);

  return display;
}
