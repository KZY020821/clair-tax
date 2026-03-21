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
        panel: "0 18px 40px rgba(0, 0, 0, 0.05)",
        accent: "0 18px 34px rgba(94, 155, 255, 0.16)",
        inner: "inset 0 1px 0 rgba(255, 255, 255, 0.8)",
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
