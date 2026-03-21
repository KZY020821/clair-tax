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
          Loading seeded policy years from Spring Boot...
        </p>
      </section>
    );
  }

  if (error instanceof Error) {
    return (
      <section className="app-panel-strong p-6 sm:p-7">
        <p className="app-eyebrow">Backend</p>
        <h2 className="mt-3 text-2xl text-brand-white">Policy years unavailable</h2>
        <p className="mt-3 text-sm leading-6 text-brand-white/80">
          {error.message}
        </p>
        <p className="mt-4 text-xs leading-6 text-brand-white/70">
          Start the backend with a database profile, for example
          {" "}
          <code>SPRING_PROFILES_ACTIVE=postgres ./mvnw spring-boot:run</code>.
        </p>
      </section>
    );
  }

  if (!data?.length) {
    return (
      <section className="app-panel p-6 sm:p-7">
        <p className="app-eyebrow">Backend</p>
        <h2 className="mt-3 text-2xl text-brand-black">Policy years</h2>
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
          <h2 className="mt-3 text-3xl text-brand-black">Policy years</h2>
        </div>
        <span className={isFetching ? "app-pill-blue" : "app-pill"}>
          {isFetching ? "Refreshing" : "Connected"}
        </span>
      </div>

      <p className="mt-3 max-w-2xl text-sm leading-6 text-brand-muted">
        Confirm which filing years are live before you switch workflows or start
        working in the calculator.
      </p>

      <div className="mt-6 grid gap-3">
        {data.map((policyYear) => (
          <article
            key={policyYear.id}
            className="rounded-card border border-brand-line bg-brand-ice px-5 py-4"
          >
            <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <p className="text-xs font-semibold uppercase tracking-[0.2em] text-brand-muted">
                  Policy year
                </p>
                <p className="mt-2 text-3xl font-semibold text-brand-black">
                  {policyYear.year}
                </p>
              </div>

              <div className="flex flex-wrap items-center gap-2">
                <span
                  className={
                    policyYear.status === "published" ? "app-pill-blue" : "app-pill"
                  }
                >
                  {formatStatusLabel(policyYear.status)}
                </span>
                <span className="app-pill">
                  {new Date(policyYear.createdAt).toLocaleDateString("en-MY")}
                </span>
              </div>
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}
