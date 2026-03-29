import { File } from "expo-file-system";
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

export type DevCurrentUser = z.infer<typeof currentDevUserSchema>;
export type Receipt = z.infer<typeof receiptSchema>;
export type UploadReceiptFile = {
  uri: string;
  name: string;
  mimeType?: string | null;
  sizeBytes?: number | null;
};
export type UploadYearReceiptRequest = {
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
export type ReceiptUploadIntent = z.infer<typeof receiptUploadIntentSchema>;

export type ConfirmReceiptReviewRequest = {
  merchantName?: string | null;
  receiptDate?: string | null;
  amount?: number | null;
  currency?: string | null;
  reliefCategoryId?: string | null;
  notes?: string | null;
};

function resolveUploadTargetUrl(uploadUrl: string): string {
  return uploadUrl.startsWith("http://") || uploadUrl.startsWith("https://")
    ? uploadUrl
    : getBackendUrl(uploadUrl.startsWith("/") ? uploadUrl : `/${uploadUrl}`);
}

function resolveUploadFileSize(file: UploadReceiptFile): number {
  if (
    typeof file.sizeBytes === "number" &&
    Number.isFinite(file.sizeBytes) &&
    file.sizeBytes > 0
  ) {
    return file.sizeBytes;
  }

  const localFile = new File(file.uri);
  if (Number.isFinite(localFile.size) && localFile.size > 0) {
    return localFile.size;
  }

  throw new Error("Unable to determine the selected file size. Please choose the file again.");
}

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
  const fileSizeBytes = resolveUploadFileSize(payload.file);
  const mimeType = payload.file.mimeType ?? "application/octet-stream";

  const uploadIntentResponse = await fetch(
    getBackendUrl(`/api/user-years/${year}/receipts/upload-intent`),
    {
      method: "POST",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        reliefCategoryId: payload.reliefCategoryId,
        fileName: payload.file.name,
        mimeType,
        fileSizeBytes,
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

  const uploadIntent: ReceiptUploadIntent = receiptUploadIntentSchema.parse(
    await uploadIntentResponse.json(),
  );
  const uploadFile = new File(payload.file.uri);
  const uploadResponse = await fetch(resolveUploadTargetUrl(uploadIntent.uploadUrl), {
    method: uploadIntent.uploadMethod,
    headers: uploadIntent.uploadHeaders,
    body: uploadFile,
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
    getBackendUrl(`/api/user-years/${year}/receipts/confirm-upload`),
    {
      method: "POST",
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

  return receiptSchema.parse(await response.json());
}

export async function confirmReceiptReview(
  receiptId: string,
  payload: ConfirmReceiptReviewRequest = {},
): Promise<Receipt> {
  const response = await fetch(getBackendUrl(`/api/receipts/${receiptId}/review/confirm`), {
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
        `Failed to confirm receipt review (${response.status})`,
      ),
    );
  }

  return receiptSchema.parse(await response.json());
}

export async function rejectReceiptReview(
  receiptId: string,
  notes?: string | null,
): Promise<Receipt> {
  const response = await fetch(getBackendUrl(`/api/receipts/${receiptId}/review/reject`), {
    method: "POST",
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ notes: notes ?? null }),
  });

  if (!response.ok) {
    throw new Error(
      await getApiErrorMessage(
        response,
        `Failed to reject receipt review (${response.status})`,
      ),
    );
  }

  return receiptSchema.parse(await response.json());
}

export function resolveReceiptFileUrl(fileUrl: string | null): string | null {
  return buildAbsoluteFileUrl(fileUrl);
}
