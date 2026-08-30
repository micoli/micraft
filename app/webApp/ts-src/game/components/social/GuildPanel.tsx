import { useState } from "react";
import { Button } from "../../../primitives/Button";
import { Dialog } from "../../../primitives/Dialog";
import { DialogContent } from "../../../primitives/DialogContent";
import { DialogTitle } from "../../../primitives/DialogTitle";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "../../../primitives/Tabs";
import { GuildInfo, GuildPermission, SocialInvite } from "../../types";

interface Props {
  open: boolean;
  guild: GuildInfo | null;
  invite?: SocialInvite;
  myPlayerId: string;
  onClose: () => void;
}

function emit(ev: string) {
  window.mcState?.events?.push(ev);
}

const ALL_FLAGS: GuildPermission[] = [
  "INVITE",
  "KICK",
  "MANAGE_RANKS",
  "EDIT_MOTD",
  "BANK_DEPOSIT",
  "BANK_WITHDRAW",
  "DISBAND",
  "EDIT_INFO",
];

export function GuildPanel({ open, guild, invite, myPlayerId, onClose }: Props) {
  const [motd, setMotd] = useState("");
  const has = (f: GuildPermission) => !!guild?.myFlags.includes(f);
  const isOwner = !!guild && guild.ownerId === myPlayerId;

  return (
    <Dialog
      open={open}
      onOpenChange={(v) => {
        if (!v) onClose();
      }}
    >
      <DialogContent className="w-[520px] max-w-[95vw]" movable>
        <DialogTitle>Guilde{guild ? ` — ${guild.name} [${guild.tag}]` : ""}</DialogTitle>
        {!guild ? (
          <div style={{ marginTop: 12 }}>
            <p style={{ fontSize: 12, opacity: 0.7, marginBottom: 8 }}>{"Vous n'êtes dans aucune guilde."}</p>
            {invite && (
              <div style={{ marginBottom: 8 }}>
                <p style={{ fontSize: 12 }}>
                  {invite.from} vous a invité dans {invite.name}.
                </p>
                <div style={{ display: "flex", gap: 6 }}>
                  <Button size="sm" onClick={() => emit(`guild_respond:${invite.id}\t1`)}>
                    Accepter
                  </Button>
                  <Button size="sm" variant="danger" onClick={() => emit(`guild_respond:${invite.id}\t0`)}>
                    Refuser
                  </Button>
                </div>
              </div>
            )}
            <Button
              size="sm"
              onClick={() => {
                const n = prompt("Nom de la guilde ?");
                if (!n) return;
                const t = prompt("Tag (1-5 caractères) ?");
                if (t) emit(`guild_create:${n}\t${t}`);
              }}
            >
              Fonder une guilde
            </Button>
          </div>
        ) : (
          <Tabs defaultValue="members" style={{ marginTop: 8 }}>
            <TabsList>
              <TabsTrigger value="members">Membres</TabsTrigger>
              <TabsTrigger value="ranks">Grades</TabsTrigger>
              <TabsTrigger value="info">Infos</TabsTrigger>
            </TabsList>

            <TabsContent value="members">
              <div style={{ display: "flex", gap: 6, margin: "8px 0", flexWrap: "wrap" }}>
                {has("INVITE") && (
                  <Button
                    size="sm"
                    onClick={() => {
                      const n = prompt("Inviter quel joueur ?");
                      if (n) emit(`guild_invite:${n}`);
                    }}
                  >
                    Inviter
                  </Button>
                )}
                <Button size="sm" variant="secondary" onClick={() => emit("guild_leave")}>
                  Quitter
                </Button>
                {has("DISBAND") && (
                  <Button size="sm" variant="danger" onClick={() => emit("guild_disband")}>
                    Dissoudre
                  </Button>
                )}
              </div>
              <table style={{ width: "100%", fontSize: 12, borderCollapse: "collapse" }}>
                <tbody>
                  {guild.members.map((m) => (
                    <tr key={m.playerId} style={{ opacity: m.online ? 1 : 0.45 }}>
                      <td>{m.playerName}</td>
                      <td>
                        {has("MANAGE_RANKS") && m.playerId !== guild.ownerId ? (
                          <select
                            value={m.rank}
                            onChange={(e) => emit(`guild_setrank:${m.playerId}\t${e.target.value}`)}
                          >
                            {guild.ranks.map((r) => (
                              <option key={r.name} value={r.name}>
                                {r.name}
                              </option>
                            ))}
                          </select>
                        ) : (
                          m.rank
                        )}
                      </td>
                      <td style={{ textAlign: "right" }}>
                        {has("KICK") && m.playerId !== guild.ownerId && m.playerId !== myPlayerId && (
                          <Button size="sm" variant="danger" onClick={() => emit(`guild_kick:${m.playerId}`)}>
                            Exclure
                          </Button>
                        )}
                        {isOwner && m.playerId !== myPlayerId && (
                          <Button size="sm" variant="secondary" onClick={() => emit(`guild_transfer:${m.playerId}`)}>
                            Céder
                          </Button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </TabsContent>

            <TabsContent value="ranks">
              {guild.ranks.map((r) => (
                <div key={r.name} style={{ borderBottom: "1px solid rgba(255,255,255,0.1)", padding: "6px 0" }}>
                  <strong>
                    {r.name} <span style={{ opacity: 0.5 }}>({r.order})</span>
                  </strong>
                  <div style={{ display: "flex", flexWrap: "wrap", gap: 8, marginTop: 4 }}>
                    {ALL_FLAGS.map((f) => (
                      <label key={f} style={{ fontSize: 11, opacity: has("MANAGE_RANKS") ? 1 : 0.5 }}>
                        <input
                          type="checkbox"
                          disabled={!has("MANAGE_RANKS")}
                          checked={r.flags.includes(f)}
                          onChange={(e) => {
                            const flags = e.target.checked ? [...r.flags, f] : r.flags.filter((x) => x !== f);
                            emit(`guild_rank_upsert:${JSON.stringify({ rank: { ...r, flags } })}`);
                          }}
                        />
                        {f}
                      </label>
                    ))}
                  </div>
                  {has("MANAGE_RANKS") && !guild.members.some((m) => m.rank === r.name) && (
                    <Button size="sm" variant="danger" onClick={() => emit(`guild_rank_delete:${r.name}`)}>
                      Supprimer
                    </Button>
                  )}
                </div>
              ))}
              {has("MANAGE_RANKS") && (
                <Button
                  size="sm"
                  className="mt-2"
                  onClick={() => {
                    const n = prompt("Nom du grade ?");
                    if (!n) return;
                    const o = parseInt(prompt("Ordre (0-100) ?") ?? "0", 10) || 0;
                    emit(`guild_rank_upsert:${JSON.stringify({ rank: { name: n, order: o, flags: [] } })}`);
                  }}
                >
                  Nouveau grade
                </Button>
              )}
            </TabsContent>

            <TabsContent value="info">
              <p style={{ fontSize: 12, opacity: 0.7 }}>Fondée le {new Date(guild.createdAtMs).toLocaleDateString()}</p>
              <p style={{ whiteSpace: "pre-wrap", fontSize: 12 }}>{guild.motd || "(aucun message du jour)"}</p>
              {has("EDIT_MOTD") && (
                <div style={{ marginTop: 8 }}>
                  <textarea
                    rows={3}
                    style={{ width: "100%" }}
                    placeholder="Message du jour…"
                    value={motd}
                    onChange={(e) => setMotd(e.target.value)}
                    onKeyDown={(e) => e.stopPropagation()}
                  />
                  <Button size="sm" className="mt-1" onClick={() => emit(`guild_motd:${motd}`)}>
                    Enregistrer
                  </Button>
                </div>
              )}
            </TabsContent>
          </Tabs>
        )}
      </DialogContent>
    </Dialog>
  );
}
