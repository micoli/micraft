/// <reference path="../../global.d.ts" />
import { Page } from "@playwright/test";
import type { E2eActions } from "../../game/lib/e2eBridge";

type PageBound<T> = {
  [K in keyof T]: T[K] extends (...a: infer A) => infer R ? (...a: A) => Promise<Awaited<R>> : never;
};

/**
 * Typed passthrough to `window.mcE2E.actions.*` — `await actions(page).setLook(0, 1.4)`.
 * Every method mirrors `E2eActions`; adding one there exposes it here automatically.
 */
export function actions(page: Page): PageBound<E2eActions> {
  return new Proxy({} as PageBound<E2eActions>, {
    get:
      (_t, name: string) =>
      (...args: unknown[]) =>
        page.evaluate(
          ([n, a]) => {
            const fn = (window.mcE2E?.actions as unknown as Record<string, (...x: unknown[]) => unknown>)?.[n];
            if (!fn) throw new Error(`mcE2E.actions.${n} unavailable`);
            return fn(...a);
          },
          [name, args] as [string, unknown[]],
        ),
  });
}

export async function targetBlockIsNot(page: Page, target: { x: number; y: number; z: number }, blockName: string) {
  return await page.waitForFunction(
    ({ t, name }) =>
      window.mcE2E?.lastWorldUpdate?.some(
        (c) => c.x === t.x && c.y === t.y && c.z === t.z && c.block.toLowerCase() !== name.toLowerCase(),
      ) ?? false,
    { t: target, name: blockName },
    { timeout: 15_000, polling: 100 },
  );
}

export async function targetBlockIs(page: Page, target: { x: number; y: number; z: number }, blockType: string) {
  return await page.waitForFunction(
    ({ t, type }) =>
      window.mcE2E?.lastWorldUpdate?.some(
        (c) => c.x === t.x && c.y === t.y && c.z === t.z && c.block.toLowerCase() === type.toLowerCase(),
      ) ?? false,
    { t: target, type: blockType },
    { timeout: 15_000, polling: 100 },
  );
}

export async function creativePlaceBlock(page: Page, target: { x: number; y: number; z: number }, blockType: string) {
  return await page.evaluate(
    ({ t, type }) => window.mcState.events.push(`creative_place:${t.x},${t.y},${t.z},${type},0`),
    { t: target, type: blockType },
  );
}

export async function waitForInventoryContains(page: Page, blockType: string, numberOfItems: number) {
  return await page.waitForFunction(
    ({ blockType, numberOfItems }) =>
      Object.entries(window.mcE2E?.inventory ?? {}).some(
        ([k, v]) => k.toLowerCase() === blockType.toLowerCase() && v >= numberOfItems,
      ),
    { blockType, numberOfItems },
    { timeout: 10_000, polling: 100 },
  );
}
