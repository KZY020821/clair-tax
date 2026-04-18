"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import {
  startTransition,
  useEffect,
  useRef,
  useState,
  type ComponentType,
  type ReactNode,
  type SVGProps,
} from "react";
import {
  authSessionQueryKey,
  broadcastAuthEvent,
  fetchAuthSession,
  logoutCurrentSession,
  subscribeToAuthEvents,
} from "../lib/auth";
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
      <path
        d="M8 7.5h8M8 11.5h2M12 11.5h2M16 11.5h0M8 15.5h2M12 15.5h2M16 15.5h0"
        strokeLinecap="round"
        strokeWidth="1.8"
      />
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
  active,
}: Readonly<{ icon: ComponentType<IconProps>; active?: boolean }>) {
  return (
    <span
      className={`flex h-7 w-7 items-center justify-center rounded-md ${
        active ? "bg-white/20" : "bg-brand-ice border border-brand-line"
      }`}
    >
      <Icon className="h-4 w-4" />
    </span>
  );
}

function AuthLoadingScreen({ label }: Readonly<{ label: string }>) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-brand-ice px-4 py-10 sm:px-6">
      <section className="app-panel flex w-full max-w-md flex-col gap-4 p-8 text-center sm:p-10">
        <span className="app-pill-blue self-center">Clair Tax</span>
        <h1 className="text-2xl text-brand-black sm:text-3xl">{label}</h1>
        <p className="text-sm leading-7 text-brand-muted">
          We&apos;re checking the current browser session so your dashboard stays in sync.
        </p>
      </section>
    </div>
  );
}

function AuthErrorScreen({ onRetry }: Readonly<{ onRetry: () => void }>) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-brand-ice px-4 py-10 sm:px-6">
      <section className="app-panel flex w-full max-w-md flex-col gap-4 p-8 text-center sm:p-10">
        <span className="app-pill self-center">Session error</span>
        <h1 className="text-2xl text-brand-black sm:text-3xl">
          We couldn&apos;t reach the sign-in service.
        </h1>
        <p className="text-sm leading-7 text-brand-muted">
          Check that the backend is running, then retry the session check.
        </p>
        <div className="flex justify-center">
          <button type="button" onClick={onRetry} className="app-button-primary">
            Retry session check
          </button>
        </div>
      </section>
    </div>
  );
}

function isActivePath(pathname: string, href: string) {
  return href === "/" ? pathname === href : pathname.startsWith(href);
}

export default function AppShell({ children, currentYear }: AppShellProps) {
  const pathname = usePathname();
  const router = useRouter();
  const queryClient = useQueryClient();
  const [isSidebarOpen, setIsSidebarOpen] = useState(false);
  const hasBroadcastSignedInRef = useRef(false);
  const isLoginRoute = pathname === "/login";

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setIsSidebarOpen(false);
  }, [pathname]);

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

  const authSessionQuery = useQuery({
    queryKey: authSessionQueryKey,
    queryFn: fetchAuthSession,
    retry: false,
    staleTime: 30_000,
  });
  const userYearsQuery = useQuery({
    queryKey: ["user-years"],
    queryFn: fetchUserYears,
    enabled: authSessionQuery.data?.authenticated === true && !isLoginRoute,
  });
  const logoutMutation = useMutation({
    mutationFn: logoutCurrentSession,
    onSuccess: () => {
      hasBroadcastSignedInRef.current = false;
      queryClient.clear();
      broadcastAuthEvent("signed-out");
      startTransition(() => {
        router.replace("/login");
      });
    },
  });

  useEffect(() => {
    return subscribeToAuthEvents(() => {
      void queryClient.invalidateQueries({ queryKey: authSessionQueryKey });
    });
  }, [queryClient]);

  useEffect(() => {
    if (!authSessionQuery.isSuccess) return;

    if (authSessionQuery.data.authenticated) {
      if (!hasBroadcastSignedInRef.current) {
        hasBroadcastSignedInRef.current = true;
        broadcastAuthEvent("signed-in");
      }
      if (isLoginRoute) {
        startTransition(() => router.replace("/"));
      }
      return;
    }

    hasBroadcastSignedInRef.current = false;
    if (!isLoginRoute) {
      startTransition(() => router.replace("/login"));
    }
  }, [authSessionQuery.data, authSessionQuery.isSuccess, isLoginRoute, router]);

  if (isLoginRoute) {
    if (authSessionQuery.isPending || authSessionQuery.data?.authenticated) {
      return <AuthLoadingScreen label="Checking your Clair Tax session..." />;
    }
    return <div className="min-h-screen">{children}</div>;
  }

  if (authSessionQuery.isPending) {
    return <AuthLoadingScreen label="Loading your Clair Tax workspace..." />;
  }

  if (authSessionQuery.isError) {
    return (
      <AuthErrorScreen onRetry={() => void authSessionQuery.refetch()} />
    );
  }

  if (!authSessionQuery.data?.authenticated) {
    return <AuthLoadingScreen label="Redirecting you to sign in..." />;
  }

  const sidebarYears = userYearsQuery.data?.map((y) => y.year) ?? [];
  const currentEmail = authSessionQuery.data.email ?? "Signed in";

  return (
    <div className="flex min-h-screen flex-col">
      {/* ── Header ── */}
      <header className="sticky top-0 z-50 border-b border-brand-blue-dark bg-brand-blue-dark">
        <div className="mx-auto flex w-full max-w-shell items-center justify-between gap-4 px-4 py-3 sm:px-6 lg:px-8">
          <Link
            href="/"
            className="font-display text-2xl font-bold leading-none text-white sm:text-3xl"
          >
            Clair Tax
          </Link>

          <div className="flex items-center gap-2 sm:gap-3">
            {/* Mobile hamburger */}
            <button
              type="button"
              onClick={() => setIsSidebarOpen(true)}
              className="flex h-9 w-9 items-center justify-center rounded-md border border-white/30 text-white transition hover:bg-white/10 lg:hidden"
              aria-label="Open menu"
            >
              <svg className="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />
              </svg>
            </button>

            {/* Email – desktop only */}
            <span className="hidden text-sm text-white/80 sm:block">
              {currentEmail}
            </span>

            {/* Signed-in badge */}
            <span className="hidden items-center rounded-full border border-white/30 bg-white/10 px-3 py-1 text-[11px] font-semibold uppercase tracking-[0.14em] text-white sm:inline-flex">
              Signed in
            </span>

            {/* Logout */}
            <button
              type="button"
              onClick={() => logoutMutation.mutate()}
              disabled={logoutMutation.isPending}
              className="inline-flex items-center justify-center rounded-md border border-white/40 bg-transparent px-4 py-2 text-sm font-semibold text-white transition hover:bg-white hover:text-brand-blue-dark disabled:opacity-50"
            >
              {logoutMutation.isPending ? "Signing out..." : "Log out"}
            </button>
          </div>
        </div>
      </header>

      <div className="mx-auto flex w-full max-w-shell flex-1 flex-col gap-6 px-4 pb-10 pt-6 sm:px-6 lg:flex-row lg:items-start lg:px-8">
        {/* ── Sidebar ── */}
        <aside
          className={`
            fixed left-0 top-0 z-40 h-full w-full max-w-xs
            transform transition-transform duration-300 ease-out
            lg:sticky lg:top-[4.5rem] lg:h-auto lg:w-full lg:max-w-[16rem] lg:transform-none lg:self-start
            ${isSidebarOpen ? "translate-x-0" : "-translate-x-full lg:translate-x-0"}
          `}
        >
          <div className="flex h-full flex-col gap-3 bg-brand-white p-4 shadow-2xl lg:rounded-panel lg:border lg:border-brand-line lg:shadow-panel">
            {/* Close button – mobile only */}
            <button
              type="button"
              onClick={() => setIsSidebarOpen(false)}
              className="self-end rounded-md p-1.5 text-brand-muted transition hover:bg-brand-ice lg:hidden"
              aria-label="Close menu"
            >
              <svg className="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>

            {/* Primary nav */}
            <nav className="space-y-1">
              {(["/" , "/calculator", "/profile"] as const).map((href) => {
                const labels: Record<string, string> = {
                  "/": "Dashboard",
                  "/calculator": "Calculator",
                  "/profile": "Profile",
                };
                const icons: Record<string, ComponentType<IconProps>> = {
                  "/": DashboardIcon,
                  "/calculator": CalculatorIcon,
                  "/profile": ProfileIcon,
                };
                const active = isActivePath(pathname, href);
                return (
                  <Link
                    key={href}
                    href={href}
                    aria-current={active ? "page" : undefined}
                    className={["sidebar-link", active ? "sidebar-link-active" : ""].join(" ").trim()}
                  >
                    <SidebarIcon icon={icons[href]} active={active} />
                    <span>{labels[href]}</span>
                  </Link>
                );
              })}
            </nav>

            <div className="border-t border-brand-line" />

            {/* Years section */}
            <section className="space-y-1">
              <div className="flex items-center justify-between px-4 py-1.5">
                <p className="text-xs font-semibold uppercase tracking-[0.18em] text-brand-muted">Years</p>
                <ChevronDownIcon className="h-4 w-4 text-brand-muted" />
              </div>

              <Link href="/year/create" className="sidebar-action">
                <SidebarIcon icon={PlusCircleIcon} />
                <span>Create New</span>
              </Link>

              <div className="space-y-0.5 px-1">
                {sidebarYears.length > 0 ? (
                  sidebarYears.map((year) => {
                    const href = `/year/${year}`;
                    const active = pathname === href;
                    return (
                      <Link
                        key={year}
                        href={href}
                        aria-current={active ? "page" : undefined}
                        className={["sidebar-year-link", active ? "sidebar-year-link-active" : ""].join(" ").trim()}
                      >
                        {year}
                      </Link>
                    );
                  })
                ) : (
                  <div className="rounded-card border border-dashed border-brand-line px-4 py-3 text-xs leading-5 text-brand-muted">
                    Create a year workspace to pin it here.
                  </div>
                )}
              </div>
            </section>

            {/* Footer links */}
            <div className="mt-auto border-t border-brand-line pt-3">
              <div className="flex flex-col gap-1.5 px-4">
                {[
                  { href: "/", label: "Send feedback" },
                  { href: "/", label: "Privacy policy" },
                  { href: "/", label: "Terms of Service" },
                ].map(({ href, label }) => (
                  <Link
                    key={label}
                    href={href}
                    className="text-xs text-brand-muted transition hover:text-brand-blue"
                  >
                    {label}
                  </Link>
                ))}
              </div>
            </div>
          </div>
        </aside>

        {/* ── Main content ── */}
        <main className="min-w-0 flex-1">
          <div className="mx-auto w-full max-w-content xl:max-w-none">{children}</div>
        </main>

        {/* Mobile overlay */}
        {isSidebarOpen ? (
          <div
            className="fixed inset-0 z-30 bg-brand-black/50 lg:hidden"
            onClick={() => setIsSidebarOpen(false)}
            aria-hidden="true"
          />
        ) : null}
      </div>

      {/* ── Footer ── */}
      <footer className="border-t border-brand-blue-dark bg-brand-blue-dark">
        <div className="mx-auto flex w-full max-w-shell flex-col gap-3 px-4 py-5 sm:px-6 lg:flex-row lg:items-center lg:justify-between lg:px-8">
          <p className="text-sm text-white/70">
            Clair Tax keeps tax work simple, clean, and easy to review.
          </p>
          <div className="flex flex-wrap items-center gap-4">
            <span className="text-sm text-white/60">© {currentYear} Clair Tax</span>
            {[
              { href: "/", label: "Dashboard" },
              { href: "/calculator", label: "Calculator" },
              { href: "/profile", label: "Profile" },
            ].map(({ href, label }) => (
              <Link
                key={label}
                href={href}
                className="text-sm font-medium text-white transition hover:text-white/70"
              >
                {label}
              </Link>
            ))}
          </div>
        </div>
      </footer>
    </div>
  );
}
