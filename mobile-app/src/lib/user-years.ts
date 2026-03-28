import { z } from "zod";
import { fetchJson, getApiErrorMessage, getBackendUrl } from "./http";

const numericValueSchema = z
  .union([z.number(), z.string()])
  .transform((value, ctx) => {
    const parsed = typeof value === "number" ? value : Number(value);

    if (!Number.isFinite(parsed)) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: "Expected a numeric value",
      });
      return z.NEVER;
    }

    return parsed;
  });

const moneyValueSchema = numericValueSchema.pipe(z.number().nonnegative());

const userYearSchema = z.object({
  id: z.string().uuid(),
  year: z.number().int(),
  status: z.string().min(1),
  createdAt: z.string().datetime({ offset: true }),
  updatedAt: z.string().datetime({ offset: true }),
});

const userYearCategorySummarySchema = z.object({
  reliefCategoryId: z.string().uuid(),
  code: z.string().min(1),
  name: z.string().min(1),
  description: z.string().min(1),
  section: z.string().min(1),
  maxAmount: moneyValueSchema,
  claimedAmount: moneyValueSchema,
  remainingAmount: moneyValueSchema,
  requiresReceipt: z.boolean(),
  receiptCount: z.number().int().nonnegative(),
});

const userYearWorkspaceSchema = userYearSchema.extend({
  totalCategories: z.number().int().nonnegative(),
  totalReceiptCount: z.number().int().nonnegative(),
  totalClaimedAmount: moneyValueSchema,
  categories: z.array(userYearCategorySummarySchema),
});

const userYearsSchema = z.array(userYearSchema);

export type UserYear = z.infer<typeof userYearSchema>;
export type UserYearWorkspace = z.infer<typeof userYearWorkspaceSchema>;
export type UserYearCategorySummary = z.infer<typeof userYearCategorySummarySchema>;

export async function fetchUserYears(): Promise<UserYear[]> {
  return fetchJson(getBackendUrl("/api/user-years"), (value) => userYearsSchema.parse(value));
}

export async function createUserYear(policyYear: number): Promise<UserYear> {
  const response = await fetch(getBackendUrl("/api/user-years"), {
    method: "POST",
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ policyYear }),
  });

  if (!response.ok) {
    throw new Error(
      await getApiErrorMessage(
        response,
        `Failed to create year workspace (${response.status})`,
      ),
    );
  }

  return userYearSchema.parse(await response.json());
}

export async function fetchUserYearWorkspace(
  year: number,
): Promise<UserYearWorkspace> {
  return fetchJson(getBackendUrl(`/api/user-years/${year}`), (value) =>
    userYearWorkspaceSchema.parse(value),
  );
}
