export function Btn({
  children,
  onClick,
  disabled,
  variant = "primary",
}: {
  children: React.ReactNode;
  onClick?: () => void;
  disabled?: boolean;
  variant?: "primary" | "ghost" | "danger";
}) {
  const s = {
    primary: "bg-[#3C50E0] hover:bg-[#3446c7] text-white",
    ghost: "bg-transparent border border-[#2E3A4E] text-[#8A99AF] hover:bg-[#2E3A4E] hover:text-white",
    danger: "bg-red-600/10 border border-red-600/30 text-red-400 hover:bg-red-600/20",
  }[variant];
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      className={`px-3 py-1.5 rounded-lg text-sm font-medium transition-colors disabled:opacity-50 ${s}`}
    >
      {children}
    </button>
  );
}
