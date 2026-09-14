import { UsePreferences } from "../../hooks/usePreferences";

const TURN_SPEED_OPTIONS = [0.8, 1.0, 1.2, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0];

export function GameTab({ pref }: { pref: UsePreferences }) {
  return (
    <>
      {[
        {
          state: pref.localContinuousBreak,
          setter: pref.setLocalContinuousBreak,
          label: "Continuous block breaking (hold to mine multiple blocks)",
        },
      ].map(({ state, setter, label }) => (
        <div key={label} className="flex items-center gap-2 py-1.5 border-b border-[#2a2a2a]">
          <input type="checkbox" checked={state} onChange={(e) => setter(e.target.checked)} />
          <span>{label}</span>
        </div>
      ))}
      {(
        [
          ["Horizontal rotation speed (keyboard)", pref.localTurnSpeedHorizontal, pref.setLocalTurnSpeedHorizontal],
          ["Vertical rotation speed (keyboard)", pref.localTurnSpeedVertical, pref.setLocalTurnSpeedVertical],
        ] as const
      ).map(([label, value, setter]) => (
        <div key={label} className="flex items-center gap-2 py-1.5 border-b border-[#2a2a2a]">
          <span className="flex-1">{label}</span>
          <select value={value} onChange={(e) => setter(Number(e.target.value))}>
            {(TURN_SPEED_OPTIONS.includes(value)
              ? TURN_SPEED_OPTIONS
              : [...TURN_SPEED_OPTIONS, value].sort((a, b) => a - b)
            ).map((s) => (
              <option key={s} value={s}>
                {s.toFixed(1)}×
              </option>
            ))}
          </select>
        </div>
      ))}
      <div className="flex items-center gap-2 py-1.5 border-b border-[#2a2a2a]">
        <span className="flex-1">Dominant hand</span>
        <select
          value={pref.localDominantHand}
          onChange={(e) => pref.setLocalDominantHand(e.target.value as "LEFT" | "RIGHT")}
        >
          <option value="RIGHT">Right-handed</option>
          <option value="LEFT">Left-handed</option>
        </select>
      </div>
      <div className="py-1.5 border-b border-[#2a2a2a]">
        <div className="mb-1">Available view modes (the view key cycles through the enabled ones)</div>
        {(
          [
            ["FIRST_PERSON", "First person"],
            ["THIRD_PERSON", "Third person"],
            ["FIRST_PERSON_NO_ARMS", "First person (no arms)"],
            ["THIRD_PERSON_ORBIT", "Third person orbit"],
            ["THIRD_PERSON_ORBIT_CURSOR", "Third person orbit (cursor)"],
          ] as const
        ).map(([id, label]) => {
          const locked = id === "FIRST_PERSON";
          const enabled = locked || !pref.localDisabledViewModes.includes(id);
          return (
            <label key={id} className="flex items-center gap-2 py-0.5">
              <input
                type="checkbox"
                checked={enabled}
                disabled={locked}
                onChange={(e) =>
                  pref.setLocalDisabledViewModes(
                    e.target.checked
                      ? pref.localDisabledViewModes.filter((m) => m !== id)
                      : [...pref.localDisabledViewModes, id],
                  )
                }
              />
              <span>{label}</span>
            </label>
          );
        })}
      </div>
    </>
  );
}
