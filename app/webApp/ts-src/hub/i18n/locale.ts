const LOCALE_STORAGE_KEY = "micraft-hub-locale";

export const LOCALES = ["en", "fr"] as const;

export type Locale = (typeof LOCALES)[number];

export const LOCALE_LABELS: Record<Locale, string> = {
  en: "English",
  fr: "Français",
};

export const DEFAULT_LOCALE: Locale = "en";

function isLocale(value: string | null): value is Locale {
  return value != null && (LOCALES as readonly string[]).includes(value);
}

/** Persisted separately from the game client's `micraft_last_lang` — the hub can run standalone. */
export function loadLocale(): Locale {
  try {
    const stored = localStorage.getItem(LOCALE_STORAGE_KEY);
    return isLocale(stored) ? stored : DEFAULT_LOCALE;
  } catch {
    return DEFAULT_LOCALE;
  }
}

export function saveLocale(locale: Locale) {
  try {
    localStorage.setItem(LOCALE_STORAGE_KEY, locale);
  } catch {
    /* storage unavailable — the choice stays session-only */
  }
}
