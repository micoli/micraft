import { useT } from "../../i18n";
import { useState } from "react";
import { useForm } from "@tanstack/react-form";
import { z } from "zod";
import { api } from "../../api";
import { Icon } from "../../../primitives/Icon";
import { ICONS } from "../../../primitives/icons";

export function CreateWorldForm({ onCreated }: { onCreated: () => void }) {
  const t = useT();
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const nameSchema = z
    .string()
    .trim()
    .min(1, t("worlds.nameRequired"))
    .regex(/^[a-zA-Z0-9_-]+$/, t("worlds.nameRule"));
  const seedSchema = z.string().refine((v) => v === "" || !isNaN(Number(v)), t("worlds.seedMustBeNumber"));

  const form = useForm({
    defaultValues: { name: "", seed: "" },
    onSubmit: async ({ value }) => {
      setError(null);
      const seedNum = value.seed === "" ? 42 : Number(value.seed);
      const r = await api.worlds.create(value.name.trim(), seedNum);
      if (r.status === 409) return setError(t("worlds.alreadyExists"));
      if (!r.ok) return setError(t("worlds.serverError"));
      form.reset();
      setOpen(false);
      onCreated();
    },
  });

  const randomSeed = () => form.setFieldValue("seed", String(Math.floor(Math.random() * 2_147_483_647)));

  if (!open) {
    return (
      <button
        onClick={() => setOpen(true)}
        className="flex items-center gap-2 px-4 py-2.5 rounded-lg bg-[#3C50E0] hover:bg-[#3446c7] text-white text-sm font-medium transition-colors"
      >
        <Icon d={ICONS.add} size={16} />
        {t("worlds.create")}
      </button>
    );
  }

  return (
    <div className="bg-[#1A222C] rounded-xl border border-[#3C50E0] p-5">
      <h3 className="text-white font-semibold text-[15px] mb-4">{t("worlds.newWorld")}</h3>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          e.stopPropagation();
          form.handleSubmit();
        }}
        className="space-y-3"
      >
        <div>
          <label className="block text-[11px] uppercase tracking-widest text-[#8A99AF] mb-1.5">
            {t("worlds.name")}
          </label>
          <form.Field name="name" validators={{ onChange: nameSchema }}>
            {(field) => (
              <>
                <input
                  type="text"
                  value={field.state.value}
                  onChange={(e) => field.handleChange(e.target.value)}
                  onBlur={field.handleBlur}
                  placeholder={t("worlds.namePlaceholder")}
                  className="w-full bg-[#0E1726] border border-[#2E3A4E] rounded-lg px-3 py-2 text-sm text-white placeholder-[#4A5568] focus:outline-none focus:border-[#3C50E0] transition-colors"
                />
                {field.state.meta.errors.length > 0 && (
                  <p className="text-red-400 text-xs mt-1">{String(field.state.meta.errors[0])}</p>
                )}
              </>
            )}
          </form.Field>
        </div>
        <div>
          <label className="block text-[11px] uppercase tracking-widest text-[#8A99AF] mb-1.5">
            {t("worlds.seed")}
          </label>
          <div className="flex gap-2">
            <form.Field name="seed" validators={{ onChange: seedSchema }}>
              {(field) => (
                <input
                  type="number"
                  value={field.state.value}
                  onChange={(e) => field.handleChange(e.target.value)}
                  onBlur={field.handleBlur}
                  placeholder="42"
                  className="flex-1 bg-[#0E1726] border border-[#2E3A4E] rounded-lg px-3 py-2 text-sm text-white placeholder-[#4A5568] focus:outline-none focus:border-[#3C50E0] transition-colors font-mono"
                />
              )}
            </form.Field>
            <button
              type="button"
              onClick={randomSeed}
              className="px-3 py-2 rounded-lg border border-[#2E3A4E] text-[#8A99AF] hover:text-white hover:bg-[#2E3A4E] text-xs transition-colors"
            >
              {t("worlds.random")}
            </button>
          </div>
        </div>
        {error && <p className="text-red-400 text-xs">{error}</p>}
        <div className="flex gap-2 pt-1">
          <form.Subscribe selector={(state) => state.isSubmitting}>
            {(isSubmitting) => (
              <button
                type="submit"
                disabled={isSubmitting}
                className="flex-1 py-2 rounded-lg bg-[#3C50E0] hover:bg-[#3446c7] text-white text-sm font-medium transition-colors disabled:opacity-50"
              >
                {isSubmitting ? t("worlds.creating") : t("worlds.createShort")}
              </button>
            )}
          </form.Subscribe>
          <button
            type="button"
            onClick={() => {
              setOpen(false);
              setError(null);
              form.reset();
            }}
            className="px-4 py-2 rounded-lg border border-[#2E3A4E] text-[#8A99AF] hover:text-white hover:bg-[#2E3A4E] text-sm transition-colors"
          >
            {t("common.cancel")}
          </button>
        </div>
      </form>
    </div>
  );
}
