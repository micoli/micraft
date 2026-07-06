import { KeyboardEvent, useEffect, useState } from "react";
import { PlayerModelPreview } from "../shared/PlayerModelPreview";
import { cn } from "../primitives/cn";
import { Input, inputFieldCls } from "../primitives/Input";
import { Label } from "../primitives/Label";
import { Button } from "../primitives/Button";
import { Panel, FormField } from "../primitives/Panel";
import { useLogin } from "../hooks/useLogin";
import { CharacterCreationForm } from "./CharacterCreation";

const SUPPORTED_LANGS: { code: string; label: string }[] = [
  { code: "en", label: "English" },
  { code: "fr", label: "Français" },
];

const SKINS = ["player", "askin"];

function LangSelect({ value, onChange }: { value: string; onChange: (v: string) => void }) {
  return (
    <FormField>
      <Label>Language</Label>
      <select value={value} onChange={(e) => onChange(e.target.value)} className={cn(inputFieldCls, "cursor-pointer")}>
        {SUPPORTED_LANGS.map((l) => (
          <option key={l.code} value={l.code}>
            {l.label}
          </option>
        ))}
      </select>
    </FormField>
  );
}

function WalkingToggle({ walking, onChange }: { walking: boolean; onChange: (v: boolean) => void }) {
  return (
    <div className="flex gap-1 w-40">
      {[
        { label: "Statique", value: false },
        { label: "Marche", value: true },
      ].map(({ label, value }) => (
        <button
          key={label}
          onClick={() => onChange(value)}
          className={cn(
            "flex-1 font-mono text-[11px] py-1 rounded border transition-colors",
            walking === value
              ? "bg-green-950/60 border-green-700/60 text-green-400"
              : "bg-[#1e1e1e] border-[#333] text-[#666]",
          )}
        >
          {label}
        </button>
      ))}
    </div>
  );
}

type L = ReturnType<typeof useLogin>;

function AuthStep({ L }: { L: L }) {
  if (L.authMode === "loading") {
    return <div className="text-center text-[#888] py-5">Loading…</div>;
  }
  return (
    <>
      <div className="text-[30px] font-bold text-center mb-10 text-blue-400">MiCraft</div>

      {L.authMode === "local" && (
        <div className="space-y-5">
          <FormField>
            <Label>Email</Label>
            <Input
              ref={L.usernameInputRef}
              type="email"
              placeholder="your@email.com"
              value={L.username}
              onChange={(e) => L.setUsername(e.target.value)}
              onKeyDown={(e: KeyboardEvent<HTMLInputElement>) => {
                if (e.key === "Enter") L.passwordInputRef.current?.focus();
              }}
            />
          </FormField>
          <FormField>
            <Label>Password</Label>
            <Input
              ref={L.passwordInputRef}
              type="password"
              placeholder="••••••••"
              value={L.password}
              onChange={(e) => L.setPassword(e.target.value)}
              onKeyDown={(e: KeyboardEvent<HTMLInputElement>) => {
                if (e.key === "Enter") L.doLocalLogin();
              }}
            />
          </FormField>
          {L.authError && <div className="text-red-400 text-sm">{L.authError}</div>}
          <Button variant="blue" size="lg" className="w-full" onClick={L.doLocalLogin} disabled={L.authLoading}>
            {L.authLoading ? "Logging in…" : "Login"}
          </Button>
        </div>
      )}

      {L.authMode === "oauth" && (
        <div className="space-y-5">
          <div className="text-center text-[#aaa] text-sm">Sign in to play</div>
          <button
            className="w-full py-3 bg-white border border-[#ccc] rounded text-[#333] font-mono font-bold text-[15px] cursor-pointer flex items-center justify-center gap-2 hover:bg-gray-100"
            onClick={L.doOAuthLogin}
          >
            <span>G</span> Continue with Google
          </button>
        </div>
      )}

      {L.authMode === "none" && (
        <div className="space-y-5">
          <FormField>
            <Label>Username</Label>
            <Input
              ref={L.usernameInputRef}
              type="text"
              placeholder="Enter your username"
              value={L.username}
              onChange={(e) => L.setUsername(e.target.value)}
              onKeyDown={(e: KeyboardEvent<HTMLInputElement>) => {
                if (e.key === "Enter" && L.username.trim()) L.goChars(L.username.trim());
              }}
            />
          </FormField>
          <LangSelect value={L.lang} onChange={L.setLang} />
          <Button
            variant="blue"
            size="lg"
            className="w-full"
            onClick={() => {
              if (L.username.trim()) L.goChars(L.username.trim());
            }}
          >
            Continue
          </Button>
        </div>
      )}
    </>
  );
}

function CharsStep({ L }: { L: L }) {
  return (
    <div
      className="flex flex-col gap-5"
      onKeyDown={(e: KeyboardEvent<HTMLDivElement>) => {
        if (e.key === "Enter" && L.selected) {
          e.stopPropagation();
          L.doPlay();
        }
      }}
    >
      <div className="flex gap-10 items-start">
        <div className="min-w-[280px] space-y-5">
          <div className="text-sm text-[#aaa]">Choose your character:</div>
          {(L.authMode === "local" || L.authMode === "oauth") && <LangSelect value={L.lang} onChange={L.setLang} />}
          <div>
            {L.chars.length === 0 && <div className="text-xs text-[#666] mb-3">No characters yet.</div>}
            {L.chars.map((name, i) => (
              <div key={name} className="flex items-center gap-2 py-2.5">
                <input
                  type="radio"
                  name="mc-char"
                  value={name}
                  id={`mc-char-${i}`}
                  checked={L.selected === name}
                  onChange={() => L.setSelected(name)}
                />
                <label htmlFor={`mc-char-${i}`} className="text-sm cursor-pointer flex items-center gap-2">
                  {name}
                  {L.charClasses[name] && (
                    <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-blue-950/60 border border-blue-700/50 text-blue-300">
                      {L.charClasses[name]}
                    </span>
                  )}
                </label>
              </div>
            ))}
          </div>
          <Button
            variant="outline"
            size="md"
            className="w-full text-blue-300 border-blue-800/60 hover:border-blue-600 hover:text-blue-200"
            onClick={L.goTypeSelect}
          >
            + Create new character
          </Button>
        </div>

        <div className="flex flex-col items-center gap-3">
          {L.selected ? (
            <>
              <PlayerModelPreview
                key={L.previewSkin + L.previewArmors.join(",")}
                skin={L.previewSkin}
                armors={L.previewArmors}
                walking={L.previewWalking}
              />
              <WalkingToggle walking={L.previewWalking} onChange={L.setPreviewWalking} />
            </>
          ) : (
            <div className="w-40 h-[220px] rounded-md bg-[#111] border border-[#333] flex items-center justify-center text-[#444] text-xs text-center">
              No character selected
            </div>
          )}
        </div>
      </div>

      <div className="flex gap-3 pt-2 border-t border-white/10">
        {L.authMode === "local" || L.authMode === "oauth" ? (
          <Button variant="outline" size="md" className="flex-1" onClick={L.doLogout}>
            ← Log out
          </Button>
        ) : (
          <Button
            variant="outline"
            size="md"
            className="flex-1"
            onClick={() => {
              L.setStep("auth");
              setTimeout(() => L.usernameInputRef.current?.focus(), 50);
            }}
          >
            ← Back
          </Button>
        )}
        <div className="flex-1 flex flex-col gap-1">
          <Button
            ref={L.playButtonRef}
            variant="blue"
            size="md"
            className={cn("w-full", (!L.selected || !L.serverReady) && "opacity-40")}
            onClick={L.doPlay}
            disabled={!L.selected || !L.serverReady}
          >
            Play
          </Button>
          {!L.serverReady && (
            <div className="flex items-center gap-1.5 justify-center text-[10px] text-yellow-500/70 font-mono">
              <span className="inline-block w-1.5 h-1.5 rounded-full bg-yellow-500/70 animate-pulse" />
              Connexion au serveur…
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function TypeSelectStep({
  L,
  postConnect,
  onRpgOptOut,
  onRpgSkip,
}: {
  L: L;
  postConnect: boolean;
  onRpgOptOut: () => void;
  onRpgSkip: () => void;
}) {
  return (
    <div className="flex flex-col gap-6 min-w-[340px]">
      <div className="text-center">
        <div className="text-blue-300 tracking-widest text-sm font-mono mb-1">CHARACTER TYPE</div>
        <div className="text-[#666] text-xs">Choose how you want to play</div>
      </div>
      <div className="flex flex-col gap-3">
        <button
          onClick={() => L.setStep("rpgCreate")}
          className="flex flex-col gap-1 px-5 py-4 rounded border border-blue-700/50 bg-blue-950/30 text-left hover:border-blue-500/70 transition-colors"
        >
          <span className="text-blue-300 font-mono text-sm font-bold">RPG Character</span>
          <span className="text-[#888] text-xs">Classes, stats, progression — full RPG system.</span>
        </button>
        <button
          onClick={() => {
            if (postConnect) {
              onRpgSkip();
            } else {
              onRpgOptOut();
              L.goCreate();
            }
          }}
          className="flex flex-col gap-1 px-5 py-4 rounded border border-white/10 bg-black/20 text-left hover:border-white/25 transition-colors"
        >
          <span className="text-white/70 font-mono text-sm font-bold">No RPG</span>
          <span className="text-[#666] text-xs">Play without stats or classes. This choice is saved.</span>
        </button>
      </div>
      {!postConnect && (
        <Button variant="ghost" size="sm" className="text-white/30 font-mono" onClick={() => L.setStep("chars")}>
          ← Back
        </Button>
      )}
    </div>
  );
}

function RpgCreateStep({
  L,
  postConnect,
  onRpgSubmit,
  onRpgFormComplete,
}: {
  L: L;
  postConnect: boolean;
  onRpgSubmit: (cmd: string) => void;
  onRpgFormComplete: (cmd: string) => void;
}) {
  return (
    <CharacterCreationForm
      required={false}
      onSubmit={(cmd) => {
        if (postConnect) {
          onRpgSubmit(cmd);
        } else {
          const parts = cmd.split(" ");
          const name = parts[1] ?? "";
          const characterClass = parts[2] ?? "";
          L.doRpgCreate(name, characterClass);
          onRpgFormComplete(cmd);
        }
      }}
      onCancel={() => L.setStep("typeSelect")}
    />
  );
}

function CreateStep({ L }: { L: L }) {
  return (
    <div className="flex gap-10 items-start">
      <div className="min-w-[280px] space-y-5">
        <div className="text-sm text-[#aaa]">New character</div>
        <FormField>
          <Label>Name</Label>
          <Input
            ref={L.createNameInputRef}
            type="text"
            placeholder="Character name"
            value={L.createName}
            onChange={(e) => {
              L.setCreateName(e.target.value);
              L.setCreateError("");
            }}
            onKeyDown={(e: KeyboardEvent<HTMLInputElement>) => {
              if (e.key === "Enter") L.doCreate();
            }}
          />
          {L.createError && <div className="text-red-400 text-sm">{L.createError}</div>}
        </FormField>
        <FormField>
          <Label>Skin</Label>
          <div className="flex items-center gap-2">
            <Button
              variant="secondary"
              size="sm"
              onClick={() => {
                const idx = SKINS.indexOf(L.createSkin);
                L.setCreateSkin(SKINS[(idx - 1 + SKINS.length) % SKINS.length]);
              }}
            >
              ‹
            </Button>
            <div className="flex-1 text-center bg-[#111] border border-[#555] rounded py-1.5 text-sm">
              {L.createSkin}
            </div>
            <Button
              variant="secondary"
              size="sm"
              onClick={() => {
                const idx = SKINS.indexOf(L.createSkin);
                L.setCreateSkin(SKINS[(idx + 1) % SKINS.length]);
              }}
            >
              ›
            </Button>
          </div>
        </FormField>
        <div className="space-y-3">
          <Button variant="blue" size="lg" className="w-full" onClick={L.doCreate}>
            Create
          </Button>
          <Button variant="outline" size="md" className="w-full" onClick={() => L.setStep("chars")}>
            ← Back
          </Button>
        </div>
      </div>

      <div className="flex flex-col items-center gap-3">
        <PlayerModelPreview key={L.createSkin} skin={L.createSkin} armors={[]} walking={L.createWalking} />
        <WalkingToggle walking={L.createWalking} onChange={L.setCreateWalking} />
      </div>
    </div>
  );
}

interface Props {
  visible: boolean;
  loginResultRef: React.MutableRefObject<string>;
  rpgCreationRequired: boolean;
  onRpgSubmit: (cmd: string) => void;
  onRpgFormComplete: (cmd: string) => void;
  onRpgSkip: () => void;
  onRpgOptOut: () => void;
  onHide: () => void;
}

export function LoginOverlay({
  visible,
  loginResultRef,
  rpgCreationRequired,
  onRpgSubmit,
  onRpgFormComplete,
  onRpgSkip,
  onRpgOptOut,
  onHide,
}: Props) {
  const L = useLogin({ visible, loginResultRef, onHide });

  useEffect(() => {
    if (rpgCreationRequired) L.setStep("typeSelect");
  }, [rpgCreationRequired]);

  if (!visible) return null;

  const postConnect = rpgCreationRequired;
  const wideStep = L.step === "rpgCreate";

  return (
    <div className="fixed inset-0 flex items-center justify-center bg-black/82 z-[2000]">
      <Panel className={cn("min-w-[340px]", wideStep && "min-w-[760px]")}>
        {L.step === "auth" && <AuthStep L={L} />}
        {L.step === "chars" && <CharsStep L={L} />}
        {L.step === "create" && <CreateStep L={L} />}
        {L.step === "typeSelect" && (
          <TypeSelectStep L={L} postConnect={postConnect} onRpgOptOut={onRpgOptOut} onRpgSkip={onRpgSkip} />
        )}
        {L.step === "rpgCreate" && (
          <RpgCreateStep
            L={L}
            postConnect={postConnect}
            onRpgSubmit={onRpgSubmit}
            onRpgFormComplete={onRpgFormComplete}
          />
        )}
      </Panel>
    </div>
  );
}
