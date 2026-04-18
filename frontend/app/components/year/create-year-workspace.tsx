"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { fetchPolicyYears } from "../../lib/policy-years";
import { createUserYear, fetchUserYears } from "../../lib/user-years";

export default function CreateYearWorkspace() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const [selectedYear, setSelectedYear] = useState<number | null>(null);
  const [formError, setFormError] = useState<string | null>(null);

  const policyYearsQuery = useQuery({
    queryKey: ["policy-years"],
    queryFn: fetchPolicyYears,
  });
  const userYearsQuery = useQuery({
    queryKey: ["user-years"],
    queryFn: fetchUserYears,
  });

  const createYearMutation = useMutation({
    mutationFn: createUserYear,
    onSuccess: async (createdYear) => {
      setFormError(null);
      await queryClient.invalidateQueries({ queryKey: ["user-years"] });
      router.push(`/year/${createdYear.year}`);
    },
  });

  const existingYears = userYearsQuery.data?.map((userYear) => userYear.year) ?? [];
  const availablePolicyYears =
    policyYearsQuery.data?.filter(
      (policyYear) => !existingYears.includes(policyYear.year),
    ) ?? [];
  const activeSelectedYear =
    selectedYear !== null &&
    availablePolicyYears.some((policyYear) => policyYear.year === selectedYear)
      ? selectedYear
      : availablePolicyYears[0]?.year ?? null;
  const selectedPolicyYear =
    availablePolicyYears.find((policyYear) => policyYear.year === activeSelectedYear) ??
    null;

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (activeSelectedYear === null) {
      setFormError("Choose a policy year before opening a workspace.");
      return;
    }

    setFormError(null);
    createYearMutation.mutate(activeSelectedYear);
  }

  return (
    <div className="space-y-6">
      <section className="grid gap-4 lg:grid-cols-[minmax(0,1.45fr)_minmax(18rem,0.95fr)]">
        <article className="app-panel p-6 sm:p-7">
          <p className="app-eyebrow">Year Workspace</p>
          <h2 className="mt-3 text-3xl text-brand-black">
            Open a year workspace before adding receipts
          </h2>
          <p className="mt-3 max-w-2xl text-sm leading-7 text-brand-muted">
            Pick a policy year from backend data, create the workspace once,
            and Clair Tax will keep that year available in the sidebar for the
            signed-in account.
          </p>
        </article>

        <article className="app-panel-muted p-6 sm:p-7">
          <p className="app-eyebrow">Current State</p>
          <p className="mt-3 text-4xl text-brand-black">
            {userYearsQuery.isLoading ? "..." : existingYears.length}
          </p>
          <p className="mt-3 text-sm leading-7 text-brand-muted">
            {existingYears.length > 0
              ? "These are the year workspaces already created for this signed-in account."
              : "No year workspaces exist yet for this signed-in account."}
          </p>
        </article>
      </section>

      <section className="grid gap-4 xl:grid-cols-[minmax(0,1.05fr)_minmax(20rem,0.95fr)]">
        <article className="app-panel p-6 sm:p-7">
          <p className="app-eyebrow">Create Workspace</p>
          <h2 className="mt-3 text-3xl text-brand-black">Choose a filing year</h2>
          <p className="mt-3 text-sm leading-7 text-brand-muted">
            Years come from `/api/policy-years`. Already-created workspaces drop
            out of the selector so this stays focused on the next year you need
            to open.
          </p>

          {policyYearsQuery.isLoading || userYearsQuery.isLoading ? (
            <div className="mt-6 rounded-card border border-brand-line bg-brand-ice px-5 py-5 text-sm text-brand-muted">
              Loading policy years and existing workspaces...
            </div>
          ) : policyYearsQuery.error instanceof Error ? (
            <div className="mt-6 rounded-card border border-red-200 bg-red-50 px-5 py-5 text-sm leading-7 text-red-700">
              {policyYearsQuery.error.message}
            </div>
          ) : availablePolicyYears.length === 0 ? (
            <div className="mt-6 rounded-card border border-brand-line bg-brand-ice px-5 py-5">
              <p className="text-sm font-semibold text-brand-black">
                All visible policy years already have a workspace.
              </p>
              <p className="mt-2 text-sm leading-7 text-brand-muted">
                Jump straight into an existing year or wait for another policy
                year to be added on the backend.
              </p>
              {existingYears.length > 0 ? (
                <div className="mt-5 flex flex-wrap gap-3">
                  {existingYears.map((year) => (
                    <Link key={year} href={`/year/${year}`} className="app-button-secondary">
                      Open {year}
                    </Link>
                  ))}
                </div>
              ) : null}
            </div>
          ) : (
            <form className="mt-6 space-y-5" onSubmit={handleSubmit}>
              <label className="block">
                <span className="app-label">Policy year</span>
                <select
                  className="app-input"
                  value={activeSelectedYear ?? ""}
                  onChange={(event) => {
                    setSelectedYear(Number(event.target.value));
                  }}
                >
                  {availablePolicyYears.map((policyYear) => (
                    <option key={policyYear.id} value={policyYear.year}>
                      {policyYear.year} · {policyYear.status}
                    </option>
                  ))}
                </select>
                <p className="app-help">
                  Draft and published years are both listed exactly as the backend
                  returns them.
                </p>
              </label>

              {selectedPolicyYear ? (
                <div className="rounded-card border border-brand-line bg-brand-ice px-5 py-5">
                  <div className="flex flex-wrap items-center justify-between gap-4">
                    <div>
                      <p className="text-sm font-semibold text-brand-black">
                        Year {selectedPolicyYear.year}
                      </p>
                      <p className="mt-2 text-sm leading-7 text-brand-muted">
                        Open this workspace to review available relief categories
                        and attach receipts for the year.
                      </p>
                    </div>
                    <span className="app-pill">{selectedPolicyYear.status}</span>
                  </div>
                </div>
              ) : null}

              {formError ? (
                <div className="rounded-card border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
                  {formError}
                </div>
              ) : null}

              {createYearMutation.error instanceof Error ? (
                <div className="rounded-card border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
                  {createYearMutation.error.message}
                </div>
              ) : null}

              <div className="flex flex-col gap-3 sm:flex-row">
                <button
                  type="submit"
                  className="app-button-primary"
                  disabled={createYearMutation.isPending}
                >
                  {createYearMutation.isPending ? "Opening workspace..." : "Create year"}
                </button>
                <Link href="/" className="app-button-secondary">
                  Back to dashboard
                </Link>
              </div>
            </form>
          )}
        </article>

        <article className="app-panel p-6 sm:p-7">
          <p className="app-eyebrow">Created Years</p>
          <h2 className="mt-3 text-3xl text-brand-black">Sidebar-ready workspaces</h2>
          <p className="mt-3 text-sm leading-7 text-brand-muted">
            Only years created for the signed-in account will appear in the year
            rail and the year routes.
          </p>

          {existingYears.length > 0 ? (
            <div className="mt-6 grid gap-3">
              {existingYears.map((year) => (
                <Link
                  key={year}
                  href={`/year/${year}`}
                  className="rounded-card border border-brand-line bg-brand-white px-4 py-4 transition hover:border-brand-blue hover:bg-brand-ice"
                >
                  <p className="text-sm font-semibold text-brand-black">Year {year}</p>
                  <p className="mt-1 text-sm leading-6 text-brand-muted">
                    Open the workspace, review summary totals, and manage receipts.
                  </p>
                </Link>
              ))}
            </div>
          ) : (
            <div className="mt-6 rounded-card border border-dashed border-brand-line px-5 py-5 text-sm leading-7 text-brand-muted">
              No workspaces yet. Create one from the form to seed the sidebar and
              unlock the year summary page.
            </div>
          )}
        </article>
      </section>
    </div>
  );
}
