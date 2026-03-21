import Link from "next/link";

type PolicyYearPageProps = Readonly<{
  params: Promise<{
    year: string;
  }>;
}>;

export default async function PolicyYearPage({
  params,
}: PolicyYearPageProps) {
  const { year } = await params;

  const yearCards = [
    {
      label: "Selected year",
      value: year,
      detail: "The workspace context is aligned to this filing year.",
    },
    {
      label: "Rule ownership",
      value: "Backend-managed",
      detail: "Relief logic and tax policy stay outside frontend presentation code.",
    },
    {
      label: "Primary action",
      value: "Review and calculate",
      detail: "Use the calculator once this year is the one you want to evaluate.",
    },
  ];

  return (
    <div className="space-y-8 pb-2">
      <section className="mx-auto max-w-4xl px-1 pt-2 text-center">
        <p className="app-eyebrow">Years</p>
        <h1 className="mt-4 text-4xl text-brand-black sm:text-5xl">
          Policy year {year}
        </h1>
        <p className="mt-4 text-base leading-8 text-brand-muted sm:text-lg">
          Keep year selection simple. This page exists to preserve sidebar context
          and direct work into the backend-driven calculator flow.
        </p>
      </section>

      <section className="grid gap-4 lg:grid-cols-3">
        {yearCards.map((card) => (
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
        <p className="app-eyebrow">Workflow</p>
        <h2 className="mt-3 text-3xl text-brand-black">
          Continue with this filing year
        </h2>
        <p className="mt-3 max-w-3xl text-sm leading-7 text-brand-muted">
          The year is visible in the sidebar, while calculations continue to rely on
          backend policy data and validation rules.
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
