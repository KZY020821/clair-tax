"use client";

import { useQuery } from "@tanstack/react-query";
import { fetchPolicyYears } from "../lib/policy-years";

function formatStatusLabel(status: "draft" | "published") {
  return status === "published" ? "Published" : "Draft";
}

export default function PolicyYearsPanel() {
  const { data, error, isLoading, isFetching } = useQuery({
    queryKey: ["policy-years"],
    queryFn: fetchPolicyYears,
  });

  if (isLoading) {
    return (
      <section className="app-panel p-6 sm:p-7">
        <p className="app-eyebrow">Backend</p>
        <p className="mt-3 text-sm leading-6 text-brand-muted">
          Loading policy years from Spring Boot...
        </p>
      </section>
    );
  }

  if (error instanceof Error) {
    return (
      <section className="app-panel-strong p-6 sm:p-7">
        <p className="app-eyebrow" style={{ color: "rgba(255,255,255,0.7)" }}>Backend</p>
        <h2 className="mt-3 text-2xl text-white">Policy years unavailable</h2>
        <p className="mt-3 text-sm leading-6 text-white/75">{error.message}</p>
        <p className="mt-4 text-xs leading-6 text-white/60">
          Start the backend with{" "}
          <code className="font-mono">SPRING_PROFILES_ACTIVE=postgres ./mvnw spring-boot:run</code>.
        </p>
      </section>
    );
  }

  if (!data?.length) {
    return (
      <section className="app-panel p-6 sm:p-7">
        <p className="app-eyebrow">Backend</p>
        <h2 className="mt-3 text-xl text-brand-black">Policy years</h2>
        <p className="mt-3 text-sm leading-6 text-brand-muted">
          No policy years were returned from the backend yet.
        </p>
      </section>
    );
  }

  return (
    <section className="app-panel p-6 sm:p-7">
      <div className="flex items-center justify-between gap-4">
        <div>
          <p className="app-eyebrow">Backend</p>
          <h2 className="mt-2 text-xl text-brand-black">Policy years</h2>
        </div>
        <span className={isFetching ? "app-pill-blue" : "app-pill"}>
          {isFetching ? "Refreshing" : "Connected"}
        </span>
      </div>

      <p className="mt-3 text-sm leading-6 text-brand-muted">
        Filing years available in the backend policy database.
      </p>

      <div className="mt-5 grid gap-2">
        {data.map((policyYear) => (
          <article
            key={policyYear.id}
            className="flex items-center justify-between gap-4 rounded-card border border-brand-line bg-brand-ice px-4 py-3"
          >
            <div className="flex items-center gap-4">
              <p className="text-2xl font-semibold text-brand-black">
                {policyYear.year}
              </p>
              <p className="text-xs text-brand-muted">
                {new Date(policyYear.createdAt).toLocaleDateString("en-MY")}
              </p>
            </div>
            <span
              className={
                policyYear.status === "published" ? "app-pill-blue" : "app-pill"
              }
            >
              {formatStatusLabel(policyYear.status)}
            </span>
          </article>
        ))}
      </div>
    </section>
  );
}
