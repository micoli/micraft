import { useEffect, useRef } from "react";
import { LogEntry } from "../types";

export function useServerLog({
  logs,
  visible,
  activeChannel,
}: {
  logs: LogEntry[];
  visible: boolean;
  activeChannel: string;
}) {
  const scrollRef = useRef<HTMLDivElement>(null);
  const userScrolledRef = useRef(false);

  const filtered = logs.filter((e) => e.channel === activeChannel);

  useEffect(() => {
    const el = scrollRef.current;
    if (!el || userScrolledRef.current) return;
    el.scrollTop = el.scrollHeight;
  }, [filtered]);

  useEffect(() => {
    if (visible) userScrolledRef.current = false;
  }, [visible]);

  function onScroll() {
    const el = scrollRef.current;
    if (!el) return;
    const atBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 4;
    userScrolledRef.current = !atBottom;
  }

  return { scrollRef, filtered, onScroll };
}
