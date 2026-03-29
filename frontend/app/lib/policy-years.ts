import { z } from "zod";
import { backendFetch } from "./backend-api";

const policyYearSchema = z.object({
  id: z.string().uuid(),
  year: z.number().int(),
  status: z.enum(["draft", "published"]),
  createdAt: z.string().datetime({ offset: true }),
});

export const policyYearsSchema = z.array(policyYearSchema);

export type PolicyYear = z.infer<typeof policyYearSchema>;

export async function fetchPolicyYears(): Promise<PolicyYear[]> {
  const response = await backendFetch("/api/policy-years", {
    headers: {
      Accept: "application/json",
    },
  });

  if (!response.ok) {
    throw new Error(`Failed to load policy years (${response.status})`);
  }

  const data: unknown = await response.json();

  return policyYearsSchema.parse(data);
}
