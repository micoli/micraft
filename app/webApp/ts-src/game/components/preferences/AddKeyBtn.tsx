export function AddKeyBtn({ onClick }: { onClick: () => void }) {
  return (
    <button
      className="bg-transparent border border-dashed border-[#555] rounded-sm text-[#888] cursor-pointer text-[11px] px-1.5 py-0.5 hover:border-[#888]"
      onClick={onClick}
    >
      +
    </button>
  );
}
