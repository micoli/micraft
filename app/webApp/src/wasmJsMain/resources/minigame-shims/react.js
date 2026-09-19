// Bridges the bare "react" specifier (used by independently-built mini-game bundles under
// app/minigames/<name>/) to the host's own React instance, exposed as `window.React` by
// app/webApp/ts-src/index.ts. Mapped via the import map in index.html/admin.html.
// Never bundle a second copy of React here — that would break hooks (two instances can't share
// a dispatcher).
const React = window.React;

export default React;
export const {
  Children,
  Component,
  Fragment,
  PureComponent,
  StrictMode,
  Suspense,
  cloneElement,
  createContext,
  createElement,
  createRef,
  forwardRef,
  isValidElement,
  lazy,
  memo,
  startTransition,
  useCallback,
  useContext,
  useDebugValue,
  useDeferredValue,
  useEffect,
  useId,
  useImperativeHandle,
  useLayoutEffect,
  useMemo,
  useReducer,
  useRef,
  useState,
  useSyncExternalStore,
  useTransition,
  version,
} = React;
