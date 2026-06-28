# add-i18n

Add a new user-visible string (i18n key).

Key format: `feature:scope:key` where scope is `server` or `client`.

## Checklist

1. Add key to **both** `data/config/i18n/en.yaml` and `data/config/i18n/fr.yaml`
2. Server-side: `context.i18n.t(session.state.language, "feature:server:key", ...args)`
3. Client-side (TypeScript): `window.mcT("feature:client:key")`
