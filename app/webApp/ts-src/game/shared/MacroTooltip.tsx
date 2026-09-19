import { TooltipShell } from "./TooltipShell";

export function MacroTooltip({
  id,
  icon,
  script,
  keybind,
}: {
  id: string;
  icon?: string;
  script?: string;
  keybind?: string;
}) {
  return (
    <TooltipShell>
      <div className="font-bold text-[11px] text-white">
        {icon ?? "⚡"} {id}
      </div>
      {keybind && (
        <div className="text-white/50 text-[9px]">
          <span className="text-white/40">Touche</span> {keybind}
        </div>
      )}
      {script && (
        <div className="text-white/60 text-[9px] whitespace-pre-wrap break-all max-w-[220px] mt-0.5 text-left">
          {script}
        </div>
      )}
    </TooltipShell>
  );
}
