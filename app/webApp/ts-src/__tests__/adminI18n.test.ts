import { afterEach, describe, expect, it, vi } from "vitest";
import { en } from "../admin/i18n/en";
import { fr } from "../admin/i18n/fr";
import { translate } from "../admin/i18n";
import { DEFAULT_LOCALE, LOCALES, loadLocale, saveLocale } from "../admin/i18n/locale";

const KEY = "micraft-admin-locale";

/** Same stand-in as the sidebar suite: this jsdom has no localStorage of its own. */
function fakeStorage(initial: Record<string, string> = {}) {
  const map = new Map(Object.entries(initial));
  return {
    getItem: (key: string) => map.get(key) ?? null,
    setItem: (key: string, value: string) => void map.set(key, value),
    removeItem: (key: string) => void map.delete(key),
    clear: () => map.clear(),
    key: () => null,
    length: 0,
    read: (key: string) => map.get(key) ?? null,
  };
}

afterEach(() => vi.unstubAllGlobals());

describe("admin dictionaries", () => {
  it("translates every English key", () => {
    expect(Object.keys(fr).sort()).toEqual(Object.keys(en).sort());
  });

  it("leaves no entry blank", () => {
    expect(Object.entries(fr).filter(([, value]) => value.trim() === "")).toEqual([]);
  });

  it("keeps the same placeholders in both languages", () => {
    const placeholders = (text: string) => (text.match(/\{\d+\}/g) ?? []).sort();
    const mismatched = Object.keys(en).filter(
      (key) => placeholders(en[key as keyof typeof en]).join() !== placeholders(fr[key as keyof typeof en]).join(),
    );
    expect(mismatched).toEqual([]);
  });
});

describe("translate", () => {
  it("substitutes positional arguments", () => {
    expect(translate("en", "sim.metrics.max", 40)).toBe("max 40");
    expect(translate("fr", "sim.metrics.max", 40)).toBe("max 40");
  });

  it("replaces every occurrence of a placeholder", () => {
    expect(translate("en", "sim.page.arenaSummary", 200, 200, 42, 60)).toBe("200×200 · seed 42 · day 60 s");
  });

  it("leaves an unsupplied placeholder in place rather than printing undefined", () => {
    expect(translate("en", "status.estimated")).toBe("est. {0}");
  });
});

describe("locale persistence", () => {
  it("defaults to English on a first visit", () => {
    vi.stubGlobal("localStorage", fakeStorage());
    expect(loadLocale()).toBe(DEFAULT_LOCALE);
  });

  it("round-trips every supported locale", () => {
    const storage = fakeStorage();
    vi.stubGlobal("localStorage", storage);
    for (const locale of LOCALES) {
      saveLocale(locale);
      expect(storage.read(KEY)).toBe(locale);
      expect(loadLocale()).toBe(locale);
    }
  });

  it("treats an unknown locale in storage as the default", () => {
    vi.stubGlobal("localStorage", fakeStorage({ [KEY]: "kl" }));
    expect(loadLocale()).toBe(DEFAULT_LOCALE);
  });

  it("survives storage being unavailable", () => {
    vi.stubGlobal("localStorage", undefined);
    expect(loadLocale()).toBe(DEFAULT_LOCALE);
    expect(() => saveLocale("fr")).not.toThrow();
  });
});
