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
  accent = false,
}: Readonly<{ label: string; value: string; accent?: boolean }>) {
  return (
    <article className={accent ? "metric-card-accent" : "metric-card"}>
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
          Calculating the YA 2025 summary from the selected relief inputs...
        </p>
      </section>
    );
  }

  if (!result) {
    return (
      <section className="app-panel p-6">
        <p className="app-eyebrow">Calculation Result</p>
        <h2 className="mt-3 text-2xl text-brand-black">Summary preview</h2>
        <p className="mt-3 text-sm leading-6 text-brand-muted">
          Submit the calculator to review gross income before deduction, tax
          deductions, taxable income, tax amount, less zakat, less rebate, and the
          final tax you should pay.
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
            Assessment Year {result.policyYear}
          </h2>
        </div>
        <span className={isPending ? "app-pill-blue" : "app-pill"}>
          {isPending ? "Updating" : "Ready"}
        </span>
      </div>

      <div className="mt-6 grid gap-4">
        <SummaryCard
          label="Gross Income Before Deduction"
          value={formatCurrency(result.grossIncome)}
        />
        <SummaryCard
          label="Tax Deductions"
          value={formatCurrency(result.totalRelief)}
        />
        <SummaryCard
          label="Taxable Income"
          value={formatCurrency(result.taxableIncome ?? result.chargeableIncome)}
        />
        <SummaryCard
          label="Tax Amount"
          value={formatCurrency(result.taxAmount)}
        />
        <SummaryCard
          label="Less Tax Rebate"
          value={formatCurrency(result.taxRebate)}
        />
        <SummaryCard label="Less Zakat" value={formatCurrency(result.zakat)} />
        <SummaryCard
          label="Tax You Should Pay"
          value={formatCurrency(result.taxYouShouldPay)}
          accent
        />
      </div>

      <div className="mt-6 overflow-hidden rounded-card border border-brand-line">
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
