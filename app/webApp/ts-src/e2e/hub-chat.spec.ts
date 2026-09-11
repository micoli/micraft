import { test, type Page } from "@playwright/test";
import { accountFor, E2eAccount, expect } from "./helpers/connectClient";
import { createPlayer, CreatedPlayer } from "./helpers/admin";

/**
 * Drives the `/hub` web companion the way a player would — real clicks and typing, no raw
 * WebSocket injection (same rule as the game specs). Two hub tabs in the same isolated,
 * memory-only E2E world (?gameSession=, matching `/game`'s own convention — see
 * GameWorldRegistry.resolve) exchange a chat message over the hub's own WS, independently of the
 * 3D client.
 *
 * `playerName`/`playerId` ride the URL into sessionStorage (hubTestOverrides.ts): the account
 * has no on-disk player file for `GET /api/players/by-email` to find in a memory-only world, so
 * the hub is told the reserved character directly instead of discovering it.
 */
test("two hub tabs exchange a world-channel chat message", async ({ browser }, info) => {
  const a = accountFor(info, "a");
  const b = { ...accountFor(info, "b"), session: a.session };

  const [playerA, playerB] = await Promise.all([
    createPlayer(a, { characterClass: null }),
    createPlayer(b, { characterClass: null }),
  ]);

  const [ctxA, ctxB] = await Promise.all([browser.newContext(), browser.newContext()]);
  const [pageA, pageB] = await Promise.all([ctxA.newPage(), ctxB.newPage()]);

  async function loginToChat(page: Page, acct: E2eAccount, player: CreatedPlayer) {
    await page.goto(
      `/hub/login?gameSession=${acct.session}&playerName=${encodeURIComponent(player.name)}&playerId=${player.playerId}`,
    );
    await page.getByPlaceholder("your@email.com").fill(acct.email);
    await page.getByRole("button", { name: "Continue" }).click();
    await page.getByRole("link", { name: "Chat" }).click();
    await expect(page.getByPlaceholder("Message #world…")).toBeVisible();
  }

  await Promise.all([loginToChat(pageA, a, playerA), loginToChat(pageB, b, playerB)]);

  const input = pageA.getByPlaceholder("Message #world…");
  await input.fill("hello from A");
  await pageA.getByRole("button", { name: "Send" }).click();

  await expect(pageA.getByText(`${playerA.name}: hello from A`)).toBeVisible();
  await expect(pageB.getByText(`${playerA.name}: hello from A`)).toBeVisible();

  await ctxA.close();
  await ctxB.close();
});
