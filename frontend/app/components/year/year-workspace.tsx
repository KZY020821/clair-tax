"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useRef, useState, type FormEvent } from "react";
import { formatCurrency } from "../../lib/format-currency";
import { fetchProfile } from "../../lib/profile";
import { buildProfileFactList } from "../../lib/profile-relief-visibility";
import {
  deleteReceipt,
  fetchReceiptsForUserYear,
  resolveReceiptFileUrl,
  updateReceipt,
  uploadReceiptForUserYear,
  type Receipt,
  type ReceiptMutationRequest,
} from "../../lib/receipts";
import {
  fetchUserYearWorkspace,
  type UserYearCategorySummary,
} from "../../lib/user-years";

type Notification = {
  id: string;
  message: string;
  type: "success" | "error" | "info";
};

type UploadFormState = {
  reliefCategoryId: string;
  merchantName: string;
  receiptDate: string;
  amount: string;
  notes: string;
  file: File | null;
};

type UploadFormErrors = {
  merchantName?: string;
  receiptDate?: string;
  amount?: string;
  file?: string;
};

type EditFormState = {
  merchantName: string;
  receiptDate: string;
  amount: string;
  reliefCategoryId: string;
  notes: string;
  fileName: string | null;
  fileUrl: string | null;
};

function formatReceiptDate(receiptDate: string): string {
  return new Intl.DateTimeFormat("en-MY", {
    day: "numeric",
    month: "short",
    year: "numeric",
  }).format(new Date(`${receiptDate}T00:00:00`));
}

function normalizeOptionalString(value: string): string | null {
  const trimmed = value.trim();
  return trimmed === "" ? null : trimmed;
}

function buildEmptyUploadForm(
  categories: UserYearCategorySummary[] | undefined,
): UploadFormState {
  return {
    reliefCategoryId: categories?.[0]?.reliefCategoryId ?? "",
    merchantName: "",
    receiptDate: "",
    amount: "",
    notes: "",
    file: null,
  };
}

function formatReceiptStatus(status: string): string {
  switch (status) {
    case "uploaded":
      return "Uploaded";
    case "processing":
      return "Processing";
    case "processed":
      return "Ready for review";
    case "verified":
      return "Verified";
    case "rejected":
      return "Rejected";
    case "failed":
      return "Failed";
    default:
      return status;
  }
}

function SummaryCard({
  label,
  value,
  detail,
  accent = false,
}: Readonly<{
  label: string;
  value: string;
  detail: string;
  accent?: boolean;
}>) {
  return (
    <article className={accent ? "metric-card-accent" : "metric-card"}>
      <p className="text-sm font-medium text-brand-muted">{label}</p>
      <p className="mt-3 text-3xl text-brand-black">{value}</p>
      <p className="mt-3 text-sm leading-6 text-brand-muted">{detail}</p>
    </article>
  );
}

function getClaimPercentage(
  claimedAmount: number,
  maxAmount: number,
): number {
  if (maxAmount <= 0) {
    return 0;
  }

  return Math.min(100, Math.max(0, (claimedAmount / maxAmount) * 100));
}

function CategorySummaryCard({
  category,
  emphasized = false,
  onUploadClick,
  year,
}: Readonly<{
  category: UserYearCategorySummary;
  emphasized?: boolean;
  onUploadClick?: (categoryId: string) => void;
  year?: number;
}>) {
  const claimPercentage = getClaimPercentage(
    category.claimedAmount,
    category.maxAmount,
  );

  return (
    <article
      className={
        emphasized
          ? "rounded-card border border-brand-blue bg-brand-ice px-5 py-5 shadow-accent"
          : "rounded-card border border-brand-line bg-brand-white px-5 py-5"
      }
    >
      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <p className="text-base font-semibold text-brand-black">
              {category.name}
            </p>
            <span className="app-pill">{category.section}</span>
            {category.requiresReceipt ? (
              <span className="app-pill-blue">Receipt needed</span>
            ) : null}
          </div>
          <p className="mt-2 max-w-3xl text-sm leading-6 text-brand-muted">
            {category.description}
          </p>
        </div>

        <div className="shrink-0 text-left lg:text-right">
          <p className="text-[11px] font-semibold uppercase tracking-[0.22em] text-brand-muted">
            Claimed
          </p>
          <p className="mt-2 text-2xl text-brand-black">
            {formatCurrency(category.claimedAmount)}
          </p>
          <p className="mt-1 text-sm leading-6 text-brand-muted">
            of {formatCurrency(category.maxAmount)}
          </p>
        </div>
      </div>

      <div className="year-claim-track" aria-hidden="true">
        <div
          className="year-claim-fill"
          style={{ width: `${claimPercentage}%` }}
        />
      </div>

      <div className="mt-4 flex flex-col gap-3 text-sm leading-6 text-brand-muted sm:flex-row sm:items-center sm:justify-between">
        <div className="flex flex-wrap items-center gap-x-4 gap-y-1">
          <span>Remaining {formatCurrency(category.remainingAmount)}</span>
          <span>
            {category.receiptCount} receipt
            {category.receiptCount === 1 ? "" : "s"}
          </span>
          <span className="font-semibold text-brand-black">
            {claimPercentage.toFixed(claimPercentage >= 10 ? 0 : 1)}% used
          </span>
        </div>

        <div className="flex flex-wrap gap-2">
          {year !== undefined ? (
            <Link
              href={`/year/${year}/category/${category.reliefCategoryId}`}
              className="app-button-secondary"
            >
              View receipts
            </Link>
          ) : null}
          {onUploadClick ? (
            <button
              type="button"
              onClick={() => onUploadClick(category.reliefCategoryId)}
              className="app-button-secondary"
            >
              Upload receipt
            </button>
          ) : null}
        </div>
      </div>
    </article>
  );
}

export default function YearWorkspace({
  year,
}: Readonly<{
  year: number;
}>) {
  const queryClient = useQueryClient();
  const notificationCounterRef = useRef(0);
  const [uploadForm, setUploadForm] = useState<UploadFormState>({
    reliefCategoryId: "",
    merchantName: "",
    receiptDate: "",
    amount: "",
    notes: "",
    file: null,
  });
  const [editingReceipt, setEditingReceipt] = useState<Receipt | null>(null);
  const [editForm, setEditForm] = useState<EditFormState | null>(null);
  const [editError, setEditError] = useState<string | null>(null);
  const [uploadFormErrors, setUploadFormErrors] = useState<UploadFormErrors>({});
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const uploadSectionRef = useRef<HTMLElement>(null);

  const workspaceQuery = useQuery({
    queryKey: ["user-year-workspace", year],
    queryFn: () => fetchUserYearWorkspace(year),
  });
  const profileQuery = useQuery({
    queryKey: ["profile"],
    queryFn: fetchProfile,
  });
  const receiptsQuery = useQuery({
    queryKey: ["user-year-receipts", year],
    queryFn: () => fetchReceiptsForUserYear(year),
    enabled: workspaceQuery.isSuccess,
  });

  const uploadMutation = useMutation({
    mutationFn: () => {
      const reliefCategoryId =
        uploadForm.reliefCategoryId ||
        workspaceQuery.data?.categories[0]?.reliefCategoryId ||
        "";

      const allowedTypes = ["application/pdf", "image/png", "image/jpeg"];
      if (!uploadForm.file || !allowedTypes.includes(uploadForm.file.type)) {
        throw new Error("Only PDF, PNG, and JPG files are accepted.");
      }
      if (uploadForm.file.size > 10 * 1024 * 1024) {
        throw new Error(`File is too large (${(uploadForm.file.size / 1024 / 1024).toFixed(1)}MB). Maximum is 10MB.`);
      }

      return uploadReceiptForUserYear(year, {
        merchantName: uploadForm.merchantName.trim(),
        receiptDate: uploadForm.receiptDate.trim(),
        amount: uploadForm.amount.trim(),
        reliefCategoryId: reliefCategoryId || null,
        notes: normalizeOptionalString(uploadForm.notes),
        file: uploadForm.file,
      });
    },
    onSuccess: async () => {
      setUploadForm(buildEmptyUploadForm(workspaceQuery.data?.categories));
      setUploadFormErrors({});
      showNotification("Receipt saved successfully.");
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["user-year-workspace", year] }),
        queryClient.invalidateQueries({ queryKey: ["user-year-receipts", year] }),
        queryClient.invalidateQueries({ queryKey: ["receipt-years"] }),
      ]);
    },
  });

  const updateMutation = useMutation({
    mutationFn: ({
      receiptId,
      payload,
    }: Readonly<{ receiptId: string; payload: ReceiptMutationRequest }>) =>
      updateReceipt(receiptId, payload),
    onSuccess: async () => {
      setEditError(null);
      setEditingReceipt(null);
      setEditForm(null);
      showNotification("Receipt updated successfully!");
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["user-year-workspace", year] }),
        queryClient.invalidateQueries({ queryKey: ["user-year-receipts", year] }),
      ]);
    },
  });

  const deleteMutation = useMutation({
    mutationFn: deleteReceipt,
    onSuccess: async (_, deletedReceiptId) => {
      if (editingReceipt?.id === deletedReceiptId) {
        setEditingReceipt(null);
        setEditForm(null);
      }

      showNotification("Receipt deleted successfully!");
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["user-year-workspace", year] }),
        queryClient.invalidateQueries({ queryKey: ["user-year-receipts", year] }),
        queryClient.invalidateQueries({ queryKey: ["receipt-years"] }),
      ]);
    },
  });

  const workspace = workspaceQuery.data;
  const receipts = receiptsQuery.data ?? [];
  const categories = workspace?.categories ?? [];
  const profileFacts = profileQuery.data ? buildProfileFactList(profileQuery.data) : [];
  const activeUploadCategory =
    categories.find(
      (category) =>
        category.reliefCategoryId ===
        (uploadForm.reliefCategoryId || categories[0]?.reliefCategoryId || ""),
    ) ?? categories[0];

  function showNotification(
    message: string,
    type: "success" | "error" | "info" = "success",
  ) {
    notificationCounterRef.current += 1;
    const id = `notification-${notificationCounterRef.current}`;
    const notification: Notification = { id, message, type };

    setNotifications((current) => [...current, notification]);

    // Auto-dismiss after 5 seconds
    setTimeout(() => {
      setNotifications((current) =>
        current.filter((n) => n.id !== notification.id),
      );
    }, 5000);
  }

  function dismissNotification(id: string) {
    setNotifications((current) => current.filter((n) => n.id !== id));
  }

  function validateUploadForm(): UploadFormErrors {
    const errors: UploadFormErrors = {};
    if (!uploadForm.merchantName.trim()) {
      errors.merchantName = "Merchant name is required.";
    }
    if (!uploadForm.receiptDate.trim()) {
      errors.receiptDate = "Receipt date is required.";
    }
    const amount = Number(uploadForm.amount);
    if (!uploadForm.amount.trim() || amount <= 0) {
      errors.amount = "Amount must be greater than zero.";
    } else if (activeUploadCategory?.maxAmount && amount > activeUploadCategory.maxAmount) {
      errors.amount = `Amount cannot exceed ${formatCurrency(activeUploadCategory.maxAmount)} for this category.`;
    }
    if (!uploadForm.file) {
      errors.file = "Please attach a receipt file.";
    }
    return errors;
  }

  function handleUploadSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const errors = validateUploadForm();
    if (Object.keys(errors).length > 0) {
      setUploadFormErrors(errors);
      return;
    }
    setUploadFormErrors({});
    uploadMutation.mutate();
  }

  function handleCategoryUploadClick(categoryId: string) {
    setUploadForm((current) => ({
      ...current,
      reliefCategoryId: categoryId,
    }));

    setTimeout(() => {
      uploadSectionRef.current?.scrollIntoView({
        behavior: 'smooth',
        block: 'start'
      });
    }, 0);
  }

  function handleEdit(receipt: Receipt) {
    if (
      receipt.merchantName === null ||
      receipt.receiptDate === null ||
      receipt.amount === null
    ) {
      showNotification(
        "Finish reviewing the extracted receipt before editing the saved values.",
        "info",
      );
      return;
    }

    setEditingReceipt(receipt);
    setEditError(null);
    setEditForm({
      merchantName: receipt.merchantName,
      receiptDate: receipt.receiptDate,
      amount: receipt.amount.toFixed(2),
      reliefCategoryId: receipt.reliefCategoryId ?? "",
      notes: receipt.notes ?? "",
      fileName: receipt.fileName ?? null,
      fileUrl: receipt.fileUrl ?? null,
    });
  }

  function handleUpdateSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!editingReceipt || !editForm) {
      return;
    }

    const merchantName = editForm.merchantName.trim();
    const receiptDate = editForm.receiptDate.trim();
    const amount = Number(editForm.amount);

    if (
      merchantName === "" ||
      receiptDate === "" ||
      !Number.isFinite(amount) ||
      amount < 0
    ) {
      setEditError("Complete the required fields before saving the receipt.");
      return;
    }

    setEditError(null);
    updateMutation.mutate({
      receiptId: editingReceipt.id,
      payload: {
        policyYear: year,
        merchantName,
        receiptDate,
        amount,
        reliefCategoryId: normalizeOptionalString(editForm.reliefCategoryId),
        notes: normalizeOptionalString(editForm.notes),
        fileName: editForm.fileName,
        fileUrl: editForm.fileUrl,
      },
    });
  }

  function handleDelete(receipt: Receipt) {
    if (
      !window.confirm(
        `Delete the receipt ${receipt.fileName ?? receipt.id}?`,
      )
    ) {
      return;
    }

    deleteMutation.mutate(receipt.id);
  }

  if (workspaceQuery.isLoading) {
    return (
      <div className="space-y-6">
        <section className="app-panel p-6 sm:p-7">
          <p className="app-eyebrow">Year Workspace</p>
          <h1 className="mt-3 text-4xl text-brand-black">Loading year {year}...</h1>
          <p className="mt-3 text-sm leading-7 text-brand-muted">
            Pulling the workspace summary, available categories, and receipts for
            this year.
          </p>
        </section>
      </div>
    );
  }

  if (workspaceQuery.error instanceof Error || !workspace) {
    return (
      <div className="space-y-6">
        <section className="app-panel p-6 sm:p-7">
          <p className="app-eyebrow">Year Workspace</p>
          <h1 className="mt-3 text-4xl text-brand-black">Year {year} is not ready</h1>
          <p className="mt-3 text-sm leading-7 text-brand-muted">
            {workspaceQuery.error instanceof Error
              ? workspaceQuery.error.message
              : "Create the year workspace first, then return here to upload receipts."}
          </p>
          <div className="mt-6 flex flex-col gap-3 sm:flex-row">
            <Link href="/year/create" className="app-button-primary">
              Create year workspace
            </Link>
            <Link href="/" className="app-button-secondary">
              Back to dashboard
            </Link>
          </div>
        </section>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <section className="app-panel p-6 sm:p-7">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <p className="app-eyebrow">Year Workspace</p>
            <h1 className="mt-3 text-4xl text-brand-black">Assessment year {year}</h1>
            <p className="mt-3 max-w-3xl text-sm leading-7 text-brand-muted">
              Categories, caps, and current claim totals come from backend policy
              data plus the receipts already stored for this year for the signed-in account.
            </p>
          </div>
          <div className="rounded-card border border-brand-line bg-brand-ice px-5 py-4">
            <p className="text-sm font-medium text-brand-black">Status</p>
            <p className="mt-2 text-3xl text-brand-black">{workspace.status}</p>
            <p className="mt-2 text-sm leading-6 text-brand-muted">
              Created {new Date(workspace.createdAt).toLocaleDateString("en-MY")}
            </p>
          </div>
        </div>
      </section>

      <section className="grid gap-4 lg:grid-cols-3">
        <SummaryCard
          label="Total claimed"
          value={formatCurrency(workspace.totalClaimedAmount)}
          detail="The sum of receipt-backed and saved-profile relief amounts already visible for this year."
        />
        <SummaryCard
          label="Receipts on file"
          value={String(workspace.totalReceiptCount)}
          detail="Uploaded receipts count immediately; claimed totals move only after review."
        />
        <SummaryCard
          label="Available categories"
          value={String(workspace.totalCategories)}
          detail="These categories come from the selected policy year, not from hardcoded frontend values."
        />
      </section>

      <section className="rounded-card border border-brand-line bg-brand-ice px-5 py-5">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <p className="app-eyebrow">Saved Profile</p>
            <h2 className="mt-3 text-2xl text-brand-black">
              Year detail uses the saved household profile
            </h2>
            <p className="mt-3 text-sm leading-7 text-brand-muted">
              Family and disability fields are no longer collected here. The
              visible category summary and upload choices already reflect the
              saved profile.
            </p>
          </div>
          <Link href="/profile" className="app-button-secondary">
            Edit profile
          </Link>
        </div>

        {profileQuery.isLoading ? (
          <div className="mt-5 rounded-card border border-brand-line bg-brand-white px-4 py-4 text-sm leading-6 text-brand-muted">
            Loading the saved profile summary...
          </div>
        ) : profileQuery.error instanceof Error ? (
          <div className="mt-5 rounded-card border border-brand-line bg-brand-white px-4 py-4 text-sm leading-6 text-brand-muted">
            {profileQuery.error.message}
          </div>
        ) : (
          <div className="mt-5 flex flex-wrap gap-2">
            {profileFacts.map((fact) => (
              <span key={fact} className="app-pill">
                {fact}
              </span>
            ))}
          </div>
        )}
      </section>

      <section className="app-panel p-6 sm:p-7">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <p className="app-eyebrow">Category Summary</p>
            <h2 className="mt-3 text-3xl text-brand-black">Relief coverage for {year}</h2>
            <p className="mt-3 text-sm leading-7 text-brand-muted">
              Review the maximum claim, current claimed amount, remaining room,
              and receipt count for each category before adding another receipt.
            </p>
          </div>
        </div>

        <div className="mt-6 grid gap-3">
          {categories.map((category) => (
            <CategorySummaryCard
              key={category.reliefCategoryId}
              category={category}
              year={year}
              onUploadClick={handleCategoryUploadClick}
            />
          ))}
        </div>
      </section>

      <section
        ref={uploadSectionRef}
        className="grid gap-4 xl:grid-cols-[minmax(0,1.05fr)_minmax(20rem,0.95fr)]"
      >
        <article className="app-panel p-6 sm:p-7">
          <p className="app-eyebrow">Upload Receipt</p>
          <h2 className="mt-3 text-3xl text-brand-black">Add another receipt</h2>
          <p className="mt-3 text-sm leading-7 text-brand-muted">
            Fill in all required fields, attach the receipt file, and submit. The receipt is saved immediately.
          </p>

          <form name="upload-receipt" className="mt-6 space-y-5" onSubmit={handleUploadSubmit}>
            <label className="block">
              <span className="app-label">Relief category</span>
              <select
                className="app-input"
                value={uploadForm.reliefCategoryId || categories[0]?.reliefCategoryId || ""}
                onChange={(event) => {
                  setUploadForm((current) => ({
                    ...current,
                    reliefCategoryId: event.target.value,
                  }));
                }}
              >
                {categories.map((category) => (
                  <option
                    key={category.reliefCategoryId}
                    value={category.reliefCategoryId}
                  >
                    {category.name}
                    {category.requiresReceipt ? " · receipt needed" : ""}
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
                  className={`app-input ${uploadFormErrors.merchantName ? "border-red-400 focus:border-red-400 focus:ring-red-400/20" : ""}`}
                  placeholder="e.g. Caring Pharmacy"
                  value={uploadForm.merchantName}
                  onChange={(event) => {
                    setUploadForm((current) => ({ ...current, merchantName: event.target.value }));
                    if (event.target.value.trim()) {
                      setUploadFormErrors((e) => ({ ...e, merchantName: undefined }));
                    }
                  }}
                />
                {uploadFormErrors.merchantName ? (
                  <p className="mt-1.5 text-xs text-red-600">{uploadFormErrors.merchantName}</p>
                ) : null}
              </div>

              <div className="block">
                <label className="app-label">
                  Receipt date <span className="text-red-500">*</span>
                </label>
                <input
                  type="date"
                  className={`app-input ${uploadFormErrors.receiptDate ? "border-red-400 focus:border-red-400 focus:ring-red-400/20" : ""}`}
                  value={uploadForm.receiptDate}
                  onChange={(event) => {
                    setUploadForm((current) => ({ ...current, receiptDate: event.target.value }));
                    if (event.target.value.trim()) {
                      setUploadFormErrors((e) => ({ ...e, receiptDate: undefined }));
                    }
                  }}
                />
                {uploadFormErrors.receiptDate ? (
                  <p className="mt-1.5 text-xs text-red-600">{uploadFormErrors.receiptDate}</p>
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
                max={activeUploadCategory?.maxAmount ?? undefined}
                className={`app-input ${uploadFormErrors.amount ? "border-red-400 focus:border-red-400 focus:ring-red-400/20" : ""}`}
                placeholder="0.00"
                value={uploadForm.amount}
                onChange={(event) => {
                  setUploadForm((current) => ({ ...current, amount: event.target.value }));
                  if (Number(event.target.value) > 0) {
                    setUploadFormErrors((e) => ({ ...e, amount: undefined }));
                  }
                }}
              />
              {activeUploadCategory?.maxAmount ? (
                <p className="app-help">Maximum claimable: {formatCurrency(activeUploadCategory.maxAmount)}</p>
              ) : null}
              {uploadFormErrors.amount ? (
                <p className="mt-1.5 text-xs text-red-600">{uploadFormErrors.amount}</p>
              ) : null}
            </div>

            <label className="block">
              <span className="app-label">Notes</span>
              <textarea
                rows={4}
                className="app-input"
                name="notes"
                value={uploadForm.notes}
                onChange={(event) => {
                  setUploadForm((current) => ({ ...current, notes: event.target.value }));
                }}
              />
            </label>

            <div className="block">
              <label className="app-label">
                Receipt file <span className="text-red-500">*</span>
              </label>
              <input
                type="file"
                accept="application/pdf,image/png,image/jpeg"
                className={`app-input file:mr-4 file:rounded-full file:border-0 file:bg-brand-black file:px-4 file:py-2 file:text-sm file:font-semibold file:text-brand-white ${uploadFormErrors.file ? "border-red-400 focus:border-red-400 focus:ring-red-400/20" : ""}`}
                onChange={(event) => {
                  const file = event.target.files?.[0] ?? null;
                  setUploadForm((current) => ({ ...current, file }));
                  if (file) {
                    setUploadFormErrors((e) => ({ ...e, file: undefined }));
                  }
                }}
              />
              <p className="app-help">Accepted formats: PDF, PNG, JPG · Maximum size: 10MB</p>
              {uploadFormErrors.file ? (
                <p className="mt-1.5 text-xs text-red-600">{uploadFormErrors.file}</p>
              ) : null}
              {uploadForm.file ? (
                <div className="mt-2 space-y-1">
                  <p className="text-sm text-brand-muted">
                    Selected: {uploadForm.file.name} ({(uploadForm.file.size / 1024 / 1024).toFixed(2)}MB)
                  </p>
                  {!["application/pdf", "image/png", "image/jpeg"].includes(uploadForm.file.type) ? (
                    <p className="text-xs text-red-600">Unsupported file type. Please choose a PDF, PNG, or JPG file.</p>
                  ) : uploadForm.file.size > 10 * 1024 * 1024 ? (
                    <p className="text-xs text-red-600">File exceeds the 10MB limit. Please choose a smaller file.</p>
                  ) : null}
                </div>
              ) : null}
            </div>

            {activeUploadCategory ? (
              <div className="rounded-card border border-brand-line bg-brand-ice px-5 py-5">
                <p className="text-sm font-semibold text-brand-black">
                  Selected category summary
                </p>
                <p className="mt-2 text-sm leading-7 text-brand-muted">
                  Verified receipts from this category contribute to the summary.
                </p>
                <div className="mt-4">
                  <CategorySummaryCard
                    category={activeUploadCategory}
                    emphasized
                    onUploadClick={undefined}
                  />
                </div>
              </div>
            ) : null}

            {uploadMutation.error instanceof Error ? (
              <div className="rounded-card border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
                {uploadMutation.error.message}
              </div>
            ) : null}

            <div className="flex flex-col gap-3 sm:flex-row">
              <button
                type="submit"
                className="app-button-primary"
                disabled={uploadMutation.isPending}
              >
                {uploadMutation.isPending ? "Uploading receipt..." : "Upload receipt"}
              </button>
              <button
                type="button"
                className="app-button-secondary"
                onClick={() => {
                  setUploadForm(buildEmptyUploadForm(categories));
                }}
              >
                Clear form
              </button>
            </div>
          </form>
        </article>

        <article className="app-panel p-6 sm:p-7">
          <p className="app-eyebrow">Saved Receipts</p>
          <h2 className="mt-3 text-3xl text-brand-black">Receipt ledger</h2>
          <p className="mt-3 text-sm leading-7 text-brand-muted">
            Every uploaded receipt here belongs to year {year}. Only verified
            receipts affect the claimed totals above.
          </p>

          {receiptsQuery.isLoading ? (
            <div className="mt-6 rounded-card border border-brand-line bg-brand-ice px-5 py-5 text-sm text-brand-muted">
              Loading receipts...
            </div>
          ) : receiptsQuery.error instanceof Error ? (
            <div className="mt-6 rounded-card border border-red-200 bg-red-50 px-5 py-5 text-sm text-red-700">
              {receiptsQuery.error.message}
            </div>
          ) : receipts.length === 0 ? (
            <div className="mt-6 rounded-card border border-dashed border-brand-line px-5 py-5 text-sm leading-7 text-brand-muted">
              No receipts yet for {year}. Upload the first one from the form and
              the category summary will update immediately.
            </div>
          ) : (
            <div className="mt-6 space-y-3">
              {receipts.map((receipt) => {
                const fileUrl = resolveReceiptFileUrl(receipt.fileUrl);

                return (
                  <article
                    key={receipt.id}
                    className="rounded-card border border-brand-line bg-brand-white px-5 py-5"
                  >
                    <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                      <div className="min-w-0">
                        <div className="flex flex-wrap items-center gap-2">
                          <p className="text-base font-semibold text-brand-black">
                            {receipt.merchantName ??
                              receipt.latestExtraction?.merchantName ??
                              receipt.fileName ??
                              "Receipt awaiting extraction"}
                          </p>
                          {receipt.reliefCategoryName ? (
                            <span className="app-pill">{receipt.reliefCategoryName}</span>
                          ) : null}
                          <span className="app-pill-blue">
                            {formatReceiptStatus(receipt.status)}
                          </span>
                        </div>
                        <p className="mt-2 text-sm leading-7 text-brand-muted">
                          {receipt.receiptDate
                            ? formatReceiptDate(receipt.receiptDate)
                            : receipt.latestExtraction?.receiptDate
                              ? formatReceiptDate(receipt.latestExtraction.receiptDate)
                              : "Date pending"}{" "}
                          ·{" "}
                          {receipt.amount !== null
                            ? formatCurrency(receipt.amount)
                            : receipt.latestExtraction?.totalAmount !== null &&
                                receipt.latestExtraction?.totalAmount !== undefined
                              ? `${formatCurrency(receipt.latestExtraction.totalAmount)} (candidate)`
                              : "Amount pending"}
                        </p>
                        {receipt.notes ? (
                          <p className="mt-2 text-sm leading-7 text-brand-muted">
                            {receipt.notes}
                          </p>
                        ) : null}
                        {receipt.latestExtraction ? (
                          <div className="mt-3 rounded-card border border-brand-line bg-brand-ice px-4 py-4 text-sm leading-6 text-brand-muted">
                            <p className="font-semibold text-brand-black">
                              Latest extraction
                            </p>
                            <p className="mt-2">
                              Confidence {(receipt.latestExtraction.confidenceScore * 100).toFixed(0)}%
                              via {receipt.latestExtraction.providerName}.
                            </p>
                            {receipt.latestExtraction.warnings.length > 0 ? (
                              <p className="mt-2">
                                {receipt.latestExtraction.warnings.join(" ")}
                              </p>
                            ) : null}
                          </div>
                        ) : null}
                        {receipt.processingErrorMessage ? (
                          <p className="mt-2 text-sm leading-7 text-red-700">
                            {receipt.processingErrorMessage}
                          </p>
                        ) : null}
                        {fileUrl ? (
                          <a
                            href={fileUrl}
                            target="_blank"
                            rel="noreferrer"
                            className="mt-3 inline-flex text-sm font-semibold text-brand-blue transition hover:text-brand-black"
                          >
                            Open {receipt.fileName ?? "receipt file"}
                          </a>
                        ) : null}
                      </div>

                      <div className="flex flex-wrap gap-3">
                        <button
                          type="button"
                          className="app-button-secondary"
                          onClick={() => {
                            handleEdit(receipt);
                          }}
                          // commented for now, as no AI is integrated yet
                          // disabled={receipt.status !== "verified"}
                        >
                          Edit
                        </button>
                        <button
                          type="button"
                          className="app-button-danger"
                          onClick={() => {
                            handleDelete(receipt);
                          }}
                          disabled={deleteMutation.isPending}
                        >
                          Delete
                        </button>
                      </div>
                    </div>
                  </article>
                );
              })}
            </div>
          )}
        </article>
      </section>

      {editingReceipt && editForm ? (
        <section className="app-panel p-6 sm:p-7">
          <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
            <div>
              <p className="app-eyebrow">Edit Receipt</p>
              <h2 className="mt-3 text-3xl text-brand-black">
                Update {editingReceipt.merchantName}
              </h2>
              <p className="mt-3 text-sm leading-7 text-brand-muted">
                File replacement is not part of this step yet, so the existing file
                stays attached while you adjust the year, category, and amount data.
              </p>
            </div>
            <button
              type="button"
              className="app-button-secondary"
              onClick={() => {
                setEditingReceipt(null);
                setEditForm(null);
                setEditError(null);
              }}
            >
              Close
            </button>
          </div>

          <form className="mt-6 space-y-5" onSubmit={handleUpdateSubmit}>
            <div className="grid gap-5 md:grid-cols-2">
              <label className="block">
                <span className="app-label">Relief category</span>
                <select
                  className="app-input"
                  value={editForm.reliefCategoryId}
                  onChange={(event) => {
                    setEditForm((current) =>
                      current
                        ? {
                            ...current,
                            reliefCategoryId: event.target.value,
                          }
                        : current,
                    );
                  }}
                >
                  <option value="">No category</option>
                  {categories.map((category) => (
                    <option
                      key={category.reliefCategoryId}
                      value={category.reliefCategoryId}
                    >
                      {category.name}
                    </option>
                  ))}
                </select>
              </label>

              <label className="block">
                <span className="app-label">Merchant name</span>
                <input
                  className="app-input"
                  value={editForm.merchantName}
                  onChange={(event) => {
                    setEditForm((current) =>
                      current
                        ? {
                            ...current,
                            merchantName: event.target.value,
                          }
                        : current,
                    );
                  }}
                />
              </label>

              <label className="block">
                <span className="app-label">Receipt date</span>
                <input
                  type="date"
                  className="app-input"
                  value={editForm.receiptDate}
                  onChange={(event) => {
                    setEditForm((current) =>
                      current
                        ? {
                            ...current,
                            receiptDate: event.target.value,
                          }
                        : current,
                    );
                  }}
                />
              </label>

              <label className="block">
                <span className="app-label">Amount</span>
                <input
                  type="number"
                  min="0"
                  step="0.01"
                  className="app-input"
                  value={editForm.amount}
                  onChange={(event) => {
                    setEditForm((current) =>
                      current
                        ? {
                            ...current,
                            amount: event.target.value,
                          }
                        : current,
                    );
                  }}
                />
              </label>
            </div>

            <label className="block">
              <span className="app-label">Notes</span>
              <textarea
                rows={4}
                className="app-input"
                value={editForm.notes}
                onChange={(event) => {
                  setEditForm((current) =>
                    current
                      ? {
                          ...current,
                          notes: event.target.value,
                        }
                      : current,
                  );
                }}
              />
            </label>

            {editForm.fileName || editForm.fileUrl ? (
              <div className="rounded-card border border-brand-line bg-brand-ice px-5 py-5">
                <p className="text-sm font-semibold text-brand-black">Attached file</p>
                <p className="mt-2 text-sm leading-7 text-brand-muted">
                  {editForm.fileName ?? "Stored receipt"}
                </p>
                {resolveReceiptFileUrl(editForm.fileUrl) ? (
                  <a
                    href={resolveReceiptFileUrl(editForm.fileUrl) ?? "#"}
                    target="_blank"
                    rel="noreferrer"
                    className="mt-3 inline-flex text-sm font-semibold text-brand-blue transition hover:text-brand-black"
                  >
                    Open attached file
                  </a>
                ) : null}
              </div>
            ) : null}

            {editError ? (
              <div className="rounded-card border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
                {editError}
              </div>
            ) : null}

            {updateMutation.error instanceof Error ? (
              <div className="rounded-card border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
                {updateMutation.error.message}
              </div>
            ) : null}

            <div className="flex flex-col gap-3 sm:flex-row">
              <button
                type="submit"
                className="app-button-primary"
                disabled={updateMutation.isPending}
              >
                {updateMutation.isPending ? "Saving receipt..." : "Save changes"}
              </button>
              <button
                type="button"
                className="app-button-secondary"
                onClick={() => {
                  setEditingReceipt(null);
                  setEditForm(null);
                  setEditError(null);
                }}
              >
                Cancel
              </button>
            </div>
          </form>
        </section>
      ) : null}

      {/* Notification Toast Container */}
      {notifications.length > 0 ? (
        <div className="fixed bottom-4 right-4 z-50 flex w-full max-w-sm flex-col gap-3 px-4 sm:bottom-6 sm:right-6 sm:px-0">
          {notifications.map((notification) => (
            <div
              key={notification.id}
              className={`
                animate-slide-in-right rounded-card border px-5 py-4 shadow-lg transition-all
                ${
                  notification.type === "success"
                    ? "border-green-200 bg-green-50"
                    : notification.type === "error"
                      ? "border-red-200 bg-red-50"
                      : "border-blue-200 bg-blue-50"
                }
              `}
            >
              <div className="flex items-start justify-between gap-3">
                <div className="flex items-start gap-3">
                  {notification.type === "success" ? (
                    <svg
                      className="mt-0.5 h-5 w-5 shrink-0 text-green-600"
                      fill="none"
                      stroke="currentColor"
                      viewBox="0 0 24 24"
                    >
                      <path
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        strokeWidth={2}
                        d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"
                      />
                    </svg>
                  ) : notification.type === "error" ? (
                    <svg
                      className="mt-0.5 h-5 w-5 shrink-0 text-red-600"
                      fill="none"
                      stroke="currentColor"
                      viewBox="0 0 24 24"
                    >
                      <path
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        strokeWidth={2}
                        d="M10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m7-2a9 9 0 11-18 0 9 9 0 0118 0z"
                      />
                    </svg>
                  ) : (
                    <svg
                      className="mt-0.5 h-5 w-5 shrink-0 text-blue-600"
                      fill="none"
                      stroke="currentColor"
                      viewBox="0 0 24 24"
                    >
                      <path
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        strokeWidth={2}
                        d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
                      />
                    </svg>
                  )}
                  <p
                    className={`text-sm font-medium ${
                      notification.type === "success"
                        ? "text-green-800"
                        : notification.type === "error"
                          ? "text-red-800"
                          : "text-blue-800"
                    }`}
                  >
                    {notification.message}
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => dismissNotification(notification.id)}
                  className={`shrink-0 rounded-full p-1 transition hover:bg-white/50 ${
                    notification.type === "success"
                      ? "text-green-600"
                      : notification.type === "error"
                        ? "text-red-600"
                        : "text-blue-600"
                  }`}
                  aria-label="Dismiss notification"
                >
                  <svg
                    className="h-4 w-4"
                    fill="none"
                    stroke="currentColor"
                    viewBox="0 0 24 24"
                  >
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth={2}
                      d="M6 18L18 6M6 6l12 12"
                    />
                  </svg>
                </button>
              </div>
            </div>
          ))}
        </div>
      ) : null}
    </div>
  );
}
