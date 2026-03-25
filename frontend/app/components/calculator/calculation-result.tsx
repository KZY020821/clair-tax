"use client";

import type { CalculatorResponse } from "../../lib/tax-calculator";
import { formatCurrency } from "../../lib/format-currency";

type CalculationResultProps = Readonly<{
  result: CalculatorResponse | undefined;
  isPending: boolean;
  errorMessage: string | null;
}>;

type KeyFigureProps = Readonly<{
  label: string;
  value: string;
}>;

type ReportRowProps = Readonly<{
  label: string;
  value: string;
  tone?: "default" | "deduction" | "final";
}>;

function formatDeduction(amount: number): string {
  return `- ${formatCurrency(amount)}`;
}

function formatRate(value: number): string {
  return `${value.toFixed(2)}%`;
}

function KeyFigure({ label, value }: KeyFigureProps) {
  return (
    <article className="rounded-card border border-brand-line bg-brand-white px-4 py-4">
      <p className="text-[11px] font-semibold uppercase tracking-[0.18em] text-brand-muted">
        {label}
      </p>
      <p className="mt-2 text-lg font-semibold text-brand-black sm:text-xl">
        {value}
      </p>
    </article>
  );
}

function ReportRow({
  label,
  value,
  tone = "default",
}: ReportRowProps) {
  const valueClassName =
    tone === "deduction"
      ? "text-brand-blue"
      : tone === "final"
        ? "text-brand-black"
        : "text-brand-black";
  const rowClassName =
    tone === "final"
      ? "border-t-2 border-brand-black pt-4"
      : "border-t border-brand-line pt-3";
  const labelClassName =
    tone === "final"
      ? "text-base font-semibold text-brand-black"
      : "text-sm font-medium text-brand-muted";

  return (
    <div className={`flex items-start justify-between gap-4 ${rowClassName}`}>
      <p className={labelClassName}>{label}</p>
      <p className={`text-right text-sm font-semibold sm:text-base ${valueClassName}`}>
        {value}
      </p>
    </div>
  );
}

export default function CalculationResult({
  result,
  isPending,
  errorMessage,
}: CalculationResultProps) {
  if (errorMessage) {
    return (
      <section id="calculation-result" className="app-panel-strong p-6 sm:p-7">
        <p className="app-eyebrow">Calculation Result</p>
        <h2 className="mt-3 text-2xl text-brand-white">Calculation error</h2>
        <p className="mt-3 text-sm leading-6 text-brand-white/80">
          {errorMessage}
        </p>
      </section>
    );
  }

  if (!result && isPending) {
    return (
      <section id="calculation-result" className="app-panel p-6 sm:p-7">
        <p className="app-eyebrow">Calculation Result</p>
        <h2 className="mt-3 text-2xl text-brand-black">Preparing your report</h2>
        <p className="mt-3 max-w-2xl text-sm leading-6 text-brand-muted">
          Calculating the YA 2025 tax summary and building a compact report from
          your income, reliefs, rebate, and zakat inputs.
        </p>
      </section>
    );
  }

  if (!result) {
    return (
      <section id="calculation-result" className="app-panel p-6 sm:p-7">
        <p className="app-eyebrow">Calculation Result</p>
        <h2 className="mt-3 text-2xl text-brand-black">Summary preview</h2>
        <p className="mt-3 max-w-2xl text-sm leading-6 text-brand-muted">
          Submit the calculator to see a compact tax report with the main figures
          up front, a clear calculation flow, and the detailed tax bracket
          breakdown underneath.
        </p>
      </section>
    );
  }

  const taxableIncome = result.taxableIncome ?? result.chargeableIncome;

  return (
    <section id="calculation-result" className="app-panel p-6 sm:p-7">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <p className="app-eyebrow">Calculation Result</p>
          <h2 className="mt-3 text-2xl text-brand-black sm:text-3xl">
            Income tax report YA {result.policyYear}
          </h2>
          <p className="mt-3 max-w-2xl text-sm leading-6 text-brand-muted">
            Review the key totals first, then expand the tax bracket breakdown if
            you need the detailed computation.
          </p>
        </div>
        <span className={isPending ? "app-pill-blue" : "app-pill"}>
          {isPending ? "Updating" : "Ready"}
        </span>
      </div>

      <div className="mt-6 grid gap-5 xl:grid-cols-[minmax(0,1.45fr)_minmax(18rem,0.95fr)]">
        <article className="rounded-card border border-brand-line bg-brand-white p-5 sm:p-6">
          <div className="flex items-center justify-between gap-3">
            <div>
              <p className="text-sm font-semibold text-brand-black">
                Tax summary
              </p>
              <p className="mt-1 text-sm leading-6 text-brand-muted">
                This report follows the same order as the tax calculation.
              </p>
            </div>
            <span className="app-pill">Report</span>
          </div>

          <div className="mt-5 space-y-3">
            <div className="flex items-start justify-between gap-4">
              <p className="text-sm font-medium text-brand-muted">
                Gross income before deduction
              </p>
              <p className="text-right text-sm font-semibold text-brand-black sm:text-base">
                {formatCurrency(result.grossIncome)}
              </p>
            </div>
            <ReportRow
              label="Less total relief"
              value={formatDeduction(result.totalRelief)}
              tone="deduction"
            />
            <ReportRow
              label="Taxable income"
              value={formatCurrency(taxableIncome)}
            />
            <ReportRow
              label="Tax amount"
              value={formatCurrency(result.taxAmount)}
            />
            <ReportRow
              label="Less tax rebate"
              value={formatDeduction(result.taxRebate)}
              tone="deduction"
            />
            <ReportRow
              label="Less zakat"
              value={formatDeduction(result.zakat)}
              tone="deduction"
            />
            <ReportRow
              label="Tax you should pay"
              value={formatCurrency(result.taxYouShouldPay)}
              tone="final"
            />
          </div>
        </article>

        <div className="space-y-4">
          <article className="metric-card-accent p-5 sm:p-6">
            <p className="text-[11px] font-semibold uppercase tracking-[0.2em] text-brand-muted">
              Final payable
            </p>
            <p className="mt-3 text-3xl font-semibold text-brand-black sm:text-4xl">
              {formatCurrency(result.taxYouShouldPay)}
            </p>
            <p className="mt-3 text-sm leading-6 text-brand-muted">
              This is the remaining tax after reliefs, rebate, and zakat are
              applied to the selected year&apos;s rules.
            </p>
          </article>

          <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-1">
            <KeyFigure
              label="Gross Income"
              value={formatCurrency(result.grossIncome)}
            />
            <KeyFigure
              label="Total Relief"
              value={formatCurrency(result.totalRelief)}
            />
            <KeyFigure
              label="Taxable Income"
              value={formatCurrency(taxableIncome)}
            />
            <KeyFigure
              label="Tax Amount"
              value={formatCurrency(result.taxAmount)}
            />
          </div>
        </div>
      </div>

      <details className="mt-6 overflow-hidden rounded-card border border-brand-line bg-brand-white">
        <summary className="flex cursor-pointer list-none items-center justify-between gap-4 px-5 py-4">
          <div>
            <p className="text-sm font-semibold text-brand-black">Tax breakdown</p>
            <p className="mt-1 text-sm leading-6 text-brand-muted">
              Expand to review each tax bracket used in the calculation.
            </p>
          </div>
          <span className="app-pill">{result.taxBreakdown.length} rows</span>
        </summary>

        <div className="border-t border-brand-line">
          <div className="overflow-x-auto">
            <table className="min-w-full text-left text-sm">
              <thead className="border-b border-brand-line bg-brand-ice text-brand-black">
                <tr>
                  <th className="px-4 py-3 font-medium">Bracket start</th>
                  <th className="px-4 py-3 font-medium">Bracket end</th>
                  <th className="px-4 py-3 font-medium">Rate</th>
                  <th className="px-4 py-3 font-medium">Taxable amount</th>
                  <th className="px-4 py-3 font-medium">Tax for bracket</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-brand-line bg-brand-white text-brand-black">
                {result.taxBreakdown.map((row) => (
                  <tr key={`${row.minIncome}-${row.maxIncome ?? "open-ended"}`}>
                    <td className="px-4 py-3">{formatCurrency(row.minIncome)}</td>
                    <td className="px-4 py-3">
                      {row.maxIncome === null
                        ? "No upper limit"
                        : formatCurrency(row.maxIncome)}
                    </td>
                    <td className="px-4 py-3">{formatRate(row.rate)}</td>
                    <td className="px-4 py-3">
                      {formatCurrency(row.taxableAmount)}
                    </td>
                    <td className="px-4 py-3">
                      {formatCurrency(row.taxForBracket)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </details>
    </section>
  );
}
