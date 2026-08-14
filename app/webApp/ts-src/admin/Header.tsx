import { useLocation } from "react-router";
import { useT } from "./i18n";
import { NAV } from "./const";

export function Header() {
  const { pathname } = useLocation();
  const t = useT();
  const labelKey =
    NAV.find((item) => item.path === pathname)?.pageLabelKey ??
    NAV.find((item) => item.path !== "/admin" && pathname.startsWith(item.path))?.pageLabelKey;
  const title = labelKey ? t(labelKey) : t("shell.admin");
  return (
    <header className="h-16 shrink-0 flex items-center justify-between px-6 bg-[#1A222C] border-b border-[#2E3A4E]">
      <div>
        <p className="text-[11px] text-[#8A99AF]">
          {t("shell.admin")} / {title}
        </p>
        <h1 className="text-white font-semibold text-[15px] leading-tight">{title}</h1>
      </div>
      <div className="flex items-center gap-2 text-[11px] text-[#8A99AF]">
        <span className="w-2 h-2 rounded-full bg-emerald-400 inline-block" />
        {t("shell.serverOnline")}
      </div>
    </header>
  );
}
