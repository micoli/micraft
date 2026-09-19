// Bridges the bare "react-dom/client" specifier to the host's `window.ReactDomClient` — see
// react.js in this same directory for why this must never bundle its own copy.
const ReactDomClient = window.ReactDomClient;

export const createRoot = ReactDomClient.createRoot;
export const hydrateRoot = ReactDomClient.hydrateRoot;
