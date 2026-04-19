"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState, type FormEvent } from "react";
import { formatCurrency } from "../../lib/format-currency";
import {
  fetchReceipt,
  replaceReceiptFile,
  resolveReceiptFileUrl,
  updateReceipt,
  type Receipt,
} from "../../lib/receipts";
import { getStorageTier } from "../../lib/storage-tier";
import { fetchUserYearWorkspace } from "../../lib/user-years";

type EditFormState = {
  reliefCategoryId: string;
  merchantName: string;
  receiptDate: string;
  amount: string;
  notes: string;
};

type EditFormErrors = {
  merchantName?: string;
  receiptDate?: string;
  amount?: string;
  file?: string;
};

function normalizeOptionalString(value: string): string | null {
  const trimmed = value.trim();
  return trimmed === "" ? null : trimmed;
}

function CurrentFileDisplay({ receipt }: Readonly<{ receipt: Receipt }>) {
  const fileUrl = resolveReceiptFileUrl(receipt.fileUrl);
  const tier = getStorageTier(receipt.uploadedAt);

  return (
    <div className="rounded-card border border-brand-line bg-brand-ice px-5 py-4">
      <p className="text-sm font-semibold text-brand-black">Current attached file</p>
      <p className="mt-2 text-sm text-brand-muted">{receipt.fileName ?? "Stored receipt"}</p>
      {tier === "glacier" ? (
        <p className="mt-2 flex items-center gap-2 text-sm text-brand-muted">
          <span className="app-pill">Glacier Deep Archive</span>
          File archived for long-term storage — not directly accessible
        </p>
      ) : fileUrl ? (
        <a
          href={fileUrl}
          target="_blank"
          rel="noreferrer"
          className="mt-3 inline-flex items-center gap-2 text-sm font-semibold text-brand-blue transition hover:text-brand-black"
        >
          Open current file
          {tier === "standard-ia" ? <span className="app-pill">(archived)</span> : null}
        </a>
      ) : null}
    </div>
  );
}

export default function EditReceiptWorkspace({
  year,
  receiptId,
}: Readonly<{
  year: number;
  receiptId: string;
}>) {
  const router = useRouter();
  const queryClient = useQueryClient();

  const [form, setForm] = useState<EditFormState>({
    reliefCategoryId: "",
    merchantName: "",
    receiptDate: "",
    amount: "",
    notes: "",
  });
  const [formErrors, setFormErrors] = useState<EditFormErrors>({});
  const [newFile, setNewFile] = useState<File | null>(null);

  const receiptQuery = useQuery({
    queryKey: ["receipt", receiptId],
    queryFn: () => fetchReceipt(receiptId),
  });

  const workspaceQuery = useQuery({
    queryKey: ["user-year-workspace", year],
    queryFn: () => fetchUserYearWorkspace(year),
  });

  const receipt = receiptQuery.data;
  const categories = workspaceQuery.data?.categories ?? [];
  const selectedCategory = categories.find((c) => c.reliefCategoryId === form.reliefCategoryId);

  useEffect(() => {
    if (receipt) {
      setForm({
        reliefCategoryId: receipt.reliefCategoryId ?? "",
        merchantName: receipt.merchantName ?? "",
        receiptDate: receipt.receiptDate ?? "",
        amount: receipt.amount != null ? receipt.amount.toFixed(2) : "",
        notes: receipt.notes ?? "",
      });
    }
  }, [receipt]);

  const saveMutation = useMutation({
    mutationFn: async () => {
      const amount = Number(form.amount);
      if (newFile) {
        return replaceReceiptFile(receiptId, {
          merchantName: form.merchantName.trim(),
          receiptDate: form.receiptDate.trim(),
          amount,
          reliefCategoryId: normalizeOptionalString(form.reliefCategoryId),
          notes: normalizeOptionalString(form.notes),
          file: newFile,
        });
      } else {
        return updateReceipt(receiptId, {
          policyYear: year,
          merchantName: form.merchantName.trim(),
          receiptDate: form.receiptDate.trim(),
          amount,
          reliefCategoryId: normalizeOptionalString(form.reliefCategoryId),
          notes: normalizeOptionalString(form.notes),
          fileName: receipt?.fileName ?? null,
          fileUrl: receipt?.fileUrl ?? null,
        });
      }
    },
    onSuccess: async (updated: Receipt) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["user-year-workspace", year] }),
        queryClient.invalidateQueries({ queryKey: ["user-year-receipts", year] }),
        queryClient.invalidateQueries({ queryKey: ["receipt", receiptId] }),
      ]);
      const targetCategory = updated.reliefCategoryId ?? form.reliefCategoryId;
      router.push(`/year/${year}/category/${targetCategory}`);
    },
  });

  function validate(): EditFormErrors {
    const errors: EditFormErrors = {};
    if (!form.merchantName.trim()) {
      errors.merchantName = "Merchant name is required.";
    }
    if (!form.receiptDate.trim()) {
      errors.receiptDate = "Receipt date is required.";
    }
    const amount = Number(form.amount);
    if (!form.amount.trim() || amount <= 0) {
      errors.amount = "Amount must be greater than zero.";
    } else if (selectedCategory?.maxAmount && amount > selectedCategory.maxAmount) {
      errors.amount = `Amount cannot exceed ${formatCurrency(selectedCategory.maxAmount)} for this category.`;
    }
    if (newFile) {
      const allowedTypes = ["application/pdf", "image/png", "image/jpeg"];
      if (!allowedTypes.includes(newFile.type)) {
        errors.file = "Only PDF, PNG, and JPG files are accepted.";
      } else if (newFile.size > 10 * 1024 * 1024) {
        errors.file = `File is too large (${(newFile.size / 1024 / 1024).toFixed(1)} MB). Maximum is 10 MB.`;
      }
    }
    return errors;
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const errors = validate();
    if (Object.keys(errors).length > 0) {
      setFormErrors(errors);
      return;
    }
    setFormErrors({});
    saveMutation.mutate();
  }

  if (receiptQuery.isLoading || workspaceQuery.isLoading) {
    return (
      <section className="app-panel p-6 sm:p-7">
        <p className="app-eyebrow">Edit Receipt</p>
        <h1 className="mt-3 text-4xl text-brand-black">Loading...</h1>
      </section>
    );
  }

  if (receiptQuery.error instanceof Error || !receipt) {
    return (
      <section className="app-panel p-6 sm:p-7">
        <p className="app-eyebrow">Edit Receipt</p>
        <h1 className="mt-3 text-4xl text-brand-black">Receipt not found</h1>
        <p className="mt-3 text-sm leading-7 text-brand-muted">
          {receiptQuery.error instanceof Error
            ? receiptQuery.error.message
            : "This receipt could not be loaded."}
        </p>
        <div className="mt-6">
          <Link href={`/year/${year}`} className="app-button-secondary">
            Back to {year} workspace
          </Link>
        </div>
      </section>
    );
  }

  return (
    <section className="app-panel p-6 sm:p-7">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <p className="app-eyebrow">Edit Receipt · {year}</p>
          <h1 className="mt-3 text-4xl text-brand-black">
            Update {receipt.merchantName ?? receipt.fileName ?? "receipt"}
          </h1>
          <p className="mt-3 text-sm leading-7 text-brand-muted">
            All fields are updatable. To replace the attached file, select a new one below.
          </p>
        </div>
        {receipt.reliefCategoryId ? (
          <Link
            href={`/year/${year}/category/${receipt.reliefCategoryId}`}
            className="app-button-secondary shrink-0"
          >
            Back to category
          </Link>
        ) : (
          <Link href={`/year/${year}`} className="app-button-secondary shrink-0">
            Back to {year} workspace
          </Link>
        )}
      </div>

      <form className="mt-6 space-y-5" onSubmit={handleSubmit}>
        <label className="block">
          <span className="app-label">Relief category</span>
          <select
            className="app-input"
            value={form.reliefCategoryId}
            onChange={(e) => setForm((f) => ({ ...f, reliefCategoryId: e.target.value }))}
          >
            <option value="">No category</option>
            {categories.map((cat) => (
              <option key={cat.reliefCategoryId} value={cat.reliefCategoryId}>
                {cat.name}
              </option>
            ))}
          </select>
        </label>

        <div className="grid gap-5 sm:grid-cols-2">
          <div className="block">
            <label className="app-label">
              Merchant name <span className="text-red-500">*</span>
            </label>
            <input
              type="text"
              className={`app-input ${formErrors.merchantName ? "border-red-400 focus:border-red-400 focus:ring-red-400/20" : ""}`}
              value={form.merchantName}
              onChange={(e) => {
                setForm((f) => ({ ...f, merchantName: e.target.value }));
                if (e.target.value.trim()) setFormErrors((err) => ({ ...err, merchantName: undefined }));
              }}
            />
            {formErrors.merchantName ? (
              <p className="mt-1.5 text-xs text-red-600">{formErrors.merchantName}</p>
            ) : null}
          </div>

          <div className="block">
            <label className="app-label">
              Receipt date <span className="text-red-500">*</span>
            </label>
            <input
              type="date"
              className={`app-input ${formErrors.receiptDate ? "border-red-400 focus:border-red-400 focus:ring-red-400/20" : ""}`}
              value={form.receiptDate}
              onChange={(e) => {
                setForm((f) => ({ ...f, receiptDate: e.target.value }));
                if (e.target.value.trim()) setFormErrors((err) => ({ ...err, receiptDate: undefined }));
              }}
            />
            {formErrors.receiptDate ? (
              <p className="mt-1.5 text-xs text-red-600">{formErrors.receiptDate}</p>
            ) : null}
          </div>
        </div>

        <div className="block">
          <label className="app-label">
            Amount (RM) <span className="text-red-500">*</span>
          </label>
          <input
            type="number"
            min="0.01"
            step="0.01"
            max={selectedCategory?.maxAmount ?? undefined}
            className={`app-input ${formErrors.amount ? "border-red-400 focus:border-red-400 focus:ring-red-400/20" : ""}`}
            value={form.amount}
            onChange={(e) => {
              setForm((f) => ({ ...f, amount: e.target.value }));
              if (Number(e.target.value) > 0) setFormErrors((err) => ({ ...err, amount: undefined }));
            }}
          />
          {selectedCategory?.maxAmount ? (
            <p className="app-help">Maximum claimable: {formatCurrency(selectedCategory.maxAmount)}</p>
          ) : null}
          {formErrors.amount ? (
            <p className="mt-1.5 text-xs text-red-600">{formErrors.amount}</p>
          ) : null}
        </div>

        <label className="block">
          <span className="app-label">Notes</span>
          <textarea
            rows={4}
            className="app-input"
            value={form.notes}
            onChange={(e) => setForm((f) => ({ ...f, notes: e.target.value }))}
          />
        </label>

        <CurrentFileDisplay receipt={receipt} />

        <div className="block">
          <label className="app-label">Replace file (optional)</label>
          <input
            type="file"
            accept="application/pdf,image/png,image/jpeg"
            className={`app-input file:mr-4 file:rounded-full file:border-0 file:bg-brand-black file:px-4 file:py-2 file:text-sm file:font-semibold file:text-brand-white ${formErrors.file ? "border-red-400 focus:border-red-400 focus:ring-red-400/20" : ""}`}
            onChange={(e) => {
              const file = e.target.files?.[0] ?? null;
              setNewFile(file);
              if (file) setFormErrors((err) => ({ ...err, file: undefined }));
            }}
          />
          <p className="app-help">Leave empty to keep the current file · PDF, PNG, JPG · max 10 MB</p>
          {newFile ? (
            <p className="mt-1.5 text-sm text-brand-muted">
              New file: {newFile.name} ({(newFile.size / 1024 / 1024).toFixed(2)} MB)
            </p>
          ) : null}
          {formErrors.file ? (
            <p className="mt-1.5 text-xs text-red-600">{formErrors.file}</p>
          ) : null}
        </div>

        {saveMutation.error instanceof Error ? (
          <div className="rounded-card border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
            {saveMutation.error.message}
          </div>
        ) : null}

        <div className="flex flex-col gap-3 sm:flex-row">
          <button
            type="submit"
            className="app-button-primary"
            disabled={saveMutation.isPending}
          >
            {saveMutation.isPending ? "Saving..." : "Save changes"}
          </button>
          <button
            type="button"
            className="app-button-secondary"
            onClick={() => router.back()}
            disabled={saveMutation.isPending}
          >
            Cancel
          </button>
        </div>
      </form>
    </section>
  );
}
