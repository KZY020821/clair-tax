import { z } from "zod";
import { fetchJson, getApiErrorMessage, getBackendUrl, buildAbsoluteFileUrl } from "./http";

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
export type UploadReceiptFile = {
  uri: string;
  name: string;
  mimeType?: string | null;
};
export type UploadYearReceiptRequest = {
  merchantName: string;
  receiptDate: string;
  amount: number;
  reliefCategoryId: string;
  notes?: string | null;
  file: UploadReceiptFile;
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

export async function fetchDevCurrentUser(): Promise<DevCurrentUser> {
  return fetchJson(getBackendUrl("/api/dev/me"), (value) =>
    currentDevUserSchema.parse(value),
  );
}

export async function fetchReceiptYears(): Promise<number[]> {
  return fetchJson(getBackendUrl("/api/receipts/years"), (value) =>
    receiptYearsSchema.parse(value),
  );
}

export async function fetchReceipts(year?: number): Promise<Receipt[]> {
  const searchParams = new URLSearchParams();
  if (year !== undefined) {
    searchParams.set("year", String(year));
  }

  const suffix = searchParams.size > 0 ? `?${searchParams.toString()}` : "";
  return fetchJson(getBackendUrl(`/api/receipts${suffix}`), (value) =>
    receiptsSchema.parse(value),
  );
}

export async function updateReceipt(
  receiptId: string,
  payload: ReceiptMutationRequest,
): Promise<Receipt> {
  const response = await fetch(getBackendUrl(`/api/receipts/${receiptId}`), {
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

  return receiptSchema.parse(await response.json());
}

export async function deleteReceipt(receiptId: string): Promise<void> {
  const response = await fetch(getBackendUrl(`/api/receipts/${receiptId}`), {
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
  return fetchJson(getBackendUrl(`/api/user-years/${year}/receipts`), (value) =>
    receiptsSchema.parse(value),
  );
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

  formData.append("file", {
    uri: payload.file.uri,
    name: payload.file.name,
    type: payload.file.mimeType ?? "application/octet-stream",
  } as unknown as Blob);

  const response = await fetch(getBackendUrl(`/api/user-years/${year}/receipts`), {
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

  return receiptSchema.parse(await response.json());
}

export function resolveReceiptFileUrl(fileUrl: string | null): string | null {
  return buildAbsoluteFileUrl(fileUrl);
}
