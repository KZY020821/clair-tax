import Link from "next/link";

const notes = [
  "Policy years should stay backend-owned so filing logic remains data-driven.",
  "The frontend can surface the workflow, but should not hardcode rule creation.",
  "Use this page as a clean hand-off point into the relevant admin or calculation flow.",
];

export default function NewYearPage() {
  return (
    <div className="space-y-8 pb-2">
      <section className="mx-auto max-w-4xl px-1 pt-2 text-center">
        <p className="app-eyebrow">Years</p>
        <h1 className="mt-4 text-4xl text-brand-black sm:text-5xl">
          Create a new filing year
        </h1>
        <p className="mt-4 text-base leading-8 text-brand-muted sm:text-lg">
          New policy years should be introduced carefully and remain tied to
          backend-managed tax data. This view keeps the action visible without
          pretending the frontend owns tax rules.
        </p>
      </section>

      <section className="app-panel p-6 sm:p-7">
        <p className="app-eyebrow">Guidance</p>
        <div className="mt-5 grid gap-3">
          {notes.map((note, index) => (
            <article
              key={note}
              className="rounded-card border border-brand-line bg-brand-ice px-4 py-4"
            >
              <p className="text-sm font-semibold text-brand-black">
                {index + 1}. {note}
              </p>
            </article>
          ))}
        </div>

        <div className="mt-6 flex flex-col gap-3 sm:flex-row">
          <Link href="/" className="app-button-secondary">
            Back to dashboard
          </Link>
          <Link href="/calculator" className="app-button-primary">
            Open calculator
          </Link>
        </div>
      </section>
    </div>
  );
}
