import Link from "next/link";
import type { Suggestion } from "../../lib/tax-suggestions";
import { formatCurrency } from "../../lib/format-currency";

type DynamicSuggestionCardProps = {
  suggestion: Suggestion;
  policyYear: number;
};

const priorityBadgeClasses: Record<string, string> = {
  HIGH: "bg-brand-blue text-white border-brand-blue",
  MEDIUM: "bg-brand-ice text-brand-blue border-brand-line-strong",
  LOW: "bg-brand-white text-brand-muted border-brand-line-strong",
};

export default function DynamicSuggestionCard({
  suggestion,
  policyYear,
}: DynamicSuggestionCardProps) {
  const additionalClaimable = Math.max(
    suggestion.suggestedAmount - suggestion.currentClaimedAmount,
    0,
  );
  const primaryAmount =
    suggestion.currentClaimedAmount > 0
      ? additionalClaimable
      : suggestion.suggestedAmount;
  const primaryLabel =
    suggestion.currentClaimedAmount > 0
      ? "Additional claimable"
      : "Suggested claim";

  const badgeClass =
    priorityBadgeClasses[suggestion.priority] ?? priorityBadgeClasses.LOW;

  return (
    <article className="app-panel flex flex-col p-5">
      <div className="flex items-start justify-between gap-3">
        <h3 className="text-base font-semibold text-brand-black">
          {suggestion.reliefCategoryName}
        </h3>
        <span
          className={`inline-flex shrink-0 items-center rounded-full border px-2.5 py-0.5 text-[10px] font-semibold uppercase tracking-[0.12em] ${badgeClass}`}
        >
          {suggestion.priority}
        </span>
      </div>

      <div className="mt-4">
        <p className="text-2xl font-semibold text-brand-blue">
          {formatCurrency(primaryAmount)}
        </p>
        <p className="mt-1 text-xs text-brand-muted">{primaryLabel}</p>
      </div>

      <p className="mt-4 text-sm leading-6 text-brand-muted">{suggestion.reason}</p>

      {(suggestion.supportingReceiptIds.length > 0 || suggestion.currentClaimedAmount > 0) && (
        <div className="mt-3 flex flex-wrap gap-2">
          {suggestion.supportingReceiptIds.length > 0 && (
            <span className="app-pill">
              {suggestion.supportingReceiptIds.length} receipt{suggestion.supportingReceiptIds.length === 1 ? "" : "s"}
            </span>
          )}
          {suggestion.currentClaimedAmount > 0 && (
            <span className="app-pill">
              Claimed: {formatCurrency(suggestion.currentClaimedAmount)}
            </span>
          )}
        </div>
      )}

      <div className="mt-auto pt-5">
        <Link
          href={`/calculator?year=${policyYear}&prefillCategory=${suggestion.reliefCategoryId}&prefillAmount=${suggestion.suggestedAmount}`}
          className="app-button-primary w-full"
        >
          Review in calculator
        </Link>
      </div>
    </article>
  );
}
