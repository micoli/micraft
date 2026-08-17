import type { ReactNode } from "react";

interface Props {
  preview: ReactNode;
  title: string;
  titleFontSize?: number;
  children?: ReactNode;
}

export function CodexDetailLayout({ preview, title, titleFontSize = 15, children }: Props) {
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 12, padding: 8 }}>
      <div style={{ display: "flex", justifyContent: "center" }}>{preview}</div>
      <div style={{ fontSize: titleFontSize, fontWeight: "bold", color: "#eee", textAlign: "center" }}>{title}</div>
      {children}
    </div>
  );
}
