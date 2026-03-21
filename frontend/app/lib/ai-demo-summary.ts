import { z } from "zod";

const aiDemoSummarySchema = z.object({
  service: z.literal("ai-service"),
  status: z.literal("ready"),
  detected_receipt_count: z.number().int().nonnegative(),
  extracted_total_amount: z.number().nonnegative(),
  suggestion_preview: z.string().min(1),
  generated_at: z.string().datetime({ offset: true }),
});

export type AiDemoSummary = z.infer<typeof aiDemoSummarySchema>;

const aiServiceBaseUrl =
  process.env.NEXT_PUBLIC_AI_SERVICE_BASE_URL ?? "http://localhost:8000";

export async function fetchAiDemoSummary(): Promise<AiDemoSummary> {
  const response = await fetch(`${aiServiceBaseUrl}/api/demo-summary`, {
    headers: {
      Accept: "application/json",
    },
  });

  if (!response.ok) {
    throw new Error(`Failed to load AI demo summary (${response.status})`);
  }

  const data: unknown = await response.json();

  return aiDemoSummarySchema.parse(data);
}
