"use client";

import { useMutation } from "@tanstack/react-query";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useState, type FormEvent } from "react";
import { requestMagicLink, type MagicLinkRequestResult } from "../../lib/auth";

const MAGIC_LINK_STATUS_MESSAGES: Record<string, string> = {
  invalid: "That sign-in link is no longer valid. Request a new one to continue.",
  expired: "That sign-in link has expired. Request a fresh email to continue.",
  used: "That sign-in link was already used. Request another email if you need to sign in again.",
};

export default function LoginScreen() {
  const searchParams = useSearchParams();
  const [email, setEmail] = useState("");
  const [submittedEmail, setSubmittedEmail] = useState<string | null>(null);
  const [debugVerifyUrl, setDebugVerifyUrl] = useState<string | null>(null);

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
      onSuccess: (result: MagicLinkRequestResult) => {
        setSubmittedEmail(normalizedEmail);
        setDebugVerifyUrl(result.debugVerifyUrl ?? null);
      },
    });
  };

  return (
    <main className="flex min-h-screen bg-brand-ice items-center justify-center px-4 py-10 sm:px-6 lg:px-8">
      <div className="grid w-full max-w-5xl gap-0 overflow-hidden rounded-panel shadow-accent lg:grid-cols-[1fr_1fr]">
        {/* Left – blue panel */}
        <section className="flex flex-col justify-between bg-brand-blue-dark p-8 sm:p-10">
          <div className="space-y-6">
            <div>
              <p className="text-sm font-semibold uppercase tracking-[0.28em] text-white/60">
                Clair Tax
              </p>
              <h1 className="mt-4 text-3xl font-bold leading-snug text-white sm:text-4xl">
                Sign in to your<br />tax workspace.
              </h1>
              <p className="mt-4 text-sm leading-7 text-white/70">
                Enter your email, open the magic link we send you, and land directly
                on the dashboard.
              </p>
            </div>
          </div>

          <div className="mt-10 grid gap-3 sm:grid-cols-3">
            {[
              { step: "1", label: "Request a magic link" },
              { step: "2", label: "Open the email in a new tab" },
              { step: "3", label: "Land on the dashboard" },
            ].map(({ step, label }) => (
              <div key={step} className="rounded-card border border-white/15 bg-white/10 p-4">
                <p className="text-[10px] font-semibold uppercase tracking-[0.2em] text-white/50">
                  Step {step}
                </p>
                <p className="mt-2 text-sm font-medium text-white">{label}</p>
              </div>
            ))}
          </div>
        </section>

        {/* Right – white form panel */}
        <section className="flex flex-col justify-between bg-brand-white p-8 sm:p-10">
          <div className="space-y-6">
            <div className="space-y-2">
              <span className="app-eyebrow">Magic Link Login</span>
              <h2 className="text-2xl text-brand-black">
                {isSuccessState ? "Check your inbox" : "Sign in to Clair Tax"}
              </h2>
              <p className="text-sm leading-6 text-brand-muted">
                {isSuccessState
                  ? "Keep this tab open — it will update automatically once the link is opened."
                  : "New accounts are created on first sign-in."}
              </p>
            </div>

            {magicLinkMessage ? (
              <div className="rounded-card border border-brand-line bg-brand-ice px-4 py-3 text-sm leading-6 text-brand-black">
                {magicLinkMessage}
              </div>
            ) : null}

            {requestMagicLinkMutation.isError ? (
              <div className="rounded-card border border-brand-line bg-brand-ice px-4 py-3 text-sm leading-6 text-brand-black">
                {requestMagicLinkMutation.error.message}
              </div>
            ) : null}

            {isSuccessState ? (
              <div className="space-y-4">
                <div className="rounded-card border border-brand-line bg-brand-ice px-5 py-4">
                  <p className="text-sm font-semibold text-brand-black">
                    Sign-in email sent to
                  </p>
                  <p className="mt-1.5 text-base font-medium text-brand-blue">{submittedEmail}</p>
                  <p className="mt-3 text-sm leading-6 text-brand-muted">
                    Open the link from your email to complete sign-in.
                  </p>
                  {debugVerifyUrl ? (
                    <div className="mt-4 rounded-card border border-brand-line bg-brand-white px-4 py-4">
                      <p className="text-sm font-semibold text-brand-black">
                        Local email delivery unavailable
                      </p>
                      <p className="mt-1.5 text-sm leading-6 text-brand-muted">
                        Use this debug link to continue testing locally.
                      </p>
                      <a
                        href={debugVerifyUrl}
                        className="mt-4 app-button-primary inline-flex"
                      >
                        Open debug sign-in link
                      </a>
                    </div>
                  ) : null}
                </div>

                <button
                  type="button"
                  onClick={() => {
                    setSubmittedEmail(null);
                    setDebugVerifyUrl(null);
                    requestMagicLinkMutation.reset();
                  }}
                  className="app-button-secondary"
                >
                  Send another link
                </button>
              </div>
            ) : (
              <form className="space-y-4" onSubmit={handleSubmit}>
                <label className="block">
                  <span className="app-label">Email address</span>
                  <input
                    type="email"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    placeholder="you@example.com"
                    autoComplete="email"
                    disabled={requestMagicLinkMutation.isPending}
                    className="app-input"
                    required
                  />
                  <span className="app-help">
                    One-time link · expires after 15 minutes.
                  </span>
                </label>

                <button
                  type="submit"
                  disabled={requestMagicLinkMutation.isPending}
                  className="app-button-primary w-full"
                >
                  {requestMagicLinkMutation.isPending
                    ? "Sending..."
                    : "Email me a sign-in link"}
                </button>
              </form>
            )}
          </div>

          <div className="mt-10 border-t border-brand-line pt-5 text-sm text-brand-muted">
            Need the dashboard?{" "}
            <Link href="/" className="font-semibold text-brand-blue">
              Go to the app
            </Link>
          </div>
        </section>
      </div>
    </main>
  );
}
