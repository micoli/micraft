import { useState, useRef } from "react";
import { useNavigate } from "react-router";
import { KeyboardEvent } from "react";
import { PlayerModelPreview } from "../game/shared/PlayerModelPreview";
import { Button } from "../primitives/Button";
import { Input } from "../primitives/Input";
import { Label } from "../primitives/Label";
import { Panel } from "../primitives/Panel";
import { FormField } from "../primitives/FormField";
import { getUsers, saveUsers, getLastUser, getAccountEmail } from "../lib/authStorage";
import { WalkingToggle } from "./WalkingToggle";

const SKINS = ["player", "askin"];

export function CharacterCreationScreen() {
  const navigate = useNavigate();
  const username = getLastUser();
  const accountKey = getAccountEmail() || username;

  const [createName, setCreateName] = useState("");
  const [createSkin, setCreateSkin] = useState("player");
  const [createError, setCreateError] = useState("");
  const [createWalking, setCreateWalking] = useState(true);
  const [loading, setLoading] = useState(false);

  const createNameInputRef = useRef<HTMLInputElement>(null);

  async function doCreate() {
    const name = createName.trim();
    if (!name) {
      setCreateError("Name required.");
      createNameInputRef.current?.focus();
      return;
    }
    const users = getUsers();
    const existing = users[accountKey] || [];
    if (existing.some((c) => c.name === name)) {
      setCreateError("Name already taken.");
      createNameInputRef.current?.focus();
      return;
    }
    setLoading(true);
    try {
      const r = await fetch("/api/character/create", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ playerName: name, skin: createSkin, email: accountKey }),
      });
      if (!r.ok) {
        const text = await r.text().catch(() => "");
        setCreateError(text || "Creation failed.");
        setLoading(false);
        return;
      }
      const data = (await r.json()) as { id: string };
      if (!users[accountKey]) users[accountKey] = [];
      users[accountKey].push({ name, id: data.id });
      saveUsers(users);
      navigate("/chars");
    } catch {
      setCreateError("Connection error.");
      setLoading(false);
    }
  }

  return (
    <div className="fixed inset-0 flex items-center justify-center bg-black/82 z-[2000]">
      <Panel className="min-w-[340px]">
        <div className="flex gap-10 items-start">
          <div className="min-w-[280px] space-y-5">
            <div className="text-sm text-[#aaa]">New character</div>
            <FormField>
              <Label>Name</Label>
              <Input
                ref={createNameInputRef}
                type="text"
                placeholder="Character name"
                value={createName}
                onChange={(e) => {
                  setCreateName(e.target.value);
                  setCreateError("");
                }}
                onKeyDown={(e: KeyboardEvent<HTMLInputElement>) => {
                  if (e.key === "Enter") void doCreate();
                }}
              />
              {createError && <div className="text-red-400 text-sm">{createError}</div>}
            </FormField>
            <FormField>
              <Label>Skin</Label>
              <div className="flex items-center gap-2">
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={() => {
                    const idx = SKINS.indexOf(createSkin);
                    setCreateSkin(SKINS[(idx - 1 + SKINS.length) % SKINS.length]);
                  }}
                >
                  ‹
                </Button>
                <div className="flex-1 text-center bg-[#111] border border-[#555] rounded py-1.5 text-sm">
                  {createSkin}
                </div>
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={() => {
                    const idx = SKINS.indexOf(createSkin);
                    setCreateSkin(SKINS[(idx + 1) % SKINS.length]);
                  }}
                >
                  ›
                </Button>
              </div>
            </FormField>
            <div className="space-y-3">
              <Button variant="blue" size="lg" className="w-full" onClick={() => void doCreate()} disabled={loading}>
                {loading ? "Creating…" : "Create"}
              </Button>
              <Button variant="outline" size="md" className="w-full" onClick={() => navigate("/chars")}>
                ← Back
              </Button>
            </div>
          </div>

          <div className="flex flex-col items-center gap-3">
            <PlayerModelPreview key={createSkin} skin={createSkin} armors={[]} walking={createWalking} />
            <WalkingToggle walking={createWalking} onChange={setCreateWalking} />
          </div>
        </div>
      </Panel>
    </div>
  );
}
