import { getJestConfig } from "@storybook/test-runner";

const defaultConfig = getJestConfig();

export default {
  ...defaultConfig,
  modulePathIgnorePatterns: [
    ...(defaultConfig.modulePathIgnorePatterns ?? []),
    "<rootDir>/build/",
    "<rootDir>/app/webApp/build/",
  ],
};
