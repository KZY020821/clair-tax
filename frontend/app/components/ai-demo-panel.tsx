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
        <p className="app-eyebrow" style={{ color: "rgba(255,255,255,0.7)" }}>AI Service</p>
        <h2 className="mt-3 text-2xl text-white">AI summary unavailable</h2>
        <p className="mt-3 text-sm leading-6 text-white/75">{error.message}</p>
        <p className="mt-4 text-xs leading-6 text-white/60">
          Start the AI service with{" "}
          <code className="font-mono">uvicorn app.main:app --reload --host 0.0.0.0 --port 8000</code>.
        </p>
      </section>
    );
  }

  if (!data) {
    return (
      <section className="app-panel p-6 sm:p-7">
        <p className="app-eyebrow">AI Service</p>
        <h2 className="mt-2 text-xl text-brand-black">Demo extraction summary</h2>
        <p className="mt-3 text-sm leading-6 text-brand-muted">No AI summary returned yet.</p>
      </section>
    );
  }

  return (
    <section className="app-panel p-6 sm:p-7">
      <div className="flex items-center justify-between gap-4">
        <div>
          <p className="app-eyebrow">AI Service</p>
          <h2 className="mt-2 text-xl text-brand-black">Demo extraction summary</h2>
        </div>
        <span className={isFetching ? "app-pill-blue" : "app-pill"}>
          {isFetching ? "Refreshing" : "Connected"}
        </span>
      </div>

      <div className="mt-5 grid gap-3 sm:grid-cols-2">
        <article className="rounded-card border border-brand-line bg-brand-ice p-5">
          <p className="text-xs font-semibold uppercase tracking-[0.18em] text-brand-muted">
            Detected receipts
          </p>
          <p className="mt-2 text-3xl font-semibold text-brand-black">
            {data.detected_receipt_count}
          </p>
          <p className="mt-3 text-sm text-brand-muted">
            Total extracted: RM {data.extracted_total_amount.toFixed(2)}
          </p>
        </article>

        <article className="rounded-card border border-brand-line bg-brand-white p-5">
          <p className="app-eyebrow">Suggestion preview</p>
          <p className="mt-2 text-sm leading-6 text-brand-black">
            {data.suggestion_preview}
          </p>
          <p className="mt-3 text-xs uppercase tracking-[0.18em] text-brand-blue">
            {data.status}
          </p>
          <p className="mt-2 text-xs text-brand-muted">
            {new Date(data.generated_at).toLocaleDateString("en-MY")}
          </p>
        </article>
      </div>
    </section>
  );
}
