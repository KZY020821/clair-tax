"use client";

import { formatCurrency } from "../../lib/format-currency";
import type { ReliefCategory } from "../../lib/tax-calculator";

type ReliefInputListProps = Readonly<{
  reliefCategories: ReliefCategory[];
  claimedAmounts: Record<string, string>;
  validationMessages: Record<string, string | null>;
  onClaimedAmountChange: (reliefCategoryId: string, nextValue: string) => void;
}>;

export default function ReliefInputList({
  reliefCategories,
  claimedAmounts,
  validationMessages,
  onClaimedAmountChange,
}: ReliefInputListProps) {
  if (!reliefCategories.length) {
    return (
      <section className="rounded-card border border-brand-line bg-brand-ice p-5">
        <p className="text-sm leading-6 text-brand-muted">
          No relief categories are available for this policy year yet.
        </p>
      </section>
    );
  }

  return (
    <div className="grid gap-4">
      {reliefCategories.map((reliefCategory) => (
        <article
          key={reliefCategory.id}
          className="rounded-card border border-brand-line bg-brand-ice p-5"
        >
          <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_16rem] lg:items-start">
            <div>
              <div className="flex flex-wrap items-center gap-2">
                <span className="app-pill-blue">
                  Max {formatCurrency(reliefCategory.maxAmount)}
                </span>
                {reliefCategory.requiresReceipt !== undefined ? (
                  <span className="app-pill">
                    {reliefCategory.requiresReceipt
                      ? "Receipt required"
                      : "Receipt not required"}
                  </span>
                ) : null}
              </div>

              <h3 className="mt-4 text-2xl text-brand-black">
                {reliefCategory.name}
              </h3>
              <p className="mt-3 text-sm leading-6 text-brand-muted">
                {reliefCategory.description}
              </p>
            </div>

            <div>
              <label
                className="app-label"
                htmlFor={`relief-${reliefCategory.id}`}
              >
                Claimed amount
              </label>
              <input
                id={`relief-${reliefCategory.id}`}
                name={`relief-${reliefCategory.id}`}
                type="number"
                inputMode="decimal"
                min="0"
                max={reliefCategory.maxAmount}
                step="0.01"
                value={claimedAmounts[reliefCategory.id] ?? ""}
                onChange={(event) =>
                  onClaimedAmountChange(reliefCategory.id, event.target.value)
                }
                className="app-input"
                placeholder="0.00"
              />
              {validationMessages[reliefCategory.id] ? (
                <p className="mt-2 text-sm leading-6 text-brand-blue">
                  {validationMessages[reliefCategory.id]}
                </p>
              ) : (
                <p className="app-help">
                  Enter the amount you want the backend to evaluate for this
                  relief category.
                </p>
              )}
            </div>
          </div>
        </article>
      ))}
    </div>
  );
}
