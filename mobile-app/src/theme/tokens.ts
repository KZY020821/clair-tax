export const colors = {
  white: "#FFFFFF",
  black: "#000000",
  blue: "#5E9BFF",
  ice: "#F5F9FF",
  sidebar: "#EFF5FF",
  line: "#D6E0F1",
  lineStrong: "#A1B3D1",
  muted: "#4F5866",
  dangerBg: "#FFF4F4",
  dangerBorder: "#F4C8C8",
  dangerText: "#A33A3A",
  overlay: "rgba(0, 0, 0, 0.4)",
} as const;

export const radii = {
  shell: 28,
  panel: 24,
  card: 20,
  field: 16,
  pill: 999,
} as const;

export const shadows = {
  panel: {
    shadowColor: "#000000",
    shadowOpacity: 0.08,
    shadowRadius: 18,
    shadowOffset: { width: 0, height: 10 },
    elevation: 3,
  },
  accent: {
    shadowColor: "#5E9BFF",
    shadowOpacity: 0.18,
    shadowRadius: 18,
    shadowOffset: { width: 0, height: 10 },
    elevation: 5,
  },
} as const;

export const spacing = {
  shellHorizontal: 20,
  panelPadding: 20,
  sectionGap: 20,
} as const;
