import type { CSSProperties } from "react";

export const filterWrapperStyle: CSSProperties = {
  padding: "8px 12px 4px",
  flexShrink: 0,
};

export const filterInputStyle: CSSProperties = {
  width: "100%",
  boxSizing: "border-box",
  background: "#1e1e1e",
  border: "1px solid #3a3a3a",
  borderRadius: 5,
  color: "#ddd",
  fontFamily: "monospace",
  fontSize: 12,
  padding: "5px 10px",
  outline: "none",
};

export const gridStyle: CSSProperties = {
  flex: 1,
  overflowY: "auto",
  padding: "8px 12px 12px",
  display: "flex",
  flexWrap: "wrap",
  alignContent: "flex-start",
  gap: 4,
};
