import Link from "next/link";
import type { Suggestion } from "../../lib/tax-suggestions";
import { formatCurrency } from "../../lib/format-currency";

type DynamicSuggestionCardProps = {
  suggestion: Suggestion;
  policyYear: number;
};

export default function DynamicSuggestionCard({
  suggestion,
  policyYear,
}: DynamicSuggestionCardProps) {
  const additionalClaimableAmount = Math.max(
    suggestion.suggestedAmount - suggestion.currentClaimedAmount,
    0,
  );
  const primaryAmount =
    suggestion.currentClaimedAmount > 0
      ? additionalClaimableAmount
      : suggestion.suggestedAmount;
  const primaryLabel =
    suggestion.currentClaimedAmount > 0
      ? "Additional claimable amount"
      : "Suggested claim amount";
  const priorityColors = {
    HIGH: "bg-blue-50 border-blue-200",
    MEDIUM: "bg-yellow-50 border-yellow-200",
    LOW: "bg-gray-50 border-gray-200",
  };

  const priorityBadgeColors = {
    HIGH: "bg-blue-100 text-blue-800",
    MEDIUM: "bg-yellow-100 text-yellow-800",
    LOW: "bg-gray-100 text-gray-800",
  };

  const cardBgClass =
    priorityColors[suggestion.priority] || priorityColors.LOW;
  const badgeColorClass =
    priorityBadgeColors[suggestion.priority] || priorityBadgeColors.LOW;

  return (
    <article
      className={`rounded-2xl border p-5 ${cardBgClass} hover:shadow-md transition-shadow`}
    >
      <div className="flex items-start justify-between gap-4 mb-3">
        <h3 className="font-semibold text-lg text-gray-900">
          {suggestion.reliefCategoryName}
        </h3>
        <span
          className={`px-2 py-1 rounded-full text-xs font-medium ${badgeColorClass}`}
        >
          {suggestion.priority}
        </span>
      </div>

      <div className="space-y-2 mb-4">
        <p className="text-3xl font-bold text-blue-600">
          {formatCurrency(primaryAmount)}
        </p>
        <p className="text-sm text-gray-600">{primaryLabel}</p>
      </div>

      <p className="text-sm text-gray-700 mb-3">{suggestion.reason}</p>

      <div className="flex items-center justify-between gap-3 text-sm">
        <div className="text-gray-600">
          {suggestion.supportingReceiptIds.length > 0 && (
            <span>
              {suggestion.supportingReceiptIds.length} receipt
              {suggestion.supportingReceiptIds.length === 1 ? "" : "s"} uploaded
            </span>
          )}
          {suggestion.currentClaimedAmount > 0 && (
            <span className="ml-2">
              • Currently claimed:{" "}
              {formatCurrency(suggestion.currentClaimedAmount)}
            </span>
          )}
        </div>
      </div>

      <div className="mt-4">
        <Link
          href={`/calculator?year=${policyYear}&prefillCategory=${suggestion.reliefCategoryId}&prefillAmount=${suggestion.suggestedAmount}`}
          className="inline-block w-full text-center bg-blue-600 hover:bg-blue-700 text-white font-medium py-2 px-4 rounded-lg transition-colors"
        >
          Review in calculator
        </Link>
      </div>
    </article>
  );
}
