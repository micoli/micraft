import { useState, useRef } from "react";
import { useNavigate } from "react-router";
import { useForm, useStore } from "@tanstack/react-form";
import { z } from "zod";
import { postApiCharacterCreate } from "../generated/api/requests";
import { PlayerModelPreview } from "../game/shared/PlayerModelPreview";
import { Button } from "../primitives/Button";
import { Input } from "../primitives/Input";
import { Label } from "../primitives/Label";
import { Panel } from "../primitives/Panel";
import { FormField } from "../primitives/FormField";
import { getUsers, saveUsers, getLastUser, getAccountEmail } from "../lib/authStorage";
import { WalkingToggle } from "./characterCreation/WalkingToggle";

const SKINS = ["articulated"];

export function CharacterCreationScreen() {
  const navigate = useNavigate();
  const username = getLastUser();
  const accountKey = getAccountEmail() || username;

  const [createSubmitError, setCreateSubmitError] = useState("");
  const [createWalking, setCreateWalking] = useState(true);
  const [loading, setLoading] = useState(false);

  const createNameInputRef = useRef<HTMLInputElement>(null);

  const existing = getUsers()[accountKey] || [];
  const nameSchema = z
    .string()
    .trim()
    .min(1, "Name required.")
    .refine((v) => !existing.some((c) => c.name === v), "Name already taken.");

  const form = useForm({
    defaultValues: { name: "", skin: "articulated" },
    onSubmit: async ({ value }) => {
      const name = value.name.trim();
      setCreateSubmitError("");
      setLoading(true);
      try {
        const { data, response } = await postApiCharacterCreate({
          body: { playerName: name, skin: value.skin, email: accountKey },
        });
        if (!response?.ok || !data) {
          setCreateSubmitError("Creation failed.");
          createNameInputRef.current?.focus();
          return;
        }
        const users = getUsers();
        if (!users[accountKey]) users[accountKey] = [];
        users[accountKey].push({ name, id: data.id });
        saveUsers(users);
        navigate("/chars");
      } catch {
        setCreateSubmitError("Connection error.");
      } finally {
        setLoading(false);
      }
    },
  });

  const skin = useStore(form.store, (s) => s.values.skin);

  return (
    <div className="fixed inset-0 flex items-center justify-center bg-black/82 z-[2000]">
      <Panel className="min-w-[340px]">
        <form
          onSubmit={(e) => {
            e.preventDefault();
            e.stopPropagation();
            form.handleSubmit();
          }}
          className="flex gap-10 items-start"
        >
          <div className="min-w-[280px] space-y-5">
            <div className="text-sm text-[#aaa]">New character</div>
            <FormField>
              <Label>Name</Label>
              <form.Field name="name" validators={{ onChange: nameSchema }}>
                {(field) => (
                  <>
                    <Input
                      ref={createNameInputRef}
                      type="text"
                      placeholder="Character name"
                      value={field.state.value}
                      onChange={(e) => {
                        field.handleChange(e.target.value);
                        setCreateSubmitError("");
                      }}
                      onBlur={field.handleBlur}
                    />
                    {field.state.meta.errors.length > 0 && (
                      <div className="text-red-400 text-sm">{String(field.state.meta.errors[0])}</div>
                    )}
                  </>
                )}
              </form.Field>
              {createSubmitError && <div className="text-red-400 text-sm">{createSubmitError}</div>}
            </FormField>
            <FormField>
              <Label>Skin</Label>
              <div className="flex items-center gap-2">
                <Button
                  variant="secondary"
                  size="sm"
                  type="button"
                  onClick={() => {
                    const idx = SKINS.indexOf(skin);
                    form.setFieldValue("skin", SKINS[(idx - 1 + SKINS.length) % SKINS.length]);
                  }}
                >
                  ‹
                </Button>
                <div className="flex-1 text-center bg-[#111] border border-[#555] rounded py-1.5 text-sm">{skin}</div>
                <Button
                  variant="secondary"
                  size="sm"
                  type="button"
                  onClick={() => {
                    const idx = SKINS.indexOf(skin);
                    form.setFieldValue("skin", SKINS[(idx + 1) % SKINS.length]);
                  }}
                >
                  ›
                </Button>
              </div>
            </FormField>
            <div className="space-y-3">
              <Button variant="blue" size="lg" className="w-full" type="submit" disabled={loading}>
                {loading ? "Creating…" : "Create"}
              </Button>
              <Button variant="outline" size="md" className="w-full" type="button" onClick={() => navigate("/chars")}>
                ← Back
              </Button>
            </div>
          </div>

          <div className="flex flex-col items-center gap-3">
            <PlayerModelPreview key={skin} skin={skin} armors={[]} walking={createWalking} />
            <WalkingToggle walking={createWalking} onChange={setCreateWalking} />
          </div>
        </form>
      </Panel>
    </div>
  );
}
