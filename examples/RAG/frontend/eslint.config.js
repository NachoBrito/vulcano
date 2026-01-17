import html from "eslint-plugin-html";
import prettierConfig from "eslint-config-prettier";

export default [
  {
    ignores: ["dist/**"],
  },
  {
    files: ["**/*.js"],
    languageOptions: {
      ecmaVersion: "latest",
      sourceType: "module",
    },
    rules: {
      "no-console": "warn",
      "no-unused-vars": ["error", { "argsIgnorePattern": "^_" }],
    },
  },
  {
    files: ["**/*.html"],
    plugins: {
      html,
    },
  },
  prettierConfig,
];
