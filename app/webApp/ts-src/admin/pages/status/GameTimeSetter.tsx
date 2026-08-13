import { putApiAdminGametime } from "../../../generated/api/requests";
import type { StatusSnapshot } from "../../apiTypes";
import type { Translate } from "../../i18n";
import { useLayoutEffect, useRef, useState } from "react";
import { ticksToTime } from "./StatusPage";
import { pad2 } from "./utils";

export function GameTimeSetter({ snap, t }: { snap: StatusSnapshot; t: Translate }) {
  const { h, m } = ticksToTime(snap.gameTicks, snap.ticksPerDay || 72000);
  const [time, setTime] = useState(`${pad2(h)}:${pad2(m)}`);
  const [saving, setSaving] = useState(false);
  const prevRef = useRef(`${pad2(h)}:${pad2(m)}`);

  const newHM = ticksToTime(snap.gameTicks, snap.ticksPerDay || 72000);
  const newStr = `${pad2(newHM.h)}:${pad2(newHM.m)}`;
  useLayoutEffect(() => {
    if (newStr !== prevRef.current) {
      prevRef.current = newStr;
      setTime(newStr);
    }
  }, [newStr]);

  const save = async () => {
    const [hh, mm] = time.split(":").map(Number);
    if (isNaN(hh) || isNaN(mm)) return;
    setSaving(true);
    try {
      await putApiAdminGametime({ body: { hour: hh, minute: mm } });
    } catch {
      /* empty */
    }
    setSaving(false);
  };

  return (
    <div className="flex items-center gap-2 mt-2">
      <input
        type="time"
        value={time}
        onChange={(e) => setTime(e.target.value)}
        className="bg-[#0E1726] border border-[#2E3A4E] rounded-lg px-2 py-1 text-sm text-white focus:outline-none focus:border-[#3C50E0] transition-colors tabular-nums"
      />
      <button
        onClick={save}
        disabled={saving}
        className="px-3 py-1 rounded-lg text-xs font-medium bg-[#3C50E0] hover:bg-[#3446c7] text-white transition-colors disabled:opacity-50"
      >
        {saving ? "…" : t("status.set")}
      </button>
    </div>
  );
}
