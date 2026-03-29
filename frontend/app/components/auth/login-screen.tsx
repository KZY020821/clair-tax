"use client";

import { useMutation } from "@tanstack/react-query";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useState, type FormEvent } from "react";
import { requestMagicLink } from "../../lib/auth";

const MAGIC_LINK_STATUS_MESSAGES: Record<string, string> = {
  invalid: "That sign-in link is no longer valid. Request a new one to continue.",
  expired: "That sign-in link has expired. Request a fresh email to continue.",
  used: "That sign-in link was already used. Request another email if you need to sign in again.",
};

export default function LoginScreen() {
  const searchParams = useSearchParams();
  const [email, setEmail] = useState("");
  const [submittedEmail, setSubmittedEmail] = useState<string | null>(null);

  const magicLinkStatus = searchParams.get("magicLink");
  const magicLinkMessage = magicLinkStatus
    ? MAGIC_LINK_STATUS_MESSAGES[magicLinkStatus] ?? null
    : null;

  const requestMagicLinkMutation = useMutation({
    mutationFn: requestMagicLink,
  });

  const isSuccessState = submittedEmail !== null;

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    const normalizedEmail = email.trim().toLowerCase();
    if (!normalizedEmail) {
      return;
    }

    requestMagicLinkMutation.mutate(normalizedEmail, {
      onSuccess: () => {
        setSubmittedEmail(normalizedEmail);
      },
    });
  };

  return (
    <main className="flex min-h-screen items-center justify-center px-4 py-10 sm:px-6 lg:px-8">
      <div className="grid w-full max-w-6xl gap-6 lg:grid-cols-[1.1fr_0.9fr]">
        <section className="app-panel-strong flex flex-col justify-between p-8 sm:p-10">
          <div className="space-y-5">
            <span className="app-pill self-start border-brand-white/40 bg-brand-white/10 text-brand-white">
              Localhost sign-in
            </span>
            <div className="space-y-4">
              <h1 className="text-4xl leading-tight sm:text-5xl">
                Open the email link and land straight in your tax workspace.
              </h1>
              <p className="max-w-xl text-sm leading-7 text-brand-white/78 sm:text-base">
                Clair Tax now uses a real browser session for the web app on localhost.
                Enter your email once, open the message in a new tab, and this page will
                continue automatically when the sign-in completes.
              </p>
            </div>
          </div>

          <div className="grid gap-3 pt-10 sm:grid-cols-3">
            <div className="rounded-card border border-brand-white/15 bg-brand-white/10 p-4">
              <p className="text-xs uppercase tracking-[0.2em] text-brand-white/60">Step 1</p>
              <p className="mt-2 text-sm font-medium text-brand-white">
                Request a magic link
              </p>
            </div>
            <div className="rounded-card border border-brand-white/15 bg-brand-white/10 p-4">
              <p className="text-xs uppercase tracking-[0.2em] text-brand-white/60">Step 2</p>
              <p className="mt-2 text-sm font-medium text-brand-white">
                Open the email in a new tab
              </p>
            </div>
            <div className="rounded-card border border-brand-white/15 bg-brand-white/10 p-4">
              <p className="text-xs uppercase tracking-[0.2em] text-brand-white/60">Step 3</p>
              <p className="mt-2 text-sm font-medium text-brand-white">
                Land on the dashboard already signed in
              </p>
            </div>
          </div>
        </section>

        <section className="app-panel flex flex-col justify-between p-8 sm:p-10">
          <div className="space-y-6">
            <div className="space-y-3">
              <span className="app-eyebrow">Magic Link Login</span>
              <h2 className="text-3xl text-brand-black">
                {isSuccessState ? "Check your inbox" : "Sign in to Clair Tax"}
              </h2>
              <p className="text-sm leading-7 text-brand-muted">
                {isSuccessState
                  ? "Keep this tab open. Once the email link completes in the new tab, this page should follow automatically."
                  : "Use the email address you want attached to your Clair Tax workspace. New emails are created on first successful sign-in."}
              </p>
            </div>

            {magicLinkMessage ? (
              <div className="rounded-card border border-brand-line-strong bg-brand-ice px-4 py-3 text-sm leading-6 text-brand-black">
                {magicLinkMessage}
              </div>
            ) : null}

            {requestMagicLinkMutation.isError ? (
              <div className="rounded-card border border-brand-line-strong bg-brand-ice px-4 py-3 text-sm leading-6 text-brand-black">
                {requestMagicLinkMutation.error.message}
              </div>
            ) : null}

            {isSuccessState ? (
              <div className="space-y-4">
                <div className="rounded-card border border-brand-blue bg-brand-ice px-5 py-4">
                  <p className="text-sm font-semibold text-brand-black">
                    Sign-in email sent to
                  </p>
                  <p className="mt-2 text-base text-brand-black">{submittedEmail}</p>
                  <p className="mt-3 text-sm leading-6 text-brand-muted">
                    Open the link from your email. The new tab should land directly on the
                    dashboard, and this tab will try to catch up without a manual refresh.
                  </p>
                </div>

                <button
                  type="button"
                  onClick={() => {
                    setSubmittedEmail(null);
                    requestMagicLinkMutation.reset();
                  }}
                  className="app-button-secondary"
                >
                  Send another link
                </button>
              </div>
            ) : (
              <form className="space-y-5" onSubmit={handleSubmit}>
                <label className="block">
                  <span className="app-label">Email</span>
                  <input
                    type="email"
                    value={email}
                    onChange={(event) => setEmail(event.target.value)}
                    placeholder="you@example.com"
                    autoComplete="email"
                    disabled={requestMagicLinkMutation.isPending}
                    className="app-input"
                    required
                  />
                  <span className="app-help">
                    The link is one-time use and expires after 15 minutes.
                  </span>
                </label>

                <button
                  type="submit"
                  disabled={requestMagicLinkMutation.isPending}
                  className="app-button-primary w-full"
                >
                  {requestMagicLinkMutation.isPending
                    ? "Sending email..."
                    : "Email me a sign-in link"}
                </button>
              </form>
            )}
          </div>

          <div className="mt-10 border-t border-brand-line pt-5 text-sm text-brand-muted">
            Need the dashboard instead?{" "}
            <Link href="/" className="font-semibold text-brand-black transition hover:text-brand-blue">
              Go to the app
            </Link>
          </div>
        </section>
      </div>
    </main>
  );
}
