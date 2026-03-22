import { z } from "zod";
import { backendApiBaseUrl } from "./backend-api";

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

const reliefCategoryWireSchema = z
  .object({
    id: z.string().uuid(),
    code: z.string().min(1).optional(),
    name: z.string().min(1),
    description: z.string().min(1),
    section: z.string().min(1).optional(),
    inputType: z.string().min(1).optional(),
    input_type: z.string().min(1).optional(),
    unitAmount: moneyValueSchema.nullable().optional(),
    unit_amount: moneyValueSchema.nullable().optional(),
    maxAmount: moneyValueSchema.optional(),
    max_amount: moneyValueSchema.optional(),
    displayOrder: z.number().int().optional(),
    display_order: z.number().int().optional(),
    groupCode: z.string().min(1).nullable().optional(),
    group_code: z.string().min(1).nullable().optional(),
    groupMaxAmount: moneyValueSchema.nullable().optional(),
    group_max_amount: moneyValueSchema.nullable().optional(),
    exclusiveGroupCode: z.string().min(1).nullable().optional(),
    exclusive_group_code: z.string().min(1).nullable().optional(),
    requiresCategoryCode: z.string().min(1).nullable().optional(),
    requires_category_code: z.string().min(1).nullable().optional(),
    autoApply: z.boolean().optional(),
    auto_apply: z.boolean().optional(),
    requiresReceipt: z.boolean().optional(),
    requires_receipt: z.boolean().optional(),
  })
  .superRefine((value, ctx) => {
    if (value.maxAmount === undefined && value.max_amount === undefined) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: "Policy relief category is missing maxAmount",
      });
    }
  });

const reliefCategorySchema = reliefCategoryWireSchema.transform((value) => ({
  id: value.id,
  code: value.code ?? value.name.toLowerCase().replace(/\s+/g, "_"),
  name: value.name,
  description: value.description,
  section: value.section ?? "identity",
  inputType: value.inputType ?? value.input_type ?? "amount",
  unitAmount: value.unitAmount ?? value.unit_amount,
  maxAmount: value.maxAmount ?? value.max_amount ?? 0,
  displayOrder: value.displayOrder ?? value.display_order ?? 0,
  groupCode: value.groupCode ?? value.group_code ?? null,
  groupMaxAmount: value.groupMaxAmount ?? value.group_max_amount ?? null,
  exclusiveGroupCode:
    value.exclusiveGroupCode ?? value.exclusive_group_code ?? null,
  requiresCategoryCode:
    value.requiresCategoryCode ?? value.requires_category_code ?? null,
  autoApply: value.autoApply ?? value.auto_apply ?? false,
  requiresReceipt: value.requiresReceipt ?? value.requires_receipt ?? false,
}));

const policyResponseWireSchema = z
  .object({
    id: z.string().uuid().optional(),
    year: z.number().int(),
    status: z.string().optional(),
    reliefCategories: z.array(reliefCategorySchema).optional(),
    relief_categories: z.array(reliefCategorySchema).optional(),
  })
  .superRefine((value, ctx) => {
    if (!value.reliefCategories && !value.relief_categories) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: "Policy response is missing relief categories",
      });
    }
  });

const taxBreakdownRowSchema = z.object({
  minIncome: moneyValueSchema,
  maxIncome: moneyValueSchema.nullable(),
  rate: moneyValueSchema,
  taxableAmount: moneyValueSchema,
  taxForBracket: moneyValueSchema,
});

const calculatorResponseSchema = z.object({
  policyYear: z.number().int(),
  grossIncome: moneyValueSchema,
  totalRelief: moneyValueSchema,
  taxableIncome: moneyValueSchema.optional(),
  chargeableIncome: moneyValueSchema,
  taxBreakdown: z.array(taxBreakdownRowSchema),
  taxAmount: moneyValueSchema,
  taxRebate: moneyValueSchema,
  zakat: moneyValueSchema,
  taxYouShouldPay: moneyValueSchema,
  totalTaxPayable: moneyValueSchema,
});

const apiErrorSchema = z.object({
  message: z.string().min(1),
});

const policyResponseSchema = policyResponseWireSchema.transform((value) => ({
  id: value.id,
  year: value.year,
  status: value.status,
  reliefCategories: (value.reliefCategories ?? value.relief_categories ?? []).sort(
    (left, right) => left.displayOrder - right.displayOrder,
  ),
}));

export type ReliefCategory = z.infer<typeof reliefCategorySchema>;
export type PolicyResponse = z.infer<typeof policyResponseSchema>;
export type TaxBreakdownRow = z.infer<typeof taxBreakdownRowSchema>;
export type CalculatorResponse = z.infer<typeof calculatorResponseSchema>;

export type ReliefClaimPayload = {
  reliefCategoryId: string;
  claimedAmount?: number;
  quantity?: number;
  selected?: boolean;
};

export type CalculatorRequest = {
  policyYear: number;
  grossIncome: number;
  zakat?: number;
  selectedReliefs: ReliefClaimPayload[];
};

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

export async function fetchPolicyYear(year: number): Promise<PolicyResponse> {
  const response = await fetch(`${backendApiBaseUrl}/api/policies/${year}`, {
    headers: {
      Accept: "application/json",
    },
  });

  if (!response.ok) {
    throw new Error(
      await getApiErrorMessage(
        response,
        `Failed to load policy data for ${year} (${response.status})`,
      ),
    );
  }

  const data: unknown = await response.json();

  return policyResponseSchema.parse(data);
}

export async function calculateTax(
  payload: CalculatorRequest,
): Promise<CalculatorResponse> {
  const response = await fetch(`${backendApiBaseUrl}/api/calculator/calculate`, {
    method: "POST",
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
    },
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    throw new Error(
      await getApiErrorMessage(
        response,
        `Failed to calculate tax (${response.status})`,
      ),
    );
  }

  const data: unknown = await response.json();

  return calculatorResponseSchema.parse(data);
}
