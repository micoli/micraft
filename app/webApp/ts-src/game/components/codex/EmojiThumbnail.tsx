export function EmojiThumbnail({ emoji }: { emoji: string }) {
  return (
    <div
      style={{
        width: 52,
        height: 52,
        background: "#2a2a2a",
        borderRadius: 8,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        fontSize: 28,
        border: "1px solid #444",
      }}
    >
      {emoji}
    </div>
  );
}
