import { useState, useEffect, useRef } from "react";
import { useNavigate } from "react-router";
import { KeyboardEvent } from "react";
import { PlayerModelPreview } from "../game/shared/PlayerModelPreview";
import { Button } from "../primitives/Button";
import { Panel } from "../primitives/Panel";
import { cn } from "../primitives/cn";
import { useGameContext } from "../game/GameContext";
import {
  getUsers,
  saveUsers,
  getLastPlayer,
  saveLastPlayer,
  getLastLang,
  saveLastLang,
  getStoredToken,
  clearStoredToken,
  getLastUser,
  saveLastUser,
  clearLastUser,
  AuthMode,
} from "../lib/authStorage";

const SUPPORTED_LANGS: { code: string; label: string }[] = [
  { code: "en", label: "English" },
  { code: "fr", label: "Français" },
];

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

const MC_SERVER_VERSION_KEY = "mc_server_version";

export function CharacterSelectionScreen() {
  const navigate = useNavigate();
  const { loginResultRef } = useGameContext();

  const [authMode, setAuthMode] = useState<AuthMode>("loading");
  const [username, setUsername] = useState(getLastUser());
  const [token, setToken] = useState(getStoredToken());
  const [lang, setLang] = useState(getLastLang());
  const [chars, setChars] = useState<string[]>([]);
  const [charClasses, setCharClasses] = useState<Record<string, string>>({});
  const [selected, setSelected] = useState("");
  const [previewSkin, setPreviewSkin] = useState("player");
  const [previewArmors, setPreviewArmors] = useState<string[]>([]);
  const [previewWalking, setPreviewWalking] = useState(true);
  const [serverReady, setServerReady] = useState(false);

  const playButtonRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    fetch("/api/auth/config")
      .then((r) => r.json())
      .then((d: { provider: string }) => setAuthMode((d.provider as AuthMode) || "none"))
      .catch(() => setAuthMode("none"));
  }, []);

  useEffect(() => {
    let cancelled = false;
    async function checkServer() {
      try {
        const r = await fetch("/api/version", { cache: "no-cache" });
        if (!r.ok) {
          if (!cancelled) setServerReady(false);
          return;
        }
        const { server } = (await r.json()) as { server: string };
        const stored = sessionStorage.getItem(MC_SERVER_VERSION_KEY);
        if (stored === null) {
          sessionStorage.setItem(MC_SERVER_VERSION_KEY, server);
        } else if (stored !== server) {
          sessionStorage.setItem(MC_SERVER_VERSION_KEY, server);
          window.location.href = location.pathname + "?_v=" + server;
          return;
        }
        if (!cancelled) setServerReady(true);
      } catch {
        if (!cancelled) setServerReady(false);
      }
    }
    void checkServer();
    const interval = setInterval(() => void checkServer(), 3000);
    return () => {
      cancelled = true;
      clearInterval(interval);
    };
  }, []);

  useEffect(() => {
    const user = username || getLastUser();
    if (!user) return;
    const users = getUsers();
    let playerChars = users[user] || [];

    async function loadChars() {
      if (playerChars.length > 0) {
        const results = await Promise.all(
          playerChars.map((name) =>
            fetch(`/api/player/${encodeURIComponent(name)}/skin`)
              .then((r) => (r.ok ? name : null))
              .catch(() => name),
          ),
        );
        const existing = results.filter((n): n is string => n !== null);
        if (existing.length !== playerChars.length) {
          const updated = getUsers();
          updated[user] = existing;
          saveUsers(updated);
          playerChars = existing;
        }
      }
      const lastPlayer = getLastPlayer(user);
      setChars(playerChars);
      setSelected(lastPlayer && playerChars.includes(lastPlayer) ? lastPlayer : playerChars[0] || "");

      const classEntries = await Promise.all(
        playerChars.map((n) =>
          fetch(`/api/player/${encodeURIComponent(n)}/rpg`)
            .then((r) => (r.ok ? r.json() : null))
            .then((d: { characterClass: string } | null) => [n, d?.characterClass ?? null] as const)
            .catch(() => [n, null] as const),
        ),
      );
      setCharClasses(Object.fromEntries(classEntries.filter(([, cls]) => cls !== null)) as Record<string, string>);
    }

    void loadChars();
  }, [username]);

  useEffect(() => {
    if (!selected) return;
    const enc = encodeURIComponent(selected);
    Promise.all([
      fetch(`/api/player/${enc}/skin`)
        .then((r) => r.json())
        .catch(() => ({ skin: "player" })),
      fetch(`/api/player/${enc}/armors`)
        .then((r) => r.json())
        .catch(() => []),
    ]).then(([skinData, armors]) => {
      setPreviewSkin(skinData.skin ?? "player");
      setPreviewArmors(Array.isArray(armors) ? armors : []);
    });
  }, [selected]);

  useEffect(() => {
    if (selected) setTimeout(() => playButtonRef.current?.focus(), 50);
  }, [selected]);

  function doPlay() {
    if (!selected) return;
    saveLastPlayer(username, selected);
    saveLastLang(lang);
    loginResultRef.current = username + "\t" + selected + "\t" + lang + "\t" + token;
    navigate("/game");
  }

  function doLogout() {
    clearStoredToken();
    setToken("");
    saveLastUser("");
    navigate("/auth");
  }

  function addCharToList(name: string, skin: string) {
    const user = username;
    const users = getUsers();
    if (!users[user]) users[user] = [];
    if (!users[user].includes(name)) users[user].push(name);
    saveUsers(users);
    setChars(users[user]);
    setSelected(name);
    setPreviewSkin(skin);
    setPreviewArmors([]);
  }

  return (
    <div className="fixed inset-0 flex items-center justify-center bg-black/82 z-[2000]">
      <Panel className="min-w-[340px]">
        <div
          className="flex flex-col gap-5"
          onKeyDown={(e: KeyboardEvent<HTMLDivElement>) => {
            if (e.key === "Enter" && selected) {
              e.stopPropagation();
              doPlay();
            }
          }}
        >
          <div className="flex gap-10 items-start">
            <div className="min-w-[280px] space-y-5">
              <div className="text-sm text-[#aaa]">Choose your character:</div>
              {(authMode === "local" || authMode === "oauth") && (
                <div className="w-full">
                  <select
                    value={lang}
                    onChange={(e) => setLang(e.target.value)}
                    className="w-full bg-[#111] border border-[#444] rounded px-3 py-2 text-sm text-white cursor-pointer"
                  >
                    {SUPPORTED_LANGS.map((l) => (
                      <option key={l.code} value={l.code}>
                        {l.label}
                      </option>
                    ))}
                  </select>
                </div>
              )}
              <div>
                {chars.length === 0 && <div className="text-xs text-[#666] mb-3">No characters yet.</div>}
                {chars.map((name, i) => (
                  <div key={name} className="flex items-center gap-2 py-2.5">
                    <input
                      type="radio"
                      name="mc-char"
                      value={name}
                      id={`mc-char-${i}`}
                      checked={selected === name}
                      onChange={() => setSelected(name)}
                    />
                    <label htmlFor={`mc-char-${i}`} className="text-sm cursor-pointer flex items-center gap-2">
                      {name}
                      {charClasses[name] && (
                        <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-blue-950/60 border border-blue-700/50 text-blue-300">
                          {charClasses[name]}
                        </span>
                      )}
                    </label>
                  </div>
                ))}
              </div>
              <div className="flex flex-col gap-2">
                <Button
                  variant="outline"
                  size="md"
                  className="w-full text-white/60 border-white/20 hover:border-white/40"
                  onClick={() => navigate("/char-create")}
                >
                  + New character
                </Button>
                <Button
                  variant="outline"
                  size="md"
                  className="w-full text-blue-300 border-blue-800/60 hover:border-blue-600 hover:text-blue-200"
                  onClick={() => navigate("/char-rpg-create")}
                >
                  + New RPG character
                </Button>
              </div>
            </div>

            <div className="flex flex-col items-center gap-3">
              {selected ? (
                <>
                  <PlayerModelPreview
                    key={previewSkin + previewArmors.join(",")}
                    skin={previewSkin}
                    armors={previewArmors}
                    walking={previewWalking}
                  />
                  <WalkingToggle walking={previewWalking} onChange={setPreviewWalking} />
                </>
              ) : (
                <div className="w-40 h-[220px] rounded-md bg-[#111] border border-[#333] flex items-center justify-center text-[#444] text-xs text-center">
                  No character selected
                </div>
              )}
            </div>
          </div>

          <div className="flex gap-3 pt-2 border-t border-white/10">
            {authMode === "local" || authMode === "oauth" ? (
              <Button variant="outline" size="md" className="flex-1" onClick={doLogout}>
                ← Log out
              </Button>
            ) : (
              <Button
                variant="outline"
                size="md"
                className="flex-1"
                onClick={() => {
                  clearLastUser();
                  navigate("/auth");
                }}
              >
                ← Back
              </Button>
            )}
            <div className="flex-1 flex flex-col gap-1">
              <Button
                ref={playButtonRef}
                variant="blue"
                size="md"
                className={cn("w-full", (!selected || !serverReady) && "opacity-40")}
                onClick={doPlay}
                disabled={!selected || !serverReady}
              >
                Play
              </Button>
              {!serverReady && (
                <div className="flex items-center gap-1.5 justify-center text-[10px] text-yellow-500/70 font-mono">
                  <span className="inline-block w-1.5 h-1.5 rounded-full bg-yellow-500/70 animate-pulse" />
                  Connexion au serveur…
                </div>
              )}
            </div>
          </div>
        </div>
      </Panel>
    </div>
  );
}
