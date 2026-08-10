export function Toggle({ value, onChange }: { value: boolean; onChange: (v: boolean) => void }) {
  return (
    <button
      onClick={() => onChange(!value)}
      className={`w-9 h-[14px] rounded-full transition-colors relative shrink-0 overflow-visible ${value ? "bg-[#3C50E0]" : "bg-[#2E3A4E]"}`}
    >
      <span
        className={`absolute top-1/2 -translate-y-1/2 left-0 w-[18px] h-[18px] rounded-full bg-white shadow-sm transition-transform ${value ? "translate-x-[20px]" : "translate-x-0.5"}`}
      />
    </button>
  );
}
