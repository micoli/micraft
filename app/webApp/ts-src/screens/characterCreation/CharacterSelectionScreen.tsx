import { useState, useEffect, useRef } from "react";
import { useNavigate } from "react-router";
import { KeyboardEvent } from "react";
import {
  getApiAuthConfig,
  getApiPlayersByEmailByEmail,
  getApiPlayerByIdSkin,
  getApiPlayerByIdRpg,
  getApiPlayerByIdArmors,
} from "../../generated/api/requests";
import { PlayerModelPreview } from "../../game/shared/PlayerModelPreview";
import { Button } from "../../primitives/Button";
import { Panel } from "../../primitives/Panel";
import { cn } from "../../primitives/cn";
import { useGameContext } from "../../game/GameContext";
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
  getAccountEmail,
  AuthMode,
  PlayerEntry,
} from "../../lib/authStorage";
import { WalkingToggle } from "./WalkingToggle";

const SUPPORTED_LANGS: { code: string; label: string }[] = [
  { code: "en", label: "English" },
  { code: "fr", label: "Français" },
];

export function CharacterSelectionScreen() {
  const navigate = useNavigate();
  const { loginResultRef } = useGameContext();

  const [authMode, setAuthMode] = useState<AuthMode>("loading");
  const [username] = useState(getLastUser());
  const [token, setToken] = useState(getStoredToken());
  const [lang, setLang] = useState(getLastLang());
  const [chars, setChars] = useState<PlayerEntry[]>([]);
  const [charClasses, setCharClasses] = useState<Record<string, string>>({});
  const [selected, setSelected] = useState("");
  const [previewSkin, setPreviewSkin] = useState("player");
  const [previewArmors, setPreviewArmors] = useState<string[]>([]);
  const [previewWalking, setPreviewWalking] = useState(true);
  const [serverReady, setServerReady] = useState(false);

  const playButtonRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    getApiAuthConfig()
      .then(({ data }) => {
        setAuthMode((data?.provider as AuthMode) || "none");
        setServerReady(true);
      })
      .catch(() => {
        setAuthMode("none");
        setServerReady(true);
      });
  }, []);

  useEffect(() => {
    const user = username || getLastUser();
    if (!user) return;
    const accountKey = getAccountEmail() || user;
    const users = getUsers();
    let playerChars = users[accountKey] || [];

    async function loadChars() {
      // Try server-authoritative list first
      try {
        const { data: serverChars } = await getApiPlayersByEmailByEmail({ path: { email: accountKey } });
        if (Array.isArray(serverChars) && serverChars.length > 0) {
          playerChars = serverChars;
          const updated = getUsers();
          updated[accountKey] = serverChars;
          saveUsers(updated);
        }
      } catch {
        // fall back to localStorage cache
      }

      if (playerChars.length > 0) {
        const results = await Promise.all(
          playerChars.map((char) =>
            getApiPlayerByIdSkin({ path: { id: char.id } })
              .then((r) => (r.response?.ok ? char : null))
              .catch(() => char),
          ),
        );
        const existing = results.filter((c): c is PlayerEntry => c !== null);
        if (existing.length !== playerChars.length) {
          const updated = getUsers();
          updated[accountKey] = existing;
          saveUsers(updated);
          playerChars = existing;
        }
      }
      const lastPlayer = getLastPlayer(accountKey);
      setChars(playerChars);
      setSelected(
        lastPlayer && playerChars.some((c) => c.name === lastPlayer) ? lastPlayer : playerChars[0]?.name || "",
      );

      const classEntries = await Promise.all(
        playerChars.map((char) =>
          getApiPlayerByIdRpg({ path: { id: char.id } })
            .then(({ data }) => [char.name, data?.characterClass ?? null] as const)
            .catch(() => [char.name, null] as const),
        ),
      );
      setCharClasses(Object.fromEntries(classEntries.filter(([, cls]) => cls !== null)) as Record<string, string>);
    }

    void loadChars();
  }, [username]);

  useEffect(() => {
    if (!selected) return;
    const selectedChar = chars.find((c) => c.name === selected);
    if (!selectedChar?.id) return;
    Promise.all([
      getApiPlayerByIdSkin({ path: { id: selectedChar.id } })
        .then((r) => r.data)
        .catch(() => ({ skin: "player" })),
      getApiPlayerByIdArmors({ path: { id: selectedChar.id } })
        .then((r) => r.data)
        .catch(() => []),
    ]).then(([skinData, armors]) => {
      setPreviewSkin(skinData?.skin ?? "player");
      setPreviewArmors(Array.isArray(armors) ? armors : []);
    });
  }, [selected, chars]);

  useEffect(() => {
    if (selected) setTimeout(() => playButtonRef.current?.focus(), 50);
  }, [selected]);

  function doPlay() {
    if (!selected) return;
    const accountKey = getAccountEmail() || username;
    const charEntry = chars.find((c) => c.name === selected);
    if (!charEntry) return;
    saveLastUser(username);
    saveLastPlayer(accountKey, selected);
    saveLastLang(lang);
    loginResultRef.current = accountKey + "\t" + selected + "\t" + lang + "\t" + token;
    navigate(`/game/${encodeURIComponent(accountKey)}/${charEntry.id}`);
  }

  function doLogout() {
    clearStoredToken();
    setToken("");
    saveLastUser("");
    navigate("/auth");
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
              <div className="flex justify-center">
                <img src="/assets/logo.png" alt="MiCraft" className="max-w-[180px] w-full" />
              </div>
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
                {chars.map((char, i) => (
                  <div key={char.name} className="flex items-center gap-2 py-2.5">
                    <input
                      type="radio"
                      name="mc-char"
                      value={char.name}
                      id={`mc-char-${i}`}
                      checked={selected === char.name}
                      onChange={() => setSelected(char.name)}
                    />
                    <label htmlFor={`mc-char-${i}`} className="text-sm cursor-pointer flex items-center gap-2">
                      {char.name}
                      {charClasses[char.name] && (
                        <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-blue-950/60 border border-blue-700/50 text-blue-300">
                          {charClasses[char.name]}
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
