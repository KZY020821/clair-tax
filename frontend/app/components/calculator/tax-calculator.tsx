"use client";

import { useMutation, useQuery } from "@tanstack/react-query";
import { useState, type FormEvent } from "react";
import CalculationResult from "./calculation-result";
import { formatCurrency } from "../../lib/format-currency";
import { fetchPolicyYears } from "../../lib/policy-years";
import {
  calculateTax,
  fetchPolicyYear,
  type CalculatorRequest,
  type ReliefCategory,
} from "../../lib/tax-calculator";

const DEFAULT_POLICY_YEAR = 2025;

const sectionOrder = [
  "identity",
  "family",
  "medical",
  "education",
  "lifestyle",
  "retirement",
  "insurance",
  "property",
] as const;

const sectionContent: Record<
  string,
  {
    title: string;
    detail: string;
  }
> = {
  identity: {
    title: "Identity and status",
    detail:
      "Resident individual reliefs that are fixed or depend on personal status.",
  },
  family: {
    title: "Family reliefs",
    detail:
      "Parents, spouse, children, childcare, and related family deductions.",
  },
  medical: {
    title: "Medical and support",
    detail:
      "Medical treatment and support items with the selected year's caps, including any shared medical ceilings.",
  },
  education: {
    title: "Education and savings",
    detail: "Self-education, skill-upgrading, and SSPN-related deductions.",
  },
  lifestyle: {
    title: "Lifestyle",
    detail:
      "Lifestyle, sports, travel, and similar personal expenditure reliefs for the selected year.",
  },
  retirement: {
    title: "Retirement and statutory contributions",
    detail: "EPF, PRS, deferred annuity, and SOCSO-linked deductions.",
  },
  insurance: {
    title: "Insurance",
    detail: "Life, family takaful, education, and medical insurance deductions.",
  },
  property: {
    title: "Property and green reliefs",
    detail:
      "First-home loan interest plus eligible green and household sustainability reliefs.",
  },
};

function SummaryTile({
  label,
  value,
}: Readonly<{ label: string; value: string }>) {
  return (
    <article className="metric-card">
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
  return Number.isFinite(parsed) ? parsed : null;
}

function parseCount(value: string): number | null {
  if (value.trim() === "") {
    return null;
  }

  const parsed = Number(value);
  return Number.isInteger(parsed) ? parsed : null;
}

function getMoneyFieldError(value: string, maxAmount: number): string | null {
  if (value.trim() === "") {
    return null;
  }

  const parsedValue = parseAmount(value);
  if (parsedValue === null) {
    return "Enter a valid amount.";
  }

  if (parsedValue < 0) {
    return "Amount cannot be negative.";
  }

  if (parsedValue > maxAmount) {
    return `Amount cannot exceed ${formatCurrency(maxAmount)}.`;
  }

  return null;
}

function getCountFieldError(value: string): string | null {
  if (value.trim() === "") {
    return null;
  }

  const parsedValue = parseCount(value);
  if (parsedValue === null) {
    return "Enter a whole number.";
  }

  if (parsedValue < 0) {
    return "Quantity cannot be negative.";
  }

  return null;
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

function getZakatError(value: string): string | null {
  if (value.trim() === "") {
    return null;
  }

  const parsedValue = parseAmount(value);
  if (parsedValue === null) {
    return "Enter a valid zakat amount.";
  }

  if (parsedValue < 0) {
    return "Zakat cannot be negative.";
  }

  return null;
}

function CategoryField({
  category,
  amountValue,
  countValue,
  selected,
  validationMessage,
  disabled,
  onAmountChange,
  onCountChange,
  onSelectedChange,
}: Readonly<{
  category: ReliefCategory;
  amountValue: string;
  countValue: string;
  selected: boolean;
  validationMessage: string | null;
  disabled: boolean;
  onAmountChange: (nextValue: string) => void;
  onCountChange: (nextValue: string) => void;
  onSelectedChange: (nextValue: boolean) => void;
}>) {
  const showSharedCap =
    category.groupCode && category.groupMaxAmount !== null && category.groupMaxAmount !== undefined;

  return (
    <article
      className={`rounded-card border p-5 ${
        category.autoApply
          ? "border-brand-lineStrong bg-brand-ice"
          : "border-brand-line bg-brand-white"
      }`}
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="max-w-3xl">
          <div className="flex flex-wrap items-center gap-2">
            <span className="app-pill-blue">Cap {formatCurrency(category.maxAmount)}</span>
            {category.unitAmount !== null && category.unitAmount !== undefined ? (
              <span className="app-pill">
                {category.inputType === "count"
                  ? `${formatCurrency(category.unitAmount)} each`
                  : `${formatCurrency(category.unitAmount)} fixed`}
              </span>
            ) : null}
            {category.requiresReceipt ? <span className="app-pill">Receipt based</span> : null}
            {showSharedCap ? (
              <span className="app-pill">
                Shared cap {formatCurrency(category.groupMaxAmount ?? 0)}
              </span>
            ) : null}
          </div>
          <h3 className="mt-4 text-2xl text-brand-black">{category.name}</h3>
          <p className="mt-3 text-sm leading-6 text-brand-muted">
            {category.description}
          </p>
          {category.requiresCategoryCode ? (
            <p className="mt-2 text-xs leading-6 text-brand-muted">
              Requires {category.requiresCategoryCode.replaceAll("_", " ")}.
            </p>
          ) : null}
        </div>

        {category.autoApply ? (
          <div className="rounded-card border border-brand-lineStrong bg-brand-white px-4 py-3 text-right">
            <p className="text-xs font-semibold uppercase tracking-[0.2em] text-brand-muted">
              Automatic
            </p>
            <p className="mt-2 text-2xl text-brand-black">
              {formatCurrency(category.unitAmount ?? category.maxAmount)}
            </p>
          </div>
        ) : null}
      </div>

      {category.autoApply ? (
        <p className="app-help">This relief is applied automatically.</p>
      ) : null}

      {!category.autoApply && category.inputType === "fixed" ? (
        <label className="mt-5 flex items-center gap-3 text-sm font-medium text-brand-black">
          <input
            type="checkbox"
            checked={selected}
            disabled={disabled}
            onChange={(event) => onSelectedChange(event.target.checked)}
            className="h-4 w-4 rounded border-brand-lineStrong text-brand-blue focus:ring-brand-blue/20"
          />
          Claim this fixed relief
        </label>
      ) : null}

      {!category.autoApply && category.inputType === "amount" ? (
        <div className="mt-5">
          <label className="app-label" htmlFor={`relief-${category.id}`}>
            Claimed amount
          </label>
          <input
            id={`relief-${category.id}`}
            name={`relief-${category.id}`}
            type="number"
            inputMode="decimal"
            min="0"
            step="0.01"
            max={category.maxAmount}
            value={amountValue}
            disabled={disabled}
            onChange={(event) => onAmountChange(event.target.value)}
            className="app-input"
            placeholder="0.00"
          />
        </div>
      ) : null}

      {!category.autoApply && category.inputType === "count" ? (
        <div className="mt-5">
          <label className="app-label" htmlFor={`relief-${category.id}`}>
            Quantity
          </label>
          <input
            id={`relief-${category.id}`}
            name={`relief-${category.id}`}
            type="number"
            inputMode="numeric"
            min="0"
            step="1"
            value={countValue}
            disabled={disabled}
            onChange={(event) => onCountChange(event.target.value)}
            className="app-input"
            placeholder="0"
          />
        </div>
      ) : null}

      {validationMessage ? (
        <p className="mt-2 text-sm leading-6 text-brand-blue">{validationMessage}</p>
      ) : category.autoApply ? null : (
        <p className="app-help">
          {category.inputType === "count"
            ? "Enter the number of qualifying dependants for this relief."
            : category.inputType === "fixed"
              ? "Tick this box only if you qualify for the fixed deduction."
              : "Enter only the eligible amount you want the backend to evaluate."}
        </p>
      )}
    </article>
  );
}

export default function TaxCalculator() {
  const [selectedYear, setSelectedYear] = useState(DEFAULT_POLICY_YEAR);
  const [grossIncome, setGrossIncome] = useState("");
  const [zakat, setZakat] = useState("");
  const [amountValues, setAmountValues] = useState<Record<string, string>>({});
  const [countValues, setCountValues] = useState<Record<string, string>>({});
  const [selectedValues, setSelectedValues] = useState<Record<string, boolean>>({});
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

  const yearOptions = Array.from(
    new Set([
      DEFAULT_POLICY_YEAR,
      ...(policyYearsQuery.data?.map((policyYear) => policyYear.year) ?? []),
    ]),
  ).sort((left, right) => left - right);

  const categories = policyQuery.data?.reliefCategories ?? [];
  const activeCodes = new Set<string>();

  for (const category of categories) {
    if (category.autoApply) {
      activeCodes.add(category.code);
      continue;
    }

    if (
      category.inputType === "fixed" &&
      selectedValues[category.id]
    ) {
      activeCodes.add(category.code);
      continue;
    }

    if (
      category.inputType === "amount" &&
      (parseAmount(amountValues[category.id] ?? "") ?? 0) > 0
    ) {
      activeCodes.add(category.code);
      continue;
    }

    if (
      category.inputType === "count" &&
      (parseCount(countValues[category.id] ?? "") ?? 0) > 0
    ) {
      activeCodes.add(category.code);
    }
  }

  const categoryValidationMessages: Record<string, string | null> = {};
  for (const category of categories) {
    if (category.inputType === "amount") {
      categoryValidationMessages[category.id] = getMoneyFieldError(
        amountValues[category.id] ?? "",
        category.maxAmount,
      );
      continue;
    }

    if (category.inputType === "count") {
      categoryValidationMessages[category.id] = getCountFieldError(
        countValues[category.id] ?? "",
      );
      continue;
    }

    categoryValidationMessages[category.id] = null;
  }

  const sectionedCategories = sectionOrder
    .map((section) => ({
      section,
      categories: categories.filter((category) => category.section === section),
    }))
    .filter((sectionGroup) => sectionGroup.categories.length > 0);

  function handleYearChange(nextYearValue: string) {
    const nextYear = Number(nextYearValue);

    if (!Number.isInteger(nextYear)) {
      return;
    }

    setSelectedYear(nextYear);
    setAmountValues({});
    setCountValues({});
    setSelectedValues({});
    setZakat("");
    setFormError(null);
    calculationMutation.reset();
  }

  function buildPayload(): CalculatorRequest | null {
    const grossIncomeError = getGrossIncomeError(grossIncome);
    if (grossIncomeError) {
      setFormError(grossIncomeError);
      return null;
    }

    const zakatError = getZakatError(zakat);
    if (zakatError) {
      setFormError(zakatError);
      return null;
    }

    const invalidCategory = categories.find(
      (category) => categoryValidationMessages[category.id],
    );
    if (invalidCategory) {
      setFormError("Fix the highlighted relief fields before calculating.");
      return null;
    }

    const selectedReliefs: CalculatorRequest["selectedReliefs"] = [];

    for (const category of categories) {
      if (category.autoApply) {
        continue;
      }

      if (
        category.requiresCategoryCode !== null &&
        !activeCodes.has(category.requiresCategoryCode)
      ) {
        continue;
      }

      if (category.inputType === "fixed") {
        if (selectedValues[category.id]) {
          selectedReliefs.push({
            reliefCategoryId: category.id,
            selected: true,
          });
        }
        continue;
      }

      if (category.inputType === "count") {
        const quantity = parseCount(countValues[category.id] ?? "");
        if (quantity && quantity > 0) {
          selectedReliefs.push({
            reliefCategoryId: category.id,
            quantity,
          });
        }
        continue;
      }

      const claimedAmount = parseAmount(amountValues[category.id] ?? "");
      if (claimedAmount && claimedAmount > 0) {
        selectedReliefs.push({
          reliefCategoryId: category.id,
          claimedAmount,
        });
      }
    }

    return {
      policyYear: selectedYear,
      grossIncome: parseAmount(grossIncome) ?? 0,
      zakat: parseAmount(zakat) ?? 0,
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

  return (
    <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_minmax(20rem,24rem)]">
      <section className="app-panel p-6 sm:p-7">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <p className="app-eyebrow">Income Tax Calculator</p>
            <h2 className="mt-3 text-3xl text-brand-black">
              Resident individual tax calculator
            </h2>
            <p className="mt-3 max-w-3xl text-sm leading-7 text-brand-muted">
              Enter gross income, choose an assessment year from 2018 to 2025,
              then complete the relevant relief sections. Fixed reliefs, dependant
              counts, and capped expenses are all handled by the backend rules
              behind this page.
            </p>
          </div>
          <span className={policyQuery.isFetching ? "app-pill-blue" : "app-pill"}>
            {policyQuery.isFetching ? "Refreshing policy" : "Ready"}
          </span>
        </div>

        <div className="mt-6 grid gap-4 md:grid-cols-3">
          <SummaryTile label="Assessment year" value={String(selectedYear)} />
          <SummaryTile
            label="Relief categories"
            value={String(categories.length)}
          />
          <SummaryTile label="Tax rebate" value="Auto up to RM 400" />
        </div>

        <form className="mt-8 space-y-8" onSubmit={handleSubmit}>
          <section className="grid gap-5 lg:grid-cols-3">
            <div>
              <label className="app-label" htmlFor="policyYear">
                Assessment year
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
                    : "Published resident-individual policy years from 2018 to 2025 are loaded from the backend."}
              </p>
            </div>

            <div>
              <label className="app-label" htmlFor="grossIncome">
                Gross income before deduction
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
                  Enter your annual gross income before deductions and rebates.
                </p>
              )}
            </div>

            <div>
              <label className="app-label" htmlFor="zakat">
                Zakat or fitrah paid
              </label>
              <input
                id="zakat"
                name="zakat"
                type="number"
                inputMode="decimal"
                min="0"
                step="0.01"
                value={zakat}
                onChange={(event) => {
                  setZakat(event.target.value);
                  setFormError(null);
                }}
                className="app-input"
                placeholder="0.00"
              />
              {zakat && getZakatError(zakat) ? (
                <p className="mt-2 text-sm leading-6 text-brand-blue">
                  {getZakatError(zakat)}
                </p>
              ) : (
                <p className="app-help">
                  Zakat is deducted after the tax amount and rebate are calculated.
                </p>
              )}
            </div>
          </section>

          {policyQuery.isLoading ? (
            <section className="rounded-card border border-brand-line bg-brand-ice p-5">
              <p className="text-sm leading-6 text-brand-muted">
                Loading the relief structure for policy year {selectedYear}...
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
            <div className="space-y-8">
              {sectionedCategories.map((sectionGroup) => (
                <section key={sectionGroup.section} className="space-y-4">
                  <div>
                    <p className="app-eyebrow">
                      {sectionGroup.section.replaceAll("_", " ")}
                    </p>
                    <h3 className="mt-3 text-2xl text-brand-black">
                      {sectionContent[sectionGroup.section]?.title ??
                        sectionGroup.section}
                    </h3>
                    <p className="mt-2 text-sm leading-6 text-brand-muted">
                      {sectionContent[sectionGroup.section]?.detail}
                    </p>
                  </div>

                  <div className="grid gap-4">
                    {sectionGroup.categories.map((category) => {
                      const disabled =
                        category.requiresCategoryCode !== null &&
                        !activeCodes.has(category.requiresCategoryCode);

                      return (
                        <CategoryField
                          key={category.id}
                          category={category}
                          amountValue={amountValues[category.id] ?? ""}
                          countValue={countValues[category.id] ?? ""}
                          selected={selectedValues[category.id] ?? false}
                          validationMessage={categoryValidationMessages[category.id]}
                          disabled={disabled}
                          onAmountChange={(nextValue) => {
                            setAmountValues((currentValues) => ({
                              ...currentValues,
                              [category.id]: nextValue,
                            }));
                            setFormError(null);
                          }}
                          onCountChange={(nextValue) => {
                            setCountValues((currentValues) => ({
                              ...currentValues,
                              [category.id]: nextValue,
                            }));
                            setFormError(null);
                          }}
                          onSelectedChange={(nextValue) => {
                            setSelectedValues((currentValues) => ({
                              ...currentValues,
                              [category.id]: nextValue,
                            }));
                            setFormError(null);
                          }}
                        />
                      );
                    })}
                  </div>
                </section>
              ))}
            </div>
          )}

          {formError ? (
            <div className="rounded-card border border-brand-lineStrong bg-brand-ice px-4 py-3 text-sm leading-6 text-brand-black">
              {formError}
            </div>
          ) : null}

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
        </form>
      </section>

      <div className="space-y-6 xl:sticky xl:top-24">
        <section className="app-panel-muted p-6">
          <p className="app-eyebrow">Notes</p>
          <h2 className="mt-3 text-2xl text-brand-black">What this calculator covers</h2>
          <div className="mt-5 space-y-3">
            <article className="rounded-card border border-brand-line bg-brand-white px-4 py-4">
              <p className="text-sm leading-6 text-brand-black">
                The calculator now supports resident-individual assessment years
                2018 through 2025 with year-specific relief categories, caps,
                and tax brackets.
              </p>
            </article>
            <article className="rounded-card border border-brand-line bg-brand-white px-4 py-4">
              <p className="text-sm leading-6 text-brand-black">
                Self relief is applied automatically, shared medical caps are
                enforced in the backend, and low-income rebates are calculated
                automatically.
              </p>
            </article>
            <article className="rounded-card border border-brand-line bg-brand-white px-4 py-4">
              <p className="text-sm leading-6 text-brand-black">
                This flow focuses on gross income, reliefs, zakat, and final tax due.
                PCB and refund balancing are not included yet.
              </p>
            </article>
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
