import type { Config } from "tailwindcss";

const config: Config = {
  content: [
    "./app/**/*.{js,ts,jsx,tsx,mdx}",
    "./components/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      colors: {
        brand: {
          white: "rgb(var(--brand-white) / <alpha-value>)",
          black: "rgb(var(--brand-black) / <alpha-value>)",
          blue: "rgb(var(--brand-blue) / <alpha-value>)",
          "blue-dark": "rgb(var(--brand-blue-dark) / <alpha-value>)",
          ice: "rgb(var(--brand-ice) / <alpha-value>)",
          sidebar: "rgb(var(--brand-sidebar) / <alpha-value>)",
          line: "rgb(var(--brand-line) / <alpha-value>)",
          "line-strong": "rgb(var(--brand-line-strong) / <alpha-value>)",
          muted: "rgb(var(--brand-muted) / <alpha-value>)",
        },
      },
      fontFamily: {
        sans: ['"Avenir Next"', '"Segoe UI"', '"Helvetica Neue"', "sans-serif"],
        display: ['"Avenir Next"', '"Segoe UI"', '"Helvetica Neue"', "sans-serif"],
      },
      borderRadius: {
        shell: "var(--radius-shell)",
        panel: "var(--radius-panel)",
        card: "var(--radius-card)",
        field: "var(--radius-field)",
      },
      boxShadow: {
        panel: "0 1px 3px rgba(0,0,0,0.06), 0 4px 16px rgba(37,99,235,0.06)",
        accent: "0 4px 20px rgba(37,99,235,0.18)",
        inner: "inset 0 1px 3px rgba(0,0,0,0.08)",
      },
      maxWidth: {
        shell: "92rem",
        content: "68rem",
      },
    },
  },
  plugins: [],
};

export default config;
