"use client";

import type { CalculatorResponse } from "../../lib/tax-calculator";
import { formatCurrency } from "../../lib/format-currency";

type CalculationResultProps = Readonly<{
  result: CalculatorResponse | undefined;
  isPending: boolean;
  errorMessage: string | null;
}>;

function SummaryCard({
  label,
  value,
  highlight = false,
}: Readonly<{ label: string; value: string; highlight?: boolean }>) {
  return (
    <article className={highlight ? "metric-card-accent" : "metric-card"}>
      <p className="text-sm font-medium text-brand-muted">{label}</p>
      <p className="mt-3 text-3xl text-brand-black">{value}</p>
    </article>
  );
}

export default function CalculationResult({
  result,
  isPending,
  errorMessage,
}: CalculationResultProps) {
  if (errorMessage) {
    return (
      <section className="app-panel-strong p-6">
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
      <section className="app-panel p-6">
        <p className="app-eyebrow">Calculation Result</p>
        <h2 className="mt-3 text-2xl text-brand-black">Working on your estimate</h2>
        <p className="mt-3 text-sm leading-6 text-brand-muted">
          Calculating tax from the selected policy year...
        </p>
      </section>
    );
  }

  if (!result) {
    return (
      <section className="app-panel p-6">
        <p className="app-eyebrow">Calculation Result</p>
        <h2 className="mt-3 text-2xl text-brand-black">Result preview</h2>
        <p className="mt-3 text-sm leading-6 text-brand-muted">
          Submit the calculator form to see chargeable income, total relief, total
          tax payable, and the progressive bracket breakdown.
        </p>
      </section>
    );
  }

  return (
    <section className="app-panel p-6">
      <div className="flex items-center justify-between gap-3">
        <div>
          <p className="app-eyebrow">Calculation Result</p>
          <h2 className="mt-3 text-2xl text-brand-black">
            Policy Year {result.policyYear}
          </h2>
        </div>
        <span className={isPending ? "app-pill-blue" : "app-pill"}>
          {isPending ? "Updating" : "Ready"}
        </span>
      </div>

      <p className="mt-3 text-sm leading-6 text-brand-muted">
        The backend evaluates each submitted claim and returns the final tax view
        as a bracket-by-bracket summary.
      </p>

      <div className="mt-6 grid gap-4">
        <SummaryCard
          label="Gross income"
          value={formatCurrency(result.grossIncome)}
        />
        <SummaryCard
          label="Total relief"
          value={formatCurrency(result.totalRelief)}
        />
        <SummaryCard
          label="Chargeable income"
          value={formatCurrency(result.chargeableIncome)}
        />
        <SummaryCard
          label="Total tax payable"
          value={formatCurrency(result.totalTaxPayable)}
          highlight
        />
      </div>

      <div className="mt-6 overflow-hidden rounded-card border border-brand-line">
        <div className="overflow-x-auto">
          <table className="min-w-full text-left text-sm">
            <thead className="border-b border-brand-line bg-brand-ice text-brand-black">
              <tr>
                <th className="px-4 py-3 font-medium">Bracket min</th>
                <th className="px-4 py-3 font-medium">Bracket max</th>
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
                  <td className="px-4 py-3">{row.rate.toFixed(2)}%</td>
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
    </section>
  );
}
