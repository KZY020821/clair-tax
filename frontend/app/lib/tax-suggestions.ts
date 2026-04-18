import { z } from "zod";
import { backendFetch } from "./backend-api";

export const suggestionPrioritySchema = z.enum(["HIGH", "MEDIUM", "LOW"]);
export type SuggestionPriority = z.infer<typeof suggestionPrioritySchema>;

export const suggestionTypeSchema = z.enum([
  "RECEIPT_BASED",
  "PROFILE_BASED",
  "UNDER_CLAIMED",
]);
export type SuggestionType = z.infer<typeof suggestionTypeSchema>;

export const suggestionSchema = z.object({
  id: z.string().uuid(),
  reliefCategoryId: z.string().uuid(),
  reliefCategoryName: z.string(),
  reliefCategoryCode: z.string(),
  suggestedAmount: z.number(),
  currentClaimedAmount: z.number(),
  reason: z.string(),
  supportingReceiptIds: z.array(z.string().uuid()),
  priority: suggestionPrioritySchema,
  suggestionType: suggestionTypeSchema,
});
export type Suggestion = z.infer<typeof suggestionSchema>;

export const suggestionsResponseSchema = z.object({
  policyYear: z.number(),
  suggestions: z.array(suggestionSchema),
  generatedAt: z.string().datetime(),
});
export type SuggestionsResponse = z.infer<typeof suggestionsResponseSchema>;

export async function fetchTaxSuggestions(
  year: number,
): Promise<SuggestionsResponse> {
  const response = await backendFetch(
    `/api/suggestions?policyYear=${year}`,
    {
      method: "GET",
    },
  );

  if (!response.ok) {
    throw new Error(
      `Failed to load suggestions (${response.status} ${response.statusText})`,
    );
  }

  const data = await response.json();
  return suggestionsResponseSchema.parse(data);
}
