import { cn } from "../../primitives/cn";
import { useServerLog } from "../hooks/useServerLog";
import { ChannelSubscription, LogEntry } from "../types";

function escapeHtml(s: string): string {
  return s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

const CHANNEL_COLORS: Record<string, string> = {
  system: "#aaa",
  game: "#f5c542",
  world: "#fff",
  around: "#90e0b0",
};

function channelColor(ch: string): string {
  if (ch.startsWith("dm:")) return "#7dd3fc";
  return CHANNEL_COLORS[ch] ?? "#c084fc";
}

interface Props {
  logs: LogEntry[];
  visible: boolean;
  subscribedChannels: ChannelSubscription[];
  activeChannel: string;
  unreadChannels: string[];
  onChannelSelect: (channel: string) => void;
  layoutStyle?: React.CSSProperties;
}

export function ServerLog({
  logs,
  visible,
  subscribedChannels,
  activeChannel,
  unreadChannels,
  onChannelSelect,
  layoutStyle,
}: Props) {
  const { scrollRef, filtered, onScroll } = useServerLog({ logs, visible, activeChannel });

  if (!visible) return null;

  return (
    <div
      className={cn(
        "flex flex-col max-h-full bg-black/55 rounded-t z-[1002] box-border",
        !layoutStyle && "fixed bottom-[94px] left-1/2 -translate-x-1/2 w-[60%] max-h-[200px]",
      )}
      style={layoutStyle}
    >
      <div className="flex flex-shrink-0 overflow-x-auto border-b border-white/15">
        {subscribedChannels.map((ch) => (
          <button
            key={ch.name}
            onMouseDown={(e) => {
              e.preventDefault();
              onChannelSelect(ch.name);
            }}
            className={cn(
              "px-2 py-0.5 border-none border-r border-white/10 font-mono text-[11px] cursor-pointer whitespace-nowrap transition-colors",
              ch.name === activeChannel ? "bg-white/15" : "bg-transparent hover:bg-white/5",
            )}
            style={{ color: channelColor(ch.name) }}
          >
            {unreadChannels.includes(ch.name) ? `* ${ch.name}` : ch.name}
          </button>
        ))}
      </div>
      <div className="flex-1 overflow-y-auto px-2 py-1 min-h-0" ref={scrollRef} onScroll={onScroll}>
        {filtered.length === 0 && (
          <div className="text-white/30 font-mono text-xs italic leading-relaxed">No messages</div>
        )}
        {filtered.map((entry, i) => (
          <div key={i} className="text-white/85 font-mono text-xs leading-relaxed">
            <span className="text-white/50">[{entry.time}]</span>{" "}
            {entry.sender && (
              <span className="font-bold" style={{ color: channelColor(entry.channel) }}>
                {escapeHtml(entry.sender)}:{" "}
              </span>
            )}
            <span dangerouslySetInnerHTML={{ __html: escapeHtml(entry.msg) }} />
          </div>
        ))}
      </div>
    </div>
  );
}
