import { z } from "zod";
import { fetchJson, getAiServiceUrl } from "./http";

const aiDemoSummarySchema = z.object({
  service: z.literal("ai-service"),
  status: z.literal("ready"),
  detected_receipt_count: z.number().int().nonnegative(),
  extracted_total_amount: z.number().nonnegative(),
  suggestion_preview: z.string().min(1),
  generated_at: z.string().datetime({ offset: true }),
});

export type AiDemoSummary = z.infer<typeof aiDemoSummarySchema>;

export async function fetchAiDemoSummary(): Promise<AiDemoSummary> {
  return fetchJson(getAiServiceUrl("/api/demo-summary"), (value) =>
    aiDemoSummarySchema.parse(value),
  );
}
