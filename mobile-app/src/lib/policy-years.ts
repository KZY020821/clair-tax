import { z } from "zod";
import { fetchJson, getBackendUrl } from "./http";

const policyYearSchema = z.object({
  id: z.string().uuid(),
  year: z.number().int(),
  status: z.enum(["draft", "published"]),
  createdAt: z.string().datetime({ offset: true }),
});

const policyYearsSchema = z.array(policyYearSchema);

export type PolicyYear = z.infer<typeof policyYearSchema>;

export async function fetchPolicyYears(): Promise<PolicyYear[]> {
  return fetchJson(getBackendUrl("/api/policy-years"), (value) =>
    policyYearsSchema.parse(value),
  );
}
