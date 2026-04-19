"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { formatCurrency } from "../../lib/format-currency";
import {
  deleteReceipt,
  fetchReceiptsForUserYear,
  resolveReceiptFileUrl,
  type Receipt,
} from "../../lib/receipts";
import { getStorageTier } from "../../lib/storage-tier";
import { fetchUserYearWorkspace } from "../../lib/user-years";

function formatReceiptDate(receiptDate: string): string {
  return new Intl.DateTimeFormat("en-MY", {
    day: "numeric",
    month: "short",
    year: "numeric",
  }).format(new Date(`${receiptDate}T00:00:00`));
}

function ReceiptFileLink({
  receipt,
}: Readonly<{ receipt: Receipt }>) {
  const fileUrl = resolveReceiptFileUrl(receipt.fileUrl);
  if (!fileUrl) return null;

  const tier = getStorageTier(receipt.uploadedAt);

  if (tier === "glacier") {
    return (
      <span className="mt-3 inline-flex items-center gap-2 text-sm text-brand-muted">
        <span className="app-pill">Glacier Deep Archive</span>
        File not directly accessible — archived for long-term storage
      </span>
    );
  }

  return (
    <a
      href={fileUrl}
      target="_blank"
      rel="noreferrer"
      className="mt-3 inline-flex items-center gap-2 text-sm font-semibold text-brand-blue transition hover:text-brand-black"
    >
      Open {receipt.fileName ?? "receipt file"}
      {tier === "standard-ia" ? (
        <span className="app-pill">(archived)</span>
      ) : null}
    </a>
  );
}

function formatStatus(status: string): string {
  switch (status) {
    case "verified": return "Verified";
    case "uploaded": return "Uploaded";
    case "processing": return "Processing";
    case "processed": return "Ready for review";
    case "rejected": return "Rejected";
    case "failed": return "Failed";
    default: return status;
  }
}

export default function CategoryReceiptsWorkspace({
  year,
  categoryId,
}: Readonly<{
  year: number;
  categoryId: string;
}>) {
  const queryClient = useQueryClient();

  const workspaceQuery = useQuery({
    queryKey: ["user-year-workspace", year],
    queryFn: () => fetchUserYearWorkspace(year),
  });

  const receiptsQuery = useQuery({
    queryKey: ["user-year-receipts", year],
    queryFn: () => fetchReceiptsForUserYear(year),
    enabled: workspaceQuery.isSuccess,
  });

  const deleteMutation = useMutation({
    mutationFn: deleteReceipt,
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["user-year-workspace", year] }),
        queryClient.invalidateQueries({ queryKey: ["user-year-receipts", year] }),
        queryClient.invalidateQueries({ queryKey: ["receipt-years"] }),
      ]);
    },
  });

  function handleDelete(receipt: Receipt) {
    if (!window.confirm(`Delete the receipt "${receipt.merchantName ?? receipt.fileName ?? receipt.id}"?`)) {
      return;
    }
    deleteMutation.mutate(receipt.id);
  }

  const workspace = workspaceQuery.data;
  const category = workspace?.categories.find((c) => c.reliefCategoryId === categoryId);
  const allReceipts = receiptsQuery.data ?? [];
  const categoryReceipts = allReceipts.filter((r) => r.reliefCategoryId === categoryId);

  if (workspaceQuery.isLoading) {
    return (
      <section className="app-panel p-6 sm:p-7">
        <p className="app-eyebrow">Category</p>
        <h1 className="mt-3 text-4xl text-brand-black">Loading...</h1>
      </section>
    );
  }

  if (workspaceQuery.error instanceof Error || (workspace && !category)) {
    return (
      <section className="app-panel p-6 sm:p-7">
        <p className="app-eyebrow">Category</p>
        <h1 className="mt-3 text-4xl text-brand-black">Category not found</h1>
        <p className="mt-3 text-sm leading-7 text-brand-muted">
          This relief category does not exist in the {year} workspace.
        </p>
        <div className="mt-6">
          <Link href={`/year/${year}`} className="app-button-secondary">
            Back to {year} workspace
          </Link>
        </div>
      </section>
    );
  }

  const claimPercentage = category
    ? Math.min(100, Math.max(0, (category.claimedAmount / category.maxAmount) * 100))
    : 0;

  return (
    <div className="space-y-6">
      <section className="app-panel p-6 sm:p-7">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <p className="app-eyebrow">Category · {year}</p>
            <h1 className="mt-3 text-4xl text-brand-black">{category?.name}</h1>
            {category?.description ? (
              <p className="mt-3 max-w-3xl text-sm leading-7 text-brand-muted">
                {category.description}
              </p>
            ) : null}
          </div>
          <Link href={`/year/${year}`} className="app-button-secondary shrink-0">
            Back to {year} workspace
          </Link>
        </div>

        {category ? (
          <div className="mt-6 grid gap-4 sm:grid-cols-3">
            <div className="rounded-card border border-brand-line bg-brand-ice px-4 py-4">
              <p className="text-xs font-semibold uppercase tracking-[0.2em] text-brand-muted">Claimed</p>
              <p className="mt-2 text-2xl text-brand-black">{formatCurrency(category.claimedAmount)}</p>
              <p className="mt-1 text-sm text-brand-muted">of {formatCurrency(category.maxAmount)}</p>
            </div>
            <div className="rounded-card border border-brand-line bg-brand-ice px-4 py-4">
              <p className="text-xs font-semibold uppercase tracking-[0.2em] text-brand-muted">Remaining</p>
              <p className="mt-2 text-2xl text-brand-black">{formatCurrency(category.remainingAmount)}</p>
            </div>
            <div className="rounded-card border border-brand-line bg-brand-ice px-4 py-4">
              <p className="text-xs font-semibold uppercase tracking-[0.2em] text-brand-muted">Receipts</p>
              <p className="mt-2 text-2xl text-brand-black">{category.receiptCount}</p>
              <p className="mt-1 text-sm text-brand-muted">{claimPercentage.toFixed(0)}% cap used</p>
            </div>
          </div>
        ) : null}
      </section>

      <section className="app-panel p-6 sm:p-7">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <p className="app-eyebrow">Receipts</p>
            <h2 className="mt-3 text-3xl text-brand-black">
              {categoryReceipts.length} receipt{categoryReceipts.length === 1 ? "" : "s"} in this category
            </h2>
          </div>
          <Link href={`/year/${year}`} className="app-button-primary shrink-0">
            Upload receipt
          </Link>
        </div>

        {receiptsQuery.isLoading ? (
          <div className="mt-6 rounded-card border border-brand-line bg-brand-ice px-5 py-5 text-sm text-brand-muted">
            Loading receipts...
          </div>
        ) : categoryReceipts.length === 0 ? (
          <div className="mt-6 rounded-card border border-dashed border-brand-line px-5 py-5 text-sm leading-7 text-brand-muted">
            No receipts yet in this category. Go back to the workspace to upload one.
          </div>
        ) : (
          <div className="mt-6 space-y-3">
            {categoryReceipts.map((receipt) => (
              <article
                key={receipt.id}
                className="rounded-card border border-brand-line bg-brand-white px-5 py-5"
              >
                <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <p className="text-base font-semibold text-brand-black">
                        {receipt.merchantName ?? receipt.fileName ?? "Receipt awaiting extraction"}
                      </p>
                      <span className="app-pill-blue">{formatStatus(receipt.status)}</span>
                    </div>
                    <p className="mt-2 text-sm leading-7 text-brand-muted">
                      {receipt.receiptDate ? formatReceiptDate(receipt.receiptDate) : "Date pending"}
                      {" · "}
                      {receipt.amount !== null ? formatCurrency(receipt.amount) : "Amount pending"}
                    </p>
                    {receipt.notes ? (
                      <p className="mt-2 text-sm leading-7 text-brand-muted">{receipt.notes}</p>
                    ) : null}
                    <ReceiptFileLink receipt={receipt} />
                    {receipt.processingErrorMessage ? (
                      <p className="mt-2 text-sm leading-7 text-red-700">{receipt.processingErrorMessage}</p>
                    ) : null}
                  </div>

                  <div className="flex flex-wrap gap-3 shrink-0">
                    {receipt.status === "verified" ? (
                      <Link
                        href={`/year/${year}/receipts/${receipt.id}/edit`}
                        className="app-button-secondary"
                      >
                        Edit
                      </Link>
                    ) : (
                      <button type="button" className="app-button-secondary" disabled>
                        Edit
                      </button>
                    )}
                    <button
                      type="button"
                      className="app-button-danger"
                      onClick={() => handleDelete(receipt)}
                      disabled={deleteMutation.isPending}
                    >
                      Delete
                    </button>
                  </div>
                </div>
              </article>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
