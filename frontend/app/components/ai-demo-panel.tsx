"use client";

import { useQuery } from "@tanstack/react-query";
import { fetchAiDemoSummary } from "../lib/ai-demo-summary";

export default function AiDemoPanel() {
  const { data, error, isLoading, isFetching } = useQuery({
    queryKey: ["ai-demo-summary"],
    queryFn: fetchAiDemoSummary,
  });

  if (isLoading) {
    return (
      <section className="app-panel p-6 sm:p-7">
        <p className="app-eyebrow">AI Service</p>
        <p className="mt-3 text-sm leading-6 text-brand-muted">
          Loading FastAPI demo summary...
        </p>
      </section>
    );
  }

  if (error instanceof Error) {
    return (
      <section className="app-panel-strong p-6 sm:p-7">
        <p className="app-eyebrow">AI Service</p>
        <h2 className="mt-3 text-2xl text-brand-white">AI summary unavailable</h2>
        <p className="mt-3 text-sm leading-6 text-brand-white/80">
          {error.message}
        </p>
        <p className="mt-4 text-xs leading-6 text-brand-white/70">
          Start the AI service with
          {" "}
          <code>uvicorn app.main:app --reload --host 0.0.0.0 --port 8000</code>.
        </p>
      </section>
    );
  }

  if (!data) {
    return (
      <section className="app-panel p-6 sm:p-7">
        <p className="app-eyebrow">AI Service</p>
        <h2 className="mt-3 text-2xl text-brand-black">Demo extraction summary</h2>
        <p className="mt-3 text-sm leading-6 text-brand-muted">
          No AI summary was returned yet.
        </p>
      </section>
    );
  }

  return (
    <section className="app-panel p-6 sm:p-7">
      <div className="flex items-center justify-between gap-4">
        <div>
          <p className="app-eyebrow">AI Service</p>
          <h2 className="mt-3 text-3xl text-brand-black">
            Demo extraction summary
          </h2>
        </div>
        <span className={isFetching ? "app-pill-blue" : "app-pill"}>
          {isFetching ? "Refreshing" : "Connected"}
        </span>
      </div>

      <p className="mt-3 max-w-2xl text-sm leading-6 text-brand-muted">
        Keep AI extraction visible, but quiet. The panel should support review work
        without competing with the main filing tasks.
      </p>

      <div className="mt-6 grid gap-4 sm:grid-cols-2">
        <article className="rounded-card border border-brand-line bg-brand-ice p-5">
          <p className="text-xs font-semibold uppercase tracking-[0.2em] text-brand-muted">
            Detected receipts
          </p>
          <p className="mt-2 text-3xl font-semibold text-brand-black">
            {data.detected_receipt_count}
          </p>
          <p className="mt-4 text-sm leading-6 text-brand-muted">
            Extracted total RM {data.extracted_total_amount.toFixed(2)}
          </p>
        </article>

        <article className="rounded-card border border-brand-line bg-brand-white p-5">
          <p className="text-xs font-semibold uppercase tracking-[0.2em] text-brand-blue">
            Suggestion preview
          </p>
          <p className="mt-2 text-base leading-7 text-brand-black">
            {data.suggestion_preview}
          </p>
          <p className="mt-4 text-sm uppercase tracking-[0.2em] text-brand-blue">
            {data.status}
          </p>
          <p className="mt-4 text-sm leading-6 text-brand-muted">
            Generated at {new Date(data.generated_at).toLocaleDateString("en-MY")}
          </p>
        </article>
      </div>
    </section>
  );
}
