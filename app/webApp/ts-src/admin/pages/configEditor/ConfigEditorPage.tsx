import { useEffect, useState } from "react";
import { Editor } from "../../../primitives/Editor";
import { api } from "../../api";
import { useT } from "../../i18n";

const SCHEMA_MAP: Record<string, string> = {
  "server.yaml": "server.schema.json",
  "biomes.yaml": "biomes.schema.json",
  "roads.yaml": "roads.schema.json",
  "houses.yaml": "houses.schema.json",
  "keybindings.yaml": "keybindings.schema.json",
  "items.yaml": "items.schema.json",
  "recipes.yaml": "recipes.schema.json",
  "attack.yaml": "attack.schema.json",
  "classes.yaml": "classes.schema.json",
  "combat.yaml": "combat.schema.json",
  "weather.yaml": "weather.schema.json",
  "auth/users.yaml": "auth-users.schema.json",
  "auth/groups.yaml": "groups.schema.json",
};

export function ConfigEditorPage() {
  const t = useT();
  const [files, setFiles] = useState<string[]>([]);
  const [selected, setSelected] = useState<string | null>(null);
  const [content, setContent] = useState<string>("");
  const [editedContent, setEditedContent] = useState<string>("");
  const [schema, setSchema] = useState<object | null>(null);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.configs.list().then(setFiles);
  }, []);

  const select = async (filename: string) => {
    setSelected(filename);
    setSchema(null);
    setError(null);
    try {
      const text = await api.configs.get(filename);
      setContent(text);
      setEditedContent(text);
    } catch {
      setError(t("config.failedToLoad"));
    }
    const schemaFile = SCHEMA_MAP[filename];
    if (schemaFile) {
      api.configs
        .schema(schemaFile)
        .then(setSchema)
        .catch(() => {});
    }
  };

  const save = async () => {
    if (!selected) return;
    setSaving(true);
    setError(null);
    try {
      const r = await api.configs.save(selected, editedContent);
      if (!r.ok) throw new Error(t("common.serverError", r.status));
      setSaved(true);
      setTimeout(() => setSaved(false), 1500);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : t("common.saveFailed"));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="h-full flex gap-4">
      {/* File list */}
      <div className="w-52 shrink-0 bg-[#1A222C] border border-[#2E3A4E] rounded-xl overflow-hidden self-start">
        <p className="px-4 py-3 text-[10px] font-semibold uppercase tracking-widest text-[#8A99AF] border-b border-[#2E3A4E]">
          {t("config.files")}
        </p>
        {files.length === 0 ? (
          <p className="px-4 py-4 text-[#4A5568] text-sm animate-pulse">{t("common.loading")}</p>
        ) : (
          <div>
            {files.map((f) => (
              <button
                key={f}
                onClick={() => select(f)}
                className={`w-full text-left px-4 py-2.5 text-xs font-mono transition-colors border-b border-[#2E3A4E] last:border-0 ${
                  selected === f
                    ? "bg-[#3C50E0]/20 text-[#818CF8]"
                    : "text-[#8A99AF] hover:bg-[#2E3A4E] hover:text-white"
                }`}
              >
                {f}
              </button>
            ))}
          </div>
        )}
      </div>

      {/* Editor panel */}
      <div className="flex-1 bg-[#1A222C] border border-[#2E3A4E] rounded-xl overflow-hidden flex flex-col min-h-[400px]">
        {selected ? (
          <>
            {/* Toolbar */}
            <div className="flex items-center justify-between px-4 py-2.5 border-b border-[#2E3A4E] shrink-0">
              <div className="flex items-center gap-2">
                <span className="text-xs font-mono text-[#8A99AF]">{selected}</span>
                {SCHEMA_MAP[selected] && (
                  <span className="text-[10px] font-semibold bg-[#3C50E0]/20 text-[#818CF8] border border-[#3C50E0]/30 rounded px-1.5 py-0.5">
                    {t("config.schema")}
                  </span>
                )}
              </div>
              <div className="flex items-center gap-3">
                {error && <span className="text-red-400 text-xs">{error}</span>}
                <button
                  onClick={save}
                  disabled={saving}
                  className="px-3 py-1 rounded-lg text-xs font-medium bg-[#3C50E0] hover:bg-[#3446c7] text-white transition-colors disabled:opacity-50"
                >
                  {saving ? t("common.saving") : saved ? t("common.saved") : t("common.save")}
                </button>
              </div>
            </div>
            <Editor key={selected} content={content} schema={schema} onChange={setEditedContent} />
          </>
        ) : (
          <div className="flex-1 flex items-center justify-center text-[#4A5568] text-sm">{t("config.selectFile")}</div>
        )}
      </div>
    </div>
  );
}
