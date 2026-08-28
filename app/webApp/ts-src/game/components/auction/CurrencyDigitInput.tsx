import { NumberInput } from "../../../primitives/NumberInput";

interface Props {
  value: string;
  onChange: (value: string) => void;
  onStep: (delta: number) => void;
  color: string;
  width?: number;
}

const noSpinnerCls =
  "[&::-webkit-outer-spin-button]:appearance-none [&::-webkit-inner-spin-button]:appearance-none [-moz-appearance:textfield]";

export function CurrencyDigitInput({ value, onChange, onStep, color, width = 96 }: Props) {
  return (
    <div style={{ position: "relative", width }}>
      <NumberInput
        placeholder="0"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === "ArrowUp") {
            e.preventDefault();
            onStep(1);
          } else if (e.key === "ArrowDown") {
            e.preventDefault();
            onStep(-1);
          }
        }}
        style={{ width: "100%", color, textAlign: "right", paddingRight: 22 }}
        className={noSpinnerCls}
      />
      <div style={{ position: "absolute", right: 6, top: "50%", transform: "translateY(-50%)" }}>
        <div style={{ display: "flex", flexDirection: "column", gap: 2 }}>
          <button
            type="button"
            onClick={() => onStep(1)}
            style={{ background: "none", border: "none", padding: 0, cursor: "pointer", lineHeight: 0 }}
          >
            <svg width="8" height="6" viewBox="0 0 8 6">
              <path d="M4 0 L8 6 L0 6 Z" fill="rgba(255,255,255,0.5)" />
            </svg>
          </button>
          <button
            type="button"
            onClick={() => onStep(-1)}
            style={{ background: "none", border: "none", padding: 0, cursor: "pointer", lineHeight: 0 }}
          >
            <svg width="8" height="6" viewBox="0 0 8 6">
              <path d="M0 0 L8 0 L4 6 Z" fill="rgba(255,255,255,0.5)" />
            </svg>
          </button>
        </div>
      </div>
    </div>
  );
}
