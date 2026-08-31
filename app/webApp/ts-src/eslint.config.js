import js from "@eslint/js";
import tseslint from "typescript-eslint";
import reactPlugin from "eslint-plugin-react";
import reactHooks from "eslint-plugin-react-hooks";
import prettierConfig from "eslint-config-prettier";
import globals from "globals";

export default tseslint.config(
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    files: ["**/*.tsx"],
    ignores: ["**/*.stories.tsx"],
    plugins: { react: reactPlugin },
    rules: {
      "react/no-multi-comp": ["error", { ignoreStateless: false }],
    },
  },
  {
    files: ["**/*.{ts,tsx}"],
    plugins: {
      react: reactPlugin,
      "react-hooks": reactHooks,
    },
    languageOptions: {
      parserOptions: {
        ecmaFeatures: { jsx: true },
      },
      globals: {
        window: "readonly",
        document: "readonly",
        console: "readonly",
      },
    },
    settings: {
      react: { version: "detect" },
    },
    rules: {
      ...reactPlugin.configs.recommended.rules,
      ...reactHooks.configs.recommended.rules,
      "react/react-in-jsx-scope": "off",
      "react-hooks/set-state-in-effect": "off",
      "@typescript-eslint/no-explicit-any": "warn",
      "@typescript-eslint/no-unused-vars": ["warn", { argsIgnorePattern: "^_" }],
      "no-else-return": ["warn", { allowElseIf: false }],
    },
  },
  {
    files: ["scripts/**/*.mjs"],
    languageOptions: {
      globals: {
        ...globals.node,
      },
    },
  },
  {
    // e2e specs pull the app's window augmentation (window.mcE2E, window.mcState) in
    // via a triple-slash reference to ../global.d.ts — that is the correct tool for an
    // ambient .d.ts, and an `import` of it breaks the Playwright runtime loader.
    files: ["e2e/**/*.ts"],
    rules: {
      "@typescript-eslint/triple-slash-reference": "off",
    },
  },
  prettierConfig,
  {
    ignores: [
      "node_modules/**",
      "storybook-static/**",
      "../src/wasmJsMain/resources/mc_bindings.js",
      "../build/**",
      "app/webApp/build/**",
      "generated/**",
    ],
  },
);
