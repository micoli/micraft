// Bridges the bare "react/jsx-runtime" specifier to the host's `window.ReactJsxRuntime` — see
// react.js in this same directory for why this must never bundle its own copy.
const ReactJsxRuntime = window.ReactJsxRuntime;

export const jsx = ReactJsxRuntime.jsx;
export const jsxs = ReactJsxRuntime.jsxs;
export const Fragment = ReactJsxRuntime.Fragment;
