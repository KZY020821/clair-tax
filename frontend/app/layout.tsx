import type { ReactNode } from "react";
import type { Metadata } from "next";
import "./globals.css";
import AppShell from "./components/app-shell";
import Providers from "./providers";

export const metadata: Metadata = {
  title: "Clair Tax",
  description: "Malaysian personal tax workspace for policy, relief, and filing review.",
  icons: {
    icon: "/logo-white.png",
  },
};

type RootLayoutProps = Readonly<{
  children: ReactNode;
}>;

export default function RootLayout({ children }: RootLayoutProps) {
  return (
    <html lang="en">
      <body>
        <Providers>
          <AppShell>{children}</AppShell>
        </Providers>
      </body>
    </html>
  );
}
