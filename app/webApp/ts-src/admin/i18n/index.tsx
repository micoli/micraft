import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { en, type TranslationKey } from "./en";
import { fr } from "./fr";
import { DEFAULT_LOCALE, loadLocale, saveLocale, type Locale } from "./locale";

export { LOCALES, LOCALE_LABELS, type Locale } from "./locale";
export type { TranslationKey } from "./en";

const DICTIONARIES: Record<Locale, Record<TranslationKey, string>> = { en, fr };

export type Translate = (key: TranslationKey, ...args: (string | number)[]) => string;

/** Substitute `{0}`, `{1}`… — same placeholder convention as the game client. */
export function translate(locale: Locale, key: TranslationKey, ...args: (string | number)[]): string {
  const template = DICTIONARIES[locale][key] ?? en[key] ?? key;
  return args.reduce<string>((text, arg, index) => text.split(`{${index}}`).join(String(arg)), template);
}

interface I18nValue {
  locale: Locale;
  setLocale: (locale: Locale) => void;
  t: Translate;
}

// Defaulted rather than nullable: a component rendered outside the provider (a test mounting one
// panel on its own, say) still renders readable English instead of throwing.
const I18nContext = createContext<I18nValue>({
  locale: DEFAULT_LOCALE,
  setLocale: () => {},
  t: (key, ...args) => translate(DEFAULT_LOCALE, key, ...args),
});

export function I18nProvider({ children }: { children: React.ReactNode }) {
  const [locale, setLocale] = useState<Locale>(() => loadLocale());

  useEffect(() => saveLocale(locale), [locale]);

  const t = useCallback<Translate>((key, ...args) => translate(locale, key, ...args), [locale]);

  const value = useMemo(() => ({ locale, setLocale, t }), [locale, t]);

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}

export function useI18n(): I18nValue {
  return useContext(I18nContext);
}

/** Shorthand for the common case of only needing the translation function. */
export function useT(): Translate {
  return useContext(I18nContext).t;
}
