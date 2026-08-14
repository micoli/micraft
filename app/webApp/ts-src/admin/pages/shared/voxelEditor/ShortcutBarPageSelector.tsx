import type { useAdminShortcutBar } from "./useAdminShortcutBar";

export function ShortcutBarPageSelector({ shortcutBar }: { shortcutBar: ReturnType<typeof useAdminShortcutBar> }) {
  return (
    <div className="flex gap-1 justify-center mt-1.5">
      {Array.from({ length: shortcutBar.pageCount }, (index, p) => (
        <button
          key={p}
          onClick={() => shortcutBar.goToPage(p)}
          className={`w-4.5 h-4.5 text-xs rounded ${p === shortcutBar.currentPage ? "bg-[#3C50E0]" : "bg-white/25"}`}
        >
          {p + 1}
        </button>
      ))}
    </div>
  );
}
