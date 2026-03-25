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

const apiErrorSchema = z.object({
  message: z.string().min(1),
});

const currentDevUserSchema = z.object({
  id: z.string().uuid(),
  email: z.string().email(),
  mode: z.string().min(1),
});

const receiptSchema = z.object({
  id: z.string().uuid(),
  policyYear: z.number().int(),
  merchantName: z.string().min(1),
  receiptDate: z.string().min(1),
  amount: moneyValueSchema,
  reliefCategoryId: z.string().uuid().nullable(),
  reliefCategoryName: z.string().nullable(),
  notes: z.string().nullable(),
  fileName: z.string().nullable(),
  fileUrl: z.string().nullable(),
  createdAt: z.string().datetime({ offset: true }),
  updatedAt: z.string().datetime({ offset: true }),
});

const receiptYearsSchema = z.array(z.number().int());
const receiptsSchema = z.array(receiptSchema);

export type DevCurrentUser = z.infer<typeof currentDevUserSchema>;
export type Receipt = z.infer<typeof receiptSchema>;
export type UploadYearReceiptRequest = {
  merchantName: string;
  receiptDate: string;
  amount: number;
  reliefCategoryId: string;
  notes?: string | null;
  file: File;
};

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

  return fileUrl.startsWith("http://") || fileUrl.startsWith("https://")
    ? fileUrl
    : `${backendApiBaseUrl}${fileUrl}`;
}

export async function fetchDevCurrentUser(): Promise<DevCurrentUser> {
  const response = await fetch(`${backendApiBaseUrl}/api/dev/me`, {
    headers: {
      Accept: "application/json",
    },
  });

  if (!response.ok) {
    throw new Error(
      await getApiErrorMessage(
        response,
        `Failed to load current dev user (${response.status})`,
      ),
    );
  }

  const data: unknown = await response.json();

  return currentDevUserSchema.parse(data);
}

export async function fetchReceiptYears(): Promise<number[]> {
  const response = await fetch(`${backendApiBaseUrl}/api/receipts/years`, {
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
  const response = await fetch(`${backendApiBaseUrl}/api/receipts${suffix}`, {
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
  const response = await fetch(`${backendApiBaseUrl}/api/receipts`, {
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
  const response = await fetch(`${backendApiBaseUrl}/api/receipts/${receiptId}`, {
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
  const response = await fetch(`${backendApiBaseUrl}/api/receipts/${receiptId}`, {
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
  const response = await fetch(`${backendApiBaseUrl}/api/user-years/${year}/receipts`, {
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
  const formData = new FormData();
  formData.set("merchantName", payload.merchantName);
  formData.set("receiptDate", payload.receiptDate);
  formData.set("amount", payload.amount.toString());
  formData.set("reliefCategoryId", payload.reliefCategoryId);

  if (payload.notes) {
    formData.set("notes", payload.notes);
  }

  formData.set("file", payload.file);

  const response = await fetch(`${backendApiBaseUrl}/api/user-years/${year}/receipts`, {
    method: "POST",
    headers: {
      Accept: "application/json",
    },
    body: formData,
  });

  if (!response.ok) {
    throw new Error(
      await getApiErrorMessage(
        response,
        `Failed to upload receipt for ${year} (${response.status})`,
      ),
    );
  }

  const data: unknown = await response.json();

  return receiptSchema.parse(data);
}

export function resolveReceiptFileUrl(fileUrl: string | null): string | null {
  return buildReceiptFileHref(fileUrl);
}
