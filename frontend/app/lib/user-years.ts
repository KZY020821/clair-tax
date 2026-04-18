import { z } from "zod";
import { backendFetch } from "./backend-api";

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

const apiErrorSchema = z.object({
  message: z.string().min(1),
});

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
export type UserYearCategorySummary = z.infer<
  typeof userYearCategorySummarySchema
>;

async function getApiErrorMessage(
  response: Response,
  fallbackMessage: string,
): Promise<string> {
  try {
    const data: unknown = await response.json();
    const parsed = apiErrorSchema.safeParse(data);

    if (parsed.success) {
      return parsed.data.message;
    }
  } catch {
    return fallbackMessage;
  }

  return fallbackMessage;
}

export async function fetchUserYears(): Promise<UserYear[]> {
  const response = await backendFetch("/api/user-years", {
    headers: {
      Accept: "application/json",
    },
  });

  if (!response.ok) {
    throw new Error(
      await getApiErrorMessage(
        response,
        `Failed to load year workspaces (${response.status})`,
      ),
    );
  }

  const data: unknown = await response.json();

  return userYearsSchema.parse(data);
}

export async function createUserYear(policyYear: number): Promise<UserYear> {
  const response = await backendFetch("/api/user-years", {
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

  const data: unknown = await response.json();

  return userYearSchema.parse(data);
}

export async function fetchUserYearWorkspace(
  year: number,
): Promise<UserYearWorkspace> {
  const response = await backendFetch(`/api/user-years/${year}`, {
    headers: {
      Accept: "application/json",
    },
  });

  if (!response.ok) {
    throw new Error(
      await getApiErrorMessage(
        response,
        `Failed to load the ${year} workspace (${response.status})`,
      ),
    );
  }

  const data: unknown = await response.json();

  return userYearWorkspaceSchema.parse(data);
}
