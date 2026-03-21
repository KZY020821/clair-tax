import Link from "next/link";

const profileCards = [
  {
    label: "Account",
    value: "khorzeyi02@gmail.com",
    detail: "Primary email used for the Clair Tax workspace.",
  },
  {
    label: "Workspace style",
    value: "Simple and operational",
    detail: "The shell stays focused on review work instead of decorative UI.",
  },
  {
    label: "Default tools",
    value: "Dashboard and calculator",
    detail: "The core navigation stays anchored on filing context and calculations.",
  },
];

export default function ProfilePage() {
  return (
    <div className="space-y-8 pb-2">
      <section className="mx-auto max-w-4xl px-1 pt-2 text-center">
        <p className="app-eyebrow">Profile</p>
        <h1 className="mt-4 text-4xl text-brand-black sm:text-5xl">
          Profile and workspace settings
        </h1>
        <p className="mt-4 text-base leading-8 text-brand-muted sm:text-lg">
          Keep account details visible without turning the page into a settings maze.
          The profile view should stay light, clear, and easy to review.
        </p>
      </section>

      <section className="grid gap-4 lg:grid-cols-3">
        {profileCards.map((card) => (
          <article key={card.label} className="metric-card">
            <p className="text-sm font-medium text-brand-muted">{card.label}</p>
            <p className="mt-3 text-3xl text-brand-black">{card.value}</p>
            <p className="mt-3 text-sm leading-6 text-brand-muted">
              {card.detail}
            </p>
          </article>
        ))}
      </section>

      <section className="app-panel p-6 sm:p-7">
        <p className="app-eyebrow">Next Step</p>
        <h2 className="mt-3 text-3xl text-brand-black">
          Return to the main tax workflow
        </h2>
        <p className="mt-3 max-w-3xl text-sm leading-7 text-brand-muted">
          The dashboard keeps live status visible, while the calculator remains the
          primary place to work with policy-year tax logic.
        </p>
        <div className="mt-6 flex flex-col gap-3 sm:flex-row">
          <Link href="/" className="app-button-secondary">
            Open dashboard
          </Link>
          <Link href="/calculator" className="app-button-primary">
            Open calculator
          </Link>
        </div>
      </section>
    </div>
  );
}
