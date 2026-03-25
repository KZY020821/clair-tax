"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState, type ComponentType, type ReactNode, type SVGProps } from "react";
import { fetchDevCurrentUser } from "../lib/receipts";
import { fetchUserYears } from "../lib/user-years";

type AppShellProps = Readonly<{
  children: ReactNode;
  currentYear: number;
}>;

type IconProps = SVGProps<SVGSVGElement>;

function DashboardIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" {...props}>
      <rect x="4" y="4" width="6" height="6" rx="1.2" strokeWidth="1.8" />
      <rect x="14" y="4" width="6" height="9" rx="1.2" strokeWidth="1.8" />
      <rect x="4" y="14" width="6" height="6" rx="1.2" strokeWidth="1.8" />
      <rect x="14" y="17" width="6" height="3" rx="1.2" strokeWidth="1.8" />
    </svg>
  );
}

function PlusCircleIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" {...props}>
      <circle cx="12" cy="12" r="8.5" strokeWidth="1.8" />
      <path d="M12 8v8M8 12h8" strokeLinecap="round" strokeWidth="1.8" />
    </svg>
  );
}

function CalculatorIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" {...props}>
      <rect x="5" y="3.5" width="14" height="17" rx="2" strokeWidth="1.8" />
      <path d="M8 7.5h8M8 11.5h2M12 11.5h2M16 11.5h0M8 15.5h2M12 15.5h2M16 15.5h0" strokeLinecap="round" strokeWidth="1.8" />
    </svg>
  );
}

function ProfileIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" {...props}>
      <circle cx="12" cy="8" r="3.25" strokeWidth="1.8" />
      <path
        d="M5 18.5c1.7-3 4.1-4.5 7-4.5s5.3 1.5 7 4.5"
        strokeLinecap="round"
        strokeWidth="1.8"
      />
      <circle cx="12" cy="12" r="8.5" strokeWidth="1.8" />
    </svg>
  );
}

function ChevronDownIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" {...props}>
      <path
        d="m7.5 10 4.5 4.5L16.5 10"
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth="1.8"
      />
    </svg>
  );
}

function SidebarIcon({
  icon: Icon,
}: Readonly<{ icon: ComponentType<IconProps> }>) {
  return (
    <span className="flex h-8 w-8 items-center justify-center rounded-full border border-brand-line bg-brand-white">
      <Icon className="h-4 w-4" />
    </span>
  );
}

function isActivePath(pathname: string, href: string) {
  return href === "/" ? pathname === href : pathname.startsWith(href);
}

function FooterTextLink({
  href,
  label,
}: Readonly<{
  href: string;
  label: string;
}>) {
  return (
    <Link
      href={href}
      className="text-sm text-brand-muted transition hover:text-brand-black"
    >
      {label}
    </Link>
  );
}

export default function AppShell({ children, currentYear }: AppShellProps) {
  const pathname = usePathname();
  const [isSidebarOpen, setIsSidebarOpen] = useState(false);

  // Auto-close sidebar on route change (mobile)
  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setIsSidebarOpen(false);
  }, [pathname]);

  // Prevent body scroll when sidebar is open on mobile
  useEffect(() => {
    if (isSidebarOpen && typeof window !== "undefined" && window.innerWidth < 1024) {
      document.body.style.overflow = "hidden";
    } else {
      document.body.style.overflow = "";
    }

    return () => {
      document.body.style.overflow = "";
    };
  }, [isSidebarOpen]);

  const userYearsQuery = useQuery({
    queryKey: ["user-years"],
    queryFn: fetchUserYears,
  });
  const devUserQuery = useQuery({
    queryKey: ["dev-current-user"],
    queryFn: fetchDevCurrentUser,
  });

  const sidebarYears = userYearsQuery.data?.map((userYear) => userYear.year) ?? [];
  const currentEmail = devUserQuery.data?.email ?? "dev@taxrelief.local";

  return (
    <div className="flex min-h-screen flex-col">
      <header className="sticky top-0 z-50 border-b border-brand-line bg-brand-white/95 backdrop-blur">
        <div className="mx-auto flex w-full max-w-shell items-center justify-between gap-6 px-4 py-4 sm:px-6 lg:px-8">
          <Link href="/" className="font-display text-4xl leading-none text-brand-black">
            Clair Tax
          </Link>

          <div className="flex items-center gap-3">
            <button
              type="button"
              onClick={() => setIsSidebarOpen(true)}
              className="flex h-10 w-10 items-center justify-center rounded-full border border-brand-line bg-brand-white transition hover:bg-brand-ice lg:hidden"
              aria-label="Open menu"
            >
              <svg className="h-5 w-5 text-brand-black" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />
              </svg>
            </button>

            <span className="hidden text-sm font-medium text-brand-black sm:block">
              {currentEmail}
            </span>
            <span className="app-pill-blue">Dev mode</span>
          </div>
        </div>
      </header>

      <div className="mx-auto flex w-full max-w-shell flex-1 flex-col gap-6 px-4 pb-8 pt-6 sm:px-6 lg:flex-row lg:items-start lg:px-8">
        <aside
          className={`
            fixed left-0 top-0 z-40 h-full w-full max-w-sm
            transform transition-transform duration-300 ease-out
            lg:sticky lg:top-24 lg:h-auto lg:w-full lg:max-w-[17.5rem] lg:transform-none lg:self-start
            ${isSidebarOpen ? "translate-x-0" : "-translate-x-full lg:translate-x-0"}
          `}
        >
          <div className="flex h-full flex-col gap-4 rounded-none border-r border-brand-line bg-brand-white p-4 shadow-2xl lg:rounded-panel lg:border lg:bg-brand-sidebar/85 lg:shadow-panel">
            <button
              type="button"
              onClick={() => setIsSidebarOpen(false)}
              className="self-end rounded-full p-2 transition hover:bg-brand-ice lg:hidden"
              aria-label="Close menu"
            >
              <svg className="h-5 w-5 text-brand-black" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>

            <nav className="space-y-1.5">
              <Link
                href="/"
                aria-current={isActivePath(pathname, "/") ? "page" : undefined}
                className={[
                  "sidebar-link",
                  isActivePath(pathname, "/") ? "sidebar-link-active" : "",
                ]
                  .join(" ")
                  .trim()}
              >
                <SidebarIcon icon={DashboardIcon} />
                <span>Dashboard</span>
              </Link>
            </nav>

            <div className="border-t border-brand-line" />

            <section className="space-y-1.5">
              <div className="flex items-center justify-between px-4 py-2">
                <p className="text-sm font-semibold text-brand-black">Years</p>
                <ChevronDownIcon className="h-4 w-4 text-brand-black" />
              </div>

              <Link href="/year/create" className="sidebar-action">
                <SidebarIcon icon={PlusCircleIcon} />
                <span>Create New</span>
              </Link>

              <div className="space-y-1 px-2">
                {sidebarYears.length > 0 ? (
                  sidebarYears.map((year) => {
                    const href = `/year/${year}`;
                    const active = pathname === href;

                    return (
                      <Link
                        key={year}
                        href={href}
                        aria-current={active ? "page" : undefined}
                        className={[
                          "sidebar-year-link",
                          active ? "sidebar-year-link-active" : "",
                        ]
                          .join(" ")
                          .trim()}
                      >
                        {year}
                      </Link>
                    );
                  })
                ) : (
                  <div className="rounded-card border border-dashed border-brand-line px-4 py-3 text-sm leading-6 text-brand-muted">
                    Create a year workspace to pin it here.
                  </div>
                )}
              </div>
            </section>

            <div className="border-t border-brand-line" />

            <nav className="space-y-1.5">
              <Link
                href="/calculator"
                aria-current={isActivePath(pathname, "/calculator") ? "page" : undefined}
                className={[
                  "sidebar-link",
                  isActivePath(pathname, "/calculator") ? "sidebar-link-active" : "",
                ]
                  .join(" ")
                  .trim()}
              >
                <SidebarIcon icon={CalculatorIcon} />
                <span>Calculator</span>
              </Link>

              <Link
                href="/profile"
                aria-current={isActivePath(pathname, "/profile") ? "page" : undefined}
                className={[
                  "sidebar-link",
                  isActivePath(pathname, "/profile") ? "sidebar-link-active" : "",
                ]
                  .join(" ")
                  .trim()}
              >
                <SidebarIcon icon={ProfileIcon} />
                <span>Profile</span>
              </Link>
            </nav>

            <div className="mt-8 space-y-4 px-4 pb-1 pt-10">
              <FooterTextLink href="/" label="Send feedback" />
              <FooterTextLink href="/" label="Privacy policy" />
              <FooterTextLink href="/" label="Terms of Service" />
            </div>
          </div>
        </aside>

        <main className="min-w-0 flex-1">
          <div className="mx-auto w-full max-w-content xl:max-w-none">{children}</div>
        </main>

        {/* Backdrop overlay for mobile sidebar */}
        {isSidebarOpen ? (
          <div
            className="fixed inset-0 z-30 bg-black/40 backdrop-blur-sm transition-opacity lg:hidden"
            onClick={() => setIsSidebarOpen(false)}
            aria-hidden="true"
          />
        ) : null}
      </div>

      <footer className="border-t border-brand-line bg-brand-white">
        <div className="mx-auto flex w-full max-w-shell flex-col gap-3 px-4 py-5 text-sm text-brand-muted sm:px-6 lg:flex-row lg:items-center lg:justify-between lg:px-8">
          <p>Clair Tax keeps tax work simple, clean, and easy to review.</p>
          <div className="flex flex-wrap items-center gap-4">
            <span>© {currentYear} Clair Tax</span>
            <Link href="/" className="font-medium text-brand-black transition hover:text-brand-blue">
              Dashboard
            </Link>
            <Link
              href="/calculator"
              className="font-medium text-brand-black transition hover:text-brand-blue"
            >
              Calculator
            </Link>
            <Link
              href="/profile"
              className="font-medium text-brand-black transition hover:text-brand-blue"
            >
              Profile
            </Link>
            <Link
              href="/year/create"
              className="font-medium text-brand-black transition hover:text-brand-blue"
            >
              Years
            </Link>
          </div>
        </div>
      </footer>
    </div>
  );
}
