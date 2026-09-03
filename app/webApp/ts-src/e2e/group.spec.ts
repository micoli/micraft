/// <reference path="../global.d.ts" />
import { test } from "@playwright/test";
import { Browser, Page, TestInfo } from "@playwright/test";
import { accountFor, connectClient, e2e, expect } from "./helpers/connectClient";
import { actions } from "./helpers/game";

function groupPanel(page: Page) {
  return page.getByRole("dialog").filter({ hasText: "Groupe" });
}

/** Alt+KeyG — the default `group_panel` keybinding. */
async function openGroupPanel(page: Page) {
  await page.keyboard.press("Alt+KeyG");
  const panel = groupPanel(page);
  await expect(panel).toBeVisible();
  return panel;
}

async function waitForGroupMembers(page: Page, names: string[]): Promise<void> {
  await page.waitForFunction(
    (expected) => {
      const g = window.mcE2E?.group;
      if (!g || g.members.length !== expected.length) return false;
      const got = g.members.map((m) => m.playerName).sort();
      return expected
        .slice()
        .sort()
        .every((n, i) => got[i] === n);
    },
    names,
    { timeout: 20_000, polling: 200 },
  );
}

async function expectNotification(page: Page, needle: string): Promise<void> {
  await expect
    .poll(async () => (await e2e(page)).notifications, { timeout: 10_000 })
    .toEqual(expect.arrayContaining([expect.stringContaining(needle)]));
}

/**
 * The two ways a real player drives the Group feature. The test body is written once against
 * this interface; each mode plugs in its own inputs and its own "what the player now sees".
 */
interface GroupDriver {
  createGroupe(leader: Page): Promise<void>;
  invite(leader: Page, targetName: string): Promise<void>;
  acceptInvitation(invitee: Page): Promise<void>;
  /** Assert the finished 3-member roster is surfaced to every client, the player's way. */
  assertRosterSurfaced(pages: Page[], members: string[], leaderName: string): Promise<void>;
}

const guiDriver: GroupDriver = {
  async createGroupe(leader) {
    await openGroupPanel(leader);
    await groupPanel(leader).getByRole("button", { name: "Créer un groupe" }).click();
  },
  async invite(leader, targetName) {
    leader.once("dialog", (d) => d.accept(targetName));
    await groupPanel(leader).getByRole("button", { name: "Inviter" }).click();
  },
  async acceptInvitation(invitee) {
    await (await openGroupPanel(invitee)).getByRole("button", { name: "Accepter" }).click();
  },
  async assertRosterSurfaced(pages, members) {
    for (const page of pages) {
      for (const name of members) {
        await expect(groupPanel(page).getByText(name, { exact: false })).toBeVisible();
      }
    }
  },
};

const commandDriver: GroupDriver = {
  async createGroupe(leader) {
    await actions(leader).runCommand("/group create");
  },
  async invite(leader, targetName) {
    await actions(leader).runCommand(`/group invite ${targetName}`);
  },
  async acceptInvitation(invitee) {
    // No panel to gate on: retry until the pending invite has landed and the accept takes.
    await expect
      .poll(
        async () => {
          await actions(invitee).runCommand("/group accept");
          return (await e2e(invitee)).group != null;
        },
        { timeout: 15_000, intervals: [500] },
      )
      .toBe(true);
  },
  async assertRosterSurfaced(pages, members, leaderName) {
    const [leader, ...invitees] = pages;
    for (const name of members.filter((n) => n !== leaderName)) {
      await expectNotification(leader, name);
    }
    for (const invitee of invitees) {
      await expectNotification(invitee, leaderName);
    }
  },
};

async function formsGroupAndTwoOthersJoin(browser: Browser, info: TestInfo, driver: GroupDriver) {
  const a = accountFor(info, "a");
  const b = { ...accountFor(info, "b"), session: a.session };
  const c = { ...accountFor(info, "c"), session: a.session };

  const contexts = await Promise.all([browser.newContext(), browser.newContext(), browser.newContext()]);
  const [pageA, pageB, pageC] = await Promise.all(contexts.map((ctx) => ctx.newPage()));

  // Group is pure UI + server state — no movement, no terrain. `noWorld` tells the server not to
  // stream the world at all (needsWorld=false) and skips the world-ready waits. Three (here, six
  // across the two parallel tests) headless Babylon contexts under CI's software GL still need
  // generous slack.
  const opts = { noWorld: true, recenter: false, timeoutScale: process.env.CI ? 2 : 1 } as const;
  await connectClient(pageA, a, opts);
  await connectClient(pageB, b, opts);
  await connectClient(pageC, c, opts);

  const idA = (await e2e(pageA)).playerId;

  await driver.createGroupe(pageA);
  await pageA.waitForFunction((leaderId) => window.mcE2E?.group?.leaderId === leaderId, idA, {
    timeout: 15_000,
    polling: 200,
  });

  await driver.invite(pageA, b.charName);
  await driver.acceptInvitation(pageB);

  await driver.invite(pageA, c.charName);
  await driver.acceptInvitation(pageC);

  // Server-side truth: all three clients received a GroupSync with the same 3 members.
  const everyone = [a.charName, b.charName, c.charName];
  await waitForGroupMembers(pageA, everyone);
  await waitForGroupMembers(pageB, everyone);
  await waitForGroupMembers(pageC, everyone);

  for (const [page, self] of [
    [pageA, a],
    [pageB, b],
    [pageC, c],
  ] as const) {
    const g = (await e2e(page)).group!;
    expect(g.leaderName, `${self.charName} sees A as leader`).toBe(a.charName);
    expect(g.members.map((m) => m.playerName).sort()).toEqual(everyone.slice().sort());
    expect(g.members.every((m) => m.online)).toBe(true);
  }

  await driver.assertRosterSurfaced([pageA, pageB, pageC], everyone, a.charName);

  await Promise.all(contexts.map((ctx) => ctx.close()));
}

test("a player forms a group and two others join it (gui)", async ({ browser }, info) => {
  test.setTimeout(process.env.CI ? 240_000 : 60_000);
  await formsGroupAndTwoOthersJoin(browser, info, guiDriver);
});

test("a player forms a group and two others join it (command)", async ({ browser }, info) => {
  test.setTimeout(process.env.CI ? 240_000 : 60_000);
  await formsGroupAndTwoOthersJoin(browser, info, commandDriver);
});
