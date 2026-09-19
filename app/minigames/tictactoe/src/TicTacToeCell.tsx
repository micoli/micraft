import { CellValue } from "./ticTacToeLogic";

interface Props {
  value: CellValue;
  disabled: boolean;
  onClick: () => void;
}

export function TicTacToeCell({ value, disabled, onClick }: Props) {
  return (
    <button
      type="button"
      disabled={disabled || value !== null}
      onClick={onClick}
      style={{
        width: 72,
        height: 72,
        fontSize: 32,
        fontFamily: "monospace",
        fontWeight: "bold",
        color: value === "X" ? "#4da3ff" : "#ff5c5c",
        background: "rgba(255,255,255,0.06)",
        border: "1px solid rgba(255,255,255,0.25)",
        borderRadius: 6,
        cursor: disabled || value !== null ? "default" : "pointer",
      }}
    >
      {value ?? ""}
    </button>
  );
}
