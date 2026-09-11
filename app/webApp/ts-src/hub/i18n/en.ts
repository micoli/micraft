/**
 * Source dictionary for the web companion (`/hub`).
 *
 * English is the source language: every other locale is typed against these keys, so a missing
 * translation is a compile error rather than a blank label discovered in production.
 *
 * Placeholders are `{0}`, `{1}`… — same convention as the game client's `window.mc.t`.
 */
export const en = {
  // ── Shell ───────────────────────────────────────────────────────────────────
  "shell.title": "MiCraft Companion",
  "shell.nav.mail": "Mail",
  "shell.nav.auction": "Auction House",
  "shell.nav.chat": "Chat",
  "shell.nav.claims": "Land Claims",
  "shell.language": "Language",
  "shell.logout": "Log out",
  "shell.connecting": "Connecting…",
  "shell.reconnecting": "Reconnecting…",
  "shell.disconnected": "Disconnected",
  "shell.supersededTitle": "Session opened elsewhere",
  "shell.supersededBody": "This companion session was replaced by another connection for the same character.",
  "shell.reconnect": "Reconnect",

  // ── Auth ────────────────────────────────────────────────────────────────────
  "auth.email": "Email",
  "auth.password": "Password",
  "auth.login": "Log in",
  "auth.loggingIn": "Logging in…",
  "auth.continue": "Continue",
  "auth.connecting": "Connecting…",
  "auth.invalidCredentials": "Invalid email or password.",
  "auth.invalidEmail": "Please enter a valid email address.",
  "auth.connectionError": "Connection error. Is the server running?",
  "auth.continueWithGoogle": "Continue with Google",
  "auth.modeLocal": "Local auth",
  "auth.modeOauth": "OAuth",
  "auth.modeNone": "Open server",

  // ── Placeholder screens (filled in as each feature lands) ──────────────────
  "placeholder.comingSoon": "This screen isn't built yet.",
} as const;

export type TranslationKey = keyof typeof en;
