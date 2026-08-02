const LOCALE_STORAGE_KEY = "micraft-admin-locale";

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

/**
 * Locale the admin UI renders in.
 *
 * Persisted next to the sidebar collapse flag, for the same reason: it is chrome, and an operator who
 * picked a language should not re-pick it on every reload. Unreadable storage falls back to English —
 * the source language of the dictionaries, so it is the one guaranteed to be complete.
 */
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
