import { MetricsPanelProps } from "./MetricsPanel";
import { useT } from "../../i18n";
import { useEffect, useRef, useState } from "react";
import { buildMetricsExport } from "./metrics";

/** How long the button keeps saying it copied. */
const COPIED_FEEDBACK_MS = 2_000;

/**
 * Hand the whole history to the clipboard as JSON, one click.
 *
 * The clipboard rather than a download, because the point is to paste the run into a conversation and
 * ask why the wolves ate everything. A download is the fallback: writing the clipboard needs a secure
 * origin and a permission, and neither is guaranteed for an admin page served over plain HTTP.
 */
export function ExportButton({ buckets, bucketGameDays, config }: MetricsPanelProps) {
  const t = useT();
  const [copied, setCopied] = useState(false);
  const timerRef = useRef<number | null>(null);

  useEffect(
    () => () => {
      if (timerRef.current !== null) window.clearTimeout(timerRef.current);
    },
    [],
  );

  const flashCopied = () => {
    setCopied(true);
    if (timerRef.current !== null) window.clearTimeout(timerRef.current);
    timerRef.current = window.setTimeout(() => setCopied(false), COPIED_FEEDBACK_MS);
  };

  const download = (json: string) => {
    const url = URL.createObjectURL(new Blob([json], { type: "application/json" }));
    const link = document.createElement("a");
    link.href = url;
    link.download = "micraft-simulation-metrics.json";
    link.click();
    URL.revokeObjectURL(url);
  };

  const exportAll = () => {
    const json = JSON.stringify(buildMetricsExport(buckets, bucketGameDays, config), null, 2);
    const clipboard = navigator.clipboard;
    if (!clipboard) {
      download(json);
      return;
    }
    clipboard.writeText(json).then(flashCopied, () => download(json));
  };

  return (
    <button
      type="button"
      onClick={exportAll}
      disabled={buckets.length === 0}
      title={t("sim.metrics.exportTitle")}
      className={
        "rounded px-1.5 py-0.5 text-[10px] disabled:opacity-40 " +
        (copied ? "bg-emerald-500/80 text-white" : "bg-[#2E3A4E] text-[#C7D2FE] hover:bg-[#3C50E0]/60")
      }
    >
      {t(copied ? "sim.metrics.exportCopied" : "sim.metrics.export")}
    </button>
  );
}
