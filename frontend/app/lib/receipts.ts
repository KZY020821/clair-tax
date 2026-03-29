import { z } from "zod";
import { backendFetch, buildBackendUrl } from "./backend-api";

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

const latestExtractionSchema = z.object({
  totalAmount: moneyValueSchema.nullable(),
  receiptDate: z.string().min(1).nullable(),
  merchantName: z.string().min(1).nullable(),
  currency: z.string().min(3).max(3).nullable(),
  confidenceScore: numericValueSchema,
  warnings: z.array(z.string()),
  providerName: z.string().min(1),
  providerVersion: z.string().min(1),
  processedAt: z.string().datetime({ offset: true }),
});

const receiptSchema = z.object({
  id: z.string().uuid(),
  policyYear: z.number().int(),
  merchantName: z.string().min(1).nullable(),
  receiptDate: z.string().min(1).nullable(),
  amount: moneyValueSchema.nullable(),
  currency: z.string().min(3).max(3).nullable(),
  reliefCategoryId: z.string().uuid().nullable(),
  reliefCategoryName: z.string().nullable(),
  notes: z.string().nullable(),
  fileName: z.string().nullable(),
  fileUrl: z.string().nullable(),
  mimeType: z.string().min(1),
  fileSizeBytes: z.number().int().nonnegative(),
  status: z.string().min(1),
  processingErrorCode: z.string().nullable(),
  processingErrorMessage: z.string().nullable(),
  latestExtraction: latestExtractionSchema.nullable(),
  uploadedAt: z.string().datetime({ offset: true }),
  createdAt: z.string().datetime({ offset: true }),
  updatedAt: z.string().datetime({ offset: true }),
});

const receiptUploadIntentSchema = z.object({
  uploadIntentId: z.string().uuid(),
  uploadUrl: z.string().min(1),
  uploadMethod: z.string().min(1),
  uploadHeaders: z.record(z.string(), z.string()),
  expiresAt: z.string().datetime({ offset: true }),
});

const receiptYearsSchema = z.array(z.number().int());
const receiptsSchema = z.array(receiptSchema);

export type Receipt = z.infer<typeof receiptSchema>;
export type UploadYearReceiptRequest = {
  reliefCategoryId: string;
  notes?: string | null;
  file: File;
};
export type ReceiptUploadIntent = z.infer<typeof receiptUploadIntentSchema>;

export type ReceiptMutationRequest = {
  policyYear: number;
  merchantName: string;
  receiptDate: string;
  amount: number;
  reliefCategoryId?: string | null;
  notes?: string | null;
  fileName?: string | null;
  fileUrl?: string | null;
};

export type ConfirmReceiptReviewRequest = {
  merchantName?: string | null;
  receiptDate?: string | null;
  amount?: number | null;
  currency?: string | null;
  reliefCategoryId?: string | null;
  notes?: string | null;
};

function getHttpStatusErrorMessage(status: number): string | null {
  switch (status) {
    case 413:
      return "File size is too large. Please upload a file smaller than 10MB.";
    case 415:
      return "Unsupported file type. Please upload a valid image or PDF file.";
    case 400:
      return "Invalid request. Please check all required fields are filled correctly.";
    case 401:
      return "Authentication required. Please refresh the page and try again.";
    case 403:
      return "You don't have permission to perform this action.";
    case 404:
      return "The requested resource was not found.";
    case 409:
      return "A conflict occurred. This resource may already exist.";
    case 422:
      return "Validation failed. Please check your input and try again.";
    case 500:
      return "Server error occurred. Please try again later.";
    case 503:
      return "Service temporarily unavailable. Please try again in a few moments.";
    default:
      return null;
  }
}

async function getApiErrorMessage(
  response: Response,
  fallbackMessage: string,
): Promise<string> {
  // Try to get the error message from the response body first
  try {
    const data: unknown = await response.json();
    const parsed = apiErrorSchema.safeParse(data);

    if (parsed.success) {
      return parsed.data.message;
    }
  } catch {
    // If parsing fails, fall through to status-based messages
  }

  // Try to get a user-friendly message based on HTTP status code
  const statusMessage = getHttpStatusErrorMessage(response.status);
  if (statusMessage) {
    return statusMessage;
  }

  return fallbackMessage;
}

function buildReceiptFileHref(fileUrl: string | null): string | null {
  if (!fileUrl) {
    return null;
  }

  return buildBackendUrl(fileUrl);
}

export async function fetchReceiptYears(): Promise<number[]> {
  const response = await backendFetch("/api/receipts/years", {
    headers: {
      Accept: "application/json",
    },
  });

  if (!response.ok) {
    throw new Error(
      await getApiErrorMessage(
        response,
        `Failed to load receipt years (${response.status})`,
      ),
    );
  }

  const data: unknown = await response.json();

  return receiptYearsSchema.parse(data);
}

export async function fetchReceipts(year?: number): Promise<Receipt[]> {
  const searchParams = new URLSearchParams();
  if (year !== undefined) {
    searchParams.set("year", String(year));
  }

  const suffix = searchParams.size > 0 ? `?${searchParams.toString()}` : "";
  const response = await backendFetch(`/api/receipts${suffix}`, {
    headers: {
      Accept: "application/json",
    },
  });

  if (!response.ok) {
    throw new Error(
      await getApiErrorMessage(
        response,
        `Failed to load receipts (${response.status})`,
      ),
    );
  }

  const data: unknown = await response.json();

  return receiptsSchema.parse(data);
}

export async function createReceipt(
  payload: ReceiptMutationRequest,
): Promise<Receipt> {
  const response = await backendFetch("/api/receipts", {
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
        `Failed to create receipt (${response.status})`,
      ),
    );
  }

  const data: unknown = await response.json();

  return receiptSchema.parse(data);
}

export async function updateReceipt(
  receiptId: string,
  payload: ReceiptMutationRequest,
): Promise<Receipt> {
  const response = await backendFetch(`/api/receipts/${receiptId}`, {
    method: "PUT",
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
        `Failed to update receipt (${response.status})`,
      ),
    );
  }

  const data: unknown = await response.json();

  return receiptSchema.parse(data);
}

export async function deleteReceipt(receiptId: string): Promise<void> {
  const response = await backendFetch(`/api/receipts/${receiptId}`, {
    method: "DELETE",
  });

  if (!response.ok) {
    throw new Error(
      await getApiErrorMessage(
        response,
        `Failed to delete receipt (${response.status})`,
      ),
    );
  }
}

export async function fetchReceiptsForUserYear(year: number): Promise<Receipt[]> {
  const response = await backendFetch(`/api/user-years/${year}/receipts`, {
    headers: {
      Accept: "application/json",
    },
  });

  if (!response.ok) {
    throw new Error(
      await getApiErrorMessage(
        response,
        `Failed to load receipts for ${year} (${response.status})`,
      ),
    );
  }

  const data: unknown = await response.json();

  return receiptsSchema.parse(data);
}

export async function uploadReceiptForUserYear(
  year: number,
  payload: UploadYearReceiptRequest,
): Promise<Receipt> {
  const uploadIntentResponse = await fetch(
    buildBackendUrl(`/api/user-years/${year}/receipts/upload-intent`),
    {
      method: "POST",
      credentials: "include",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        reliefCategoryId: payload.reliefCategoryId,
        fileName: payload.file.name,
        mimeType: payload.file.type || "application/octet-stream",
        fileSizeBytes: payload.file.size,
      }),
    },
  );

  if (!uploadIntentResponse.ok) {
    throw new Error(
      await getApiErrorMessage(
        uploadIntentResponse,
        `Failed to create receipt upload intent for ${year} (${uploadIntentResponse.status})`,
      ),
    );
  }

  const uploadIntentData: unknown = await uploadIntentResponse.json();
  const uploadIntent = receiptUploadIntentSchema.parse(uploadIntentData);

  const uploadResponse = await fetch(buildBackendUrl(uploadIntent.uploadUrl), {
    method: uploadIntent.uploadMethod,
    headers: uploadIntent.uploadHeaders,
    body: payload.file,
  });

  if (!uploadResponse.ok) {
    throw new Error(
      await getApiErrorMessage(
        uploadResponse,
        `Failed to upload receipt file for ${year} (${uploadResponse.status})`,
      ),
    );
  }

  const response = await fetch(
    buildBackendUrl(`/api/user-years/${year}/receipts/confirm-upload`),
    {
      method: "POST",
      credentials: "include",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        uploadIntentId: uploadIntent.uploadIntentId,
        notes: payload.notes ?? null,
      }),
    },
  );

  if (!response.ok) {
    throw new Error(
      await getApiErrorMessage(
        response,
        `Failed to confirm receipt upload for ${year} (${response.status})`,
      ),
    );
  }

  const data: unknown = await response.json();

  return receiptSchema.parse(data);
}

export async function confirmReceiptReview(
  receiptId: string,
  payload: ConfirmReceiptReviewRequest = {},
): Promise<Receipt> {
  const response = await fetch(
    buildBackendUrl(`/api/receipts/${receiptId}/review/confirm`),
    {
      method: "POST",
      credentials: "include",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify(payload),
    },
  );

  if (!response.ok) {
    throw new Error(
      await getApiErrorMessage(
        response,
        `Failed to confirm receipt review (${response.status})`,
      ),
    );
  }

  const data: unknown = await response.json();

  return receiptSchema.parse(data);
}

export async function rejectReceiptReview(
  receiptId: string,
  notes?: string | null,
): Promise<Receipt> {
  const response = await fetch(
    buildBackendUrl(`/api/receipts/${receiptId}/review/reject`),
    {
      method: "POST",
      credentials: "include",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ notes: notes ?? null }),
    },
  );

  if (!response.ok) {
    throw new Error(
      await getApiErrorMessage(
        response,
        `Failed to reject receipt review (${response.status})`,
      ),
    );
  }

  const data: unknown = await response.json();

  return receiptSchema.parse(data);
}

export function resolveReceiptFileUrl(fileUrl: string | null): string | null {
  return buildReceiptFileHref(fileUrl);
}
