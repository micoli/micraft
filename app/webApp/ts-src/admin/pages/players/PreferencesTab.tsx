import { getApiSkins } from "../../../generated/api/requests";
import { PlayerFile } from "../../apiTypes";
import { useT } from "../../i18n";
import { Field } from "../../../primitives/Field";
import { useEffect, useState } from "react";
import { BoolRow } from "../../../primitives/BoolRow";
import { SaveButton } from "../../../primitives/SaveButton";

const KNOWN_LOCALES = ["en", "fr", "de", "es", "ja", "zh", "pt", "ru", "it", "nl"];

export function PreferencesTab({
  file,
  onSave,
}: {
  file: PlayerFile;
  onSave: (prefs: Partial<PlayerFile["state"]>) => Promise<void>;
}) {
  const t = useT();
  const s = file.state;
  const [skin, setSkin] = useState(s.skin);
  const [language, setLanguage] = useState(s.language);
  const [fov, setFov] = useState(s.fieldOfView);
  const [shaders, setShaders] = useState(s.shadersEnabled);
  const [favicon, setFavicon] = useState(s.animatedFavicon);
  const [godMode, setGodMode] = useState(s.godMode);
  const [lightBoost, setLightBoost] = useState(s.lightBoostEnabled);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [availableSkins, setAvailableSkins] = useState<string[]>([]);

  useEffect(() => {
    getApiSkins({ throwOnError: true })
      .then((r) => setAvailableSkins(r.data))
      .catch(() => {});
  }, []);

  const skinOptions = Array.from(new Set([...availableSkins, skin])).sort();
  const langOptions = Array.from(new Set([...KNOWN_LOCALES, language])).sort();

  const save = async () => {
    setSaving(true);
    await onSave({
      skin,
      language,
      fieldOfView: fov,
      shadersEnabled: shaders,
      animatedFavicon: favicon,
      godMode,
      lightBoostEnabled: lightBoost,
    });
    setSaving(false);
    setSaved(true);
    setTimeout(() => setSaved(false), 1500);
  };

  const selectCls =
    "w-full bg-[#0E1726] border border-[#2E3A4E] rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:border-[#3C50E0] transition-colors";

  return (
    <div className="p-5 space-y-4">
      <Field label={t("players.skin")} htmlFor="skin">
        <select id="skin" value={skin} onChange={(e) => setSkin(e.target.value)} className={selectCls}>
          {skinOptions.map((s) => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
        </select>
      </Field>
      <Field label={t("players.language")} htmlFor="lang">
        <select id="lang" value={language} onChange={(e) => setLanguage(e.target.value)} className={selectCls}>
          {langOptions.map((l) => (
            <option key={l} value={l}>
              {l}
            </option>
          ))}
        </select>
      </Field>
      <Field label={t("players.fieldOfView", fov)} htmlFor="fov">
        <input
          id="fov"
          type="range"
          min={30}
          max={120}
          step={1}
          value={fov}
          onChange={(e) => setFov(Number(e.target.value))}
          className="w-full accent-[#3C50E0]"
        />
        <div className="flex justify-between text-xs text-[#4A5568] mt-0.5">
          <span>30°</span>
          <span>120°</span>
        </div>
      </Field>
      <div className="pt-1">
        <BoolRow label={t("players.shaders")} value={shaders} onChange={setShaders} />
        <BoolRow label={t("players.animatedFavicon")} value={favicon} onChange={setFavicon} />
        <BoolRow label={t("players.godMode")} value={godMode} onChange={setGodMode} />
        <BoolRow label={t("players.lightBoost")} value={lightBoost} onChange={setLightBoost} />
      </div>
      <SaveButton saving={saving} saved={saved} onClick={save} />
    </div>
  );
}
