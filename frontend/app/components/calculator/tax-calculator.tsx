"use client";

import { useMutation, useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useEffect, useState, type FormEvent } from "react";
import CalculationResult from "./calculation-result";
import ReliefInputList from "./relief-input-list";
import { formatCurrency } from "../../lib/format-currency";
import { fetchPolicyYears } from "../../lib/policy-years";
import {
  calculateTax,
  fetchPolicyYear,
  type CalculatorRequest,
  type ReliefCategory,
} from "../../lib/tax-calculator";

const DEFAULT_POLICY_YEAR = 2025;

const calculatorChecklist = [
  "Choose a policy year first so the relief list reflects backend-owned data.",
  "Enter gross income before adding claims so the estimate has a clear base.",
  "Fix any highlighted claim values before submitting the calculator request.",
];

function SummaryTile({
  label,
  value,
  highlight = false,
}: Readonly<{ label: string; value: string; highlight?: boolean }>) {
  return (
    <article className={highlight ? "metric-card-accent" : "metric-card"}>
      <p className="text-xs font-semibold uppercase tracking-[0.2em] text-brand-muted">
        {label}
      </p>
      <p className="mt-3 text-3xl text-brand-black">{value}</p>
    </article>
  );
}

function parseAmount(value: string): number | null {
  if (value.trim() === "") {
    return null;
  }

  const parsed = Number(value);

  if (!Number.isFinite(parsed)) {
    return null;
  }

  return parsed;
}

function getGrossIncomeError(value: string): string | null {
  if (value.trim() === "") {
    return "Enter your gross income before calculating tax.";
  }

  const parsedValue = parseAmount(value);

  if (parsedValue === null) {
    return "Enter a valid gross income amount.";
  }

  if (parsedValue < 0) {
    return "Gross income cannot be negative.";
  }

  return null;
}

function getClaimedAmountError(
  value: string,
  reliefCategory: ReliefCategory,
): string | null {
  if (value.trim() === "") {
    return null;
  }

  const parsedValue = parseAmount(value);

  if (parsedValue === null) {
    return "Enter a valid claimed amount.";
  }

  if (parsedValue < 0) {
    return "Claimed amount cannot be negative.";
  }

  if (parsedValue > reliefCategory.maxAmount) {
    return `Claimed amount cannot exceed ${formatCurrency(
      reliefCategory.maxAmount,
    )}.`;
  }

  return null;
}

function formatStatusLabel(status?: string) {
  if (!status) {
    return null;
  }

  return status.charAt(0).toUpperCase() + status.slice(1);
}

export default function TaxCalculator() {
  const [selectedYear, setSelectedYear] = useState(DEFAULT_POLICY_YEAR);
  const [grossIncome, setGrossIncome] = useState("");
  const [claimedAmounts, setClaimedAmounts] = useState<Record<string, string>>(
    {},
  );
  const [formError, setFormError] = useState<string | null>(null);

  const policyYearsQuery = useQuery({
    queryKey: ["policy-years"],
    queryFn: fetchPolicyYears,
  });

  const policyQuery = useQuery({
    queryKey: ["policy", selectedYear],
    queryFn: () => fetchPolicyYear(selectedYear),
  });

  const calculationMutation = useMutation({
    mutationFn: calculateTax,
  });

  useEffect(() => {
    if (!policyQuery.data) {
      return;
    }

    setClaimedAmounts((currentClaims) => {
      const nextClaims: Record<string, string> = {};

      for (const reliefCategory of policyQuery.data.reliefCategories) {
        nextClaims[reliefCategory.id] = currentClaims[reliefCategory.id] ?? "";
      }

      return nextClaims;
    });
  }, [policyQuery.data]);

  const yearOptions = Array.from(
    new Set([
      DEFAULT_POLICY_YEAR,
      ...(policyYearsQuery.data?.map((policyYear) => policyYear.year) ?? []),
    ]),
  ).sort((left, right) => left - right);

  const claimedAmountErrors: Record<string, string | null> = {};

  for (const reliefCategory of policyQuery.data?.reliefCategories ?? []) {
    claimedAmountErrors[reliefCategory.id] = getClaimedAmountError(
      claimedAmounts[reliefCategory.id] ?? "",
      reliefCategory,
    );
  }

  function handleYearChange(nextYearValue: string) {
    const nextYear = Number(nextYearValue);

    if (!Number.isInteger(nextYear)) {
      return;
    }

    setSelectedYear(nextYear);
    setClaimedAmounts({});
    setFormError(null);
    calculationMutation.reset();
  }

  function handleClaimedAmountChange(
    reliefCategoryId: string,
    nextValue: string,
  ) {
    setClaimedAmounts((currentClaims) => ({
      ...currentClaims,
      [reliefCategoryId]: nextValue,
    }));
    setFormError(null);
  }

  function buildPayload(): CalculatorRequest | null {
    const grossIncomeError = getGrossIncomeError(grossIncome);

    if (grossIncomeError) {
      setFormError(grossIncomeError);
      return null;
    }

    if (!policyQuery.data) {
      setFormError(
        "Wait for the selected policy year to load before calculating.",
      );
      return null;
    }

    const invalidClaim = policyQuery.data.reliefCategories.find(
      (reliefCategory) => claimedAmountErrors[reliefCategory.id],
    );

    if (invalidClaim) {
      setFormError("Fix the highlighted claimed amounts before calculating.");
      return null;
    }

    const selectedReliefs = policyQuery.data.reliefCategories.flatMap(
      (reliefCategory) => {
        const rawValue = claimedAmounts[reliefCategory.id] ?? "";
        const parsedValue = parseAmount(rawValue);

        if (parsedValue === null || parsedValue === 0) {
          return [];
        }

        return [
          {
            reliefCategoryId: reliefCategory.id,
            claimedAmount: parsedValue,
          },
        ];
      },
    );

    return {
      policyYear: selectedYear,
      grossIncome: parseAmount(grossIncome) ?? 0,
      selectedReliefs,
    };
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    const payload = buildPayload();

    if (!payload) {
      return;
    }

    setFormError(null);
    calculationMutation.mutate(payload);
  }

  const policyLoadError =
    policyQuery.error instanceof Error ? policyQuery.error.message : null;
  const calculationError =
    calculationMutation.error instanceof Error
      ? calculationMutation.error.message
      : null;
  const yearListError =
    policyYearsQuery.error instanceof Error
      ? policyYearsQuery.error.message
      : null;
  const policyStatusLabel =
    formatStatusLabel(policyQuery.data?.status) ??
    (policyQuery.isLoading ? "Loading" : "Unavailable");

  return (
    <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_minmax(20rem,24rem)]">
      <section className="app-panel p-6 sm:p-7">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <p className="app-eyebrow">Calculator Workspace</p>
            <h2 className="mt-3 text-3xl text-brand-black">
              Estimate tax from live policy data
            </h2>
            <p className="mt-3 max-w-3xl text-sm leading-7 text-brand-muted">
              Keep the flow straightforward: choose a year, enter income, add
              claims, and send a clean request to the backend calculator.
            </p>
          </div>
          <span className={policyQuery.isFetching ? "app-pill-blue" : "app-pill"}>
            {policyQuery.isFetching ? "Refreshing policy" : "Ready"}
          </span>
        </div>

        <div className="mt-6 grid gap-4 md:grid-cols-3">
          <SummaryTile label="Selected year" value={String(selectedYear)} />
          <SummaryTile label="Policy status" value={policyStatusLabel} />
          <SummaryTile
            label="Relief categories"
            value={String(policyQuery.data?.reliefCategories.length ?? 0)}
            highlight={Boolean(policyQuery.data)}
          />
        </div>

        <form className="mt-8 space-y-6" onSubmit={handleSubmit}>
          <div className="grid gap-5 lg:grid-cols-2">
            <div>
              <label className="app-label" htmlFor="policyYear">
                Policy year
              </label>
              <select
                id="policyYear"
                name="policyYear"
                value={selectedYear}
                onChange={(event) => handleYearChange(event.target.value)}
                className="app-input"
              >
                {yearOptions.map((year) => (
                  <option key={year} value={year}>
                    {year}
                  </option>
                ))}
              </select>
              <p className="app-help">
                {policyYearsQuery.isLoading
                  ? "Loading available years from the backend..."
                  : yearListError
                    ? `Using the default year while the year list is unavailable: ${yearListError}`
                    : "Choose the filing year that should control the relief list."}
              </p>
            </div>

            <div>
              <label className="app-label" htmlFor="grossIncome">
                Gross income
              </label>
              <input
                id="grossIncome"
                name="grossIncome"
                type="number"
                inputMode="decimal"
                min="0"
                step="0.01"
                value={grossIncome}
                onChange={(event) => {
                  setGrossIncome(event.target.value);
                  setFormError(null);
                }}
                className="app-input"
                placeholder="0.00"
              />
              {grossIncome && getGrossIncomeError(grossIncome) ? (
                <p className="mt-2 text-sm leading-6 text-brand-blue">
                  {getGrossIncomeError(grossIncome)}
                </p>
              ) : (
                <p className="app-help">
                  Enter annual gross income before adding relief claims.
                </p>
              )}
            </div>
          </div>

          <section className="space-y-4">
            <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <h3 className="text-2xl text-brand-black">Relief claims</h3>
                <p className="mt-2 text-sm leading-6 text-brand-muted">
                  The calculator reads these categories from the selected policy
                  year, so the UI stays aligned with backend-managed rules.
                </p>
              </div>
              {policyQuery.data ? (
                <span className="app-pill">
                  {policyQuery.data.reliefCategories.length} categories loaded
                </span>
              ) : null}
            </div>

            {policyQuery.isLoading ? (
              <section className="rounded-card border border-brand-line bg-brand-ice p-5">
                <p className="text-sm leading-6 text-brand-muted">
                  Loading relief categories for policy year {selectedYear}...
                </p>
              </section>
            ) : policyLoadError ? (
              <section className="rounded-card border border-brand-black bg-brand-black p-5 text-brand-white">
                <p className="text-sm font-semibold text-brand-blue">
                  Policy request failed
                </p>
                <p className="mt-2 text-sm leading-6 text-brand-white/80">
                  {policyLoadError}
                </p>
              </section>
            ) : (
              <ReliefInputList
                reliefCategories={policyQuery.data?.reliefCategories ?? []}
                claimedAmounts={claimedAmounts}
                validationMessages={claimedAmountErrors}
                onClaimedAmountChange={handleClaimedAmountChange}
              />
            )}
          </section>

          {formError ? (
            <div className="rounded-card border border-brand-line-strong bg-brand-ice px-4 py-3 text-sm leading-6 text-brand-black">
              {formError}
            </div>
          ) : null}

          <div className="flex flex-col gap-3 sm:flex-row">
            <button
              type="submit"
              disabled={
                calculationMutation.isPending ||
                policyQuery.isLoading ||
                !policyQuery.data ||
                !!policyLoadError
              }
              className="app-button-primary w-full sm:w-auto"
            >
              {calculationMutation.isPending ? "Calculating..." : "Calculate tax"}
            </button>
            <Link href="/" className="app-button-secondary w-full sm:w-auto">
              Back to dashboard
            </Link>
          </div>
        </form>
      </section>

      <div className="space-y-6 xl:sticky xl:top-24">
        <section className="app-panel-muted p-6">
          <p className="app-eyebrow">Checklist</p>
          <h2 className="mt-3 text-2xl text-brand-black">Before you submit</h2>
          <div className="mt-5 space-y-3">
            {calculatorChecklist.map((step, index) => (
              <article
                key={step}
                className="rounded-card border border-brand-line bg-brand-white px-4 py-4"
              >
                <p className="text-sm font-semibold text-brand-black">
                  {index + 1}. {step}
                </p>
              </article>
            ))}
          </div>
        </section>

        <CalculationResult
          result={calculationMutation.data}
          isPending={calculationMutation.isPending}
          errorMessage={calculationError}
        />
      </div>
    </div>
  );
}
