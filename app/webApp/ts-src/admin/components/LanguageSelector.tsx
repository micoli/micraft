import { cn } from "../../primitives/cn";
import { LOCALE_LABELS, LOCALES, useI18n, type Locale } from "../i18n";

/**
 * Language picker at the foot of the nav.
 *
 * A plain `<select>` rather than a row of flags: locales are a list that will grow, and the native
 * control stays keyboard- and screen-reader-usable at both sidebar widths. Collapsed, the label goes
 * and the control shrinks to the locale code — the same trade the nav links make.
 */
export function LanguageSelector({ collapsed }: { collapsed: boolean }) {
  const { locale, setLocale, t } = useI18n();

  return (
    <div
      className={cn(
        "border-t border-[#2E3A4E] flex items-center gap-2 py-2.5 text-[10px] text-[#8A99AF]",
        collapsed ? "px-2 justify-center" : "px-6",
      )}
    >
      {!collapsed && (
        <label htmlFor="admin-locale" className="flex-1">
          {t("shell.language")}
        </label>
      )}
      <select
        id="admin-locale"
        value={locale}
        title={t("shell.language")}
        aria-label={t("shell.language")}
        onChange={(event) => setLocale(event.target.value as Locale)}
        className={cn(
          "rounded border border-[#2E3A4E] bg-[#0E1726] py-1 text-[11px] text-white hover:border-[#3C50E0]",
          collapsed ? "w-full px-1 text-center" : "px-2",
        )}
      >
        {LOCALES.map((option) => (
          <option key={option} value={option}>
            {collapsed ? option.toUpperCase() : LOCALE_LABELS[option]}
          </option>
        ))}
      </select>
    </div>
  );
}
