import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import * as DocumentPicker from "expo-document-picker";
import * as ImagePicker from "expo-image-picker";
import * as Linking from "expo-linking";
import { useRouter } from "expo-router";
import { useEffect, useMemo, useState } from "react";
import { Pressable, StyleSheet, Text, View } from "react-native";
import {
  buildProfileFactList,
} from "@/lib/profile-relief-visibility";
import { formatCurrency } from "@/lib/format-currency";
import { fetchProfile } from "@/lib/profile";
import {
  Receipt,
  ReceiptMutationRequest,
  UploadReceiptFile,
  deleteReceipt,
  fetchReceiptsForUserYear,
  resolveReceiptFileUrl,
  updateReceipt,
  uploadReceiptForUserYear,
} from "@/lib/receipts";
import {
  UserYearCategorySummary,
  fetchUserYearWorkspace,
} from "@/lib/user-years";
import {
  Button,
  ChoiceChip,
  EmptyState,
  ErrorBanner,
  Hero,
  InputField,
  MetricCard,
  Panel,
  Pill,
  Screen,
  SectionTitle,
} from "@/components/ui";
import { colors, radii } from "@/theme/tokens";

type UploadFormState = {
  merchantName: string;
  receiptDate: string;
  amount: string;
  reliefCategoryId: string;
  notes: string;
  file: UploadReceiptFile | null;
};

type EditFormState = {
  merchantName: string;
  receiptDate: string;
  amount: string;
  reliefCategoryId: string;
  notes: string;
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
    merchantName: "",
    receiptDate: "",
    amount: "",
    reliefCategoryId: categories?.[0]?.reliefCategoryId ?? "",
    notes: "",
    file: null,
  };
}

function toUploadReceiptFile(
  file: Pick<UploadReceiptFile, "uri" | "name" | "mimeType">,
): UploadReceiptFile {
  return {
    uri: file.uri,
    name: file.name,
    mimeType: file.mimeType,
  };
}

function toUploadFileFromDocumentPicker(
  asset: DocumentPicker.DocumentPickerAsset,
): UploadReceiptFile {
  return toUploadReceiptFile({
    uri: asset.uri,
    name: asset.name,
    mimeType: asset.mimeType,
  });
}

function toUploadFileFromCapturedImage(
  asset: ImagePicker.ImagePickerAsset,
): UploadReceiptFile {
  const extension = asset.mimeType === "image/png" ? "png" : "jpg";

  return toUploadReceiptFile({
    uri: asset.uri,
    name: asset.fileName ?? `receipt-photo.${extension}`,
    mimeType: asset.mimeType ?? `image/${extension}`,
  });
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
    <MetricCard label={label} value={value} detail={detail} accent={accent} />
  );
}

function getClaimPercentage(claimedAmount: number, maxAmount: number): number {
  if (maxAmount <= 0) {
    return 0;
  }

  return Math.min(100, Math.max(0, (claimedAmount / maxAmount) * 100));
}

function CategorySummaryCard({
  category,
  emphasized = false,
  onUploadClick,
}: Readonly<{
  category: UserYearCategorySummary;
  emphasized?: boolean;
  onUploadClick?: (categoryId: string) => void;
}>) {
  const claimPercentage = getClaimPercentage(
    category.claimedAmount,
    category.maxAmount,
  );

  return (
    <View style={[styles.categorySummaryCard, emphasized ? styles.categorySummaryCardAccent : null]}>
      <View style={styles.categorySummaryHeader}>
        <View style={styles.categorySummaryCopy}>
          <View style={styles.chipWrap}>
            <Text style={styles.categorySummaryTitle}>{category.name}</Text>
            <Pill>{category.section}</Pill>
            {category.requiresReceipt ? <Pill tone="blue">Receipt needed</Pill> : null}
          </View>
          <Text style={styles.categorySummaryDetail}>{category.description}</Text>
        </View>

        <View style={styles.categorySummaryTotals}>
          <Text style={styles.claimedLabel}>Claimed</Text>
          <Text style={styles.claimedValue}>{formatCurrency(category.claimedAmount)}</Text>
          <Text style={styles.claimedHint}>of {formatCurrency(category.maxAmount)}</Text>
        </View>
      </View>

      <View style={styles.progressTrack}>
        <View style={[styles.progressFill, { width: `${claimPercentage}%` }]} />
      </View>

      <View style={styles.categorySummaryFooter}>
        <Text style={styles.categorySummaryMeta}>
          Remaining {formatCurrency(category.remainingAmount)} · {category.receiptCount} receipt
          {category.receiptCount === 1 ? "" : "s"} · {claimPercentage.toFixed(claimPercentage >= 10 ? 0 : 1)}% used
        </Text>
        {onUploadClick ? (
          <Button label="Upload receipt" variant="secondary" onPress={() => onUploadClick(category.reliefCategoryId)} />
        ) : null}
      </View>
    </View>
  );
}

function toEditForm(receipt: Receipt): EditFormState {
  return {
    merchantName: receipt.merchantName,
    receiptDate: receipt.receiptDate,
    amount: receipt.amount.toString(),
    reliefCategoryId: receipt.reliefCategoryId ?? "",
    notes: receipt.notes ?? "",
  };
}

export function YearWorkspaceScreen({ year }: Readonly<{ year: number }>) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const [editingReceiptId, setEditingReceiptId] = useState<string | null>(null);
  const [editForm, setEditForm] = useState<EditFormState | null>(null);
  const [uploadSourceError, setUploadSourceError] = useState<string | null>(null);

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

  const [uploadForm, setUploadForm] = useState<UploadFormState>(
    buildEmptyUploadForm(workspaceQuery.data?.categories),
  );

  const uploadMutation = useMutation({
    mutationFn: async () => {
      const merchantName = uploadForm.merchantName.trim();
      const receiptDate = uploadForm.receiptDate.trim();
      const amount = Number(uploadForm.amount);
      const reliefCategoryId =
        uploadForm.reliefCategoryId ||
        workspaceQuery.data?.categories[0]?.reliefCategoryId ||
        "";

      if (
        merchantName === "" ||
        receiptDate === "" ||
        !Number.isFinite(amount) ||
        amount < 0 ||
        reliefCategoryId === "" ||
        uploadForm.file === null
      ) {
        throw new Error("Complete the required fields before uploading a receipt.");
      }

      return uploadReceiptForUserYear(year, {
        merchantName,
        receiptDate,
        amount,
        reliefCategoryId,
        notes: normalizeOptionalString(uploadForm.notes),
        file: {
          uri: uploadForm.file.uri,
          name: uploadForm.file.name,
          mimeType: uploadForm.file.mimeType,
        },
      });
    },
    onSuccess: async () => {
      setUploadSourceError(null);
      setUploadForm(buildEmptyUploadForm(workspaceQuery.data?.categories));
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["user-year-receipts", year] }),
        queryClient.invalidateQueries({ queryKey: ["user-year-workspace", year] }),
      ]);
    },
  });

  const updateMutation = useMutation({
    mutationFn: async (receiptId: string) => {
      if (!editForm) {
        throw new Error("Select a receipt before saving.");
      }

      const payload: ReceiptMutationRequest = {
        policyYear: year,
        merchantName: editForm.merchantName.trim(),
        receiptDate: editForm.receiptDate.trim(),
        amount: Number(editForm.amount),
        reliefCategoryId: normalizeOptionalString(editForm.reliefCategoryId),
        notes: normalizeOptionalString(editForm.notes),
      };

      return updateReceipt(receiptId, payload);
    },
    onSuccess: async () => {
      setEditingReceiptId(null);
      setEditForm(null);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["user-year-receipts", year] }),
        queryClient.invalidateQueries({ queryKey: ["user-year-workspace", year] }),
      ]);
    },
  });

  const deleteMutation = useMutation({
    mutationFn: deleteReceipt,
    onSuccess: async () => {
      setEditingReceiptId(null);
      setEditForm(null);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["user-year-receipts", year] }),
        queryClient.invalidateQueries({ queryKey: ["user-year-workspace", year] }),
      ]);
    },
  });

  const workspace = workspaceQuery.data;
  const categories = workspace?.categories ?? [];
  const facts = profileQuery.data ? buildProfileFactList(profileQuery.data) : [];
  const receipts = receiptsQuery.data ?? [];
  const highlightedCategory = useMemo(
    () => categories.find((category) => category.requiresReceipt) ?? categories[0] ?? null,
    [categories],
  );

  useEffect(() => {
    if (!workspaceQuery.data) {
      return;
    }

    setUploadForm((current) =>
      current.reliefCategoryId !== "" || current.file !== null || current.merchantName !== ""
        ? current
        : buildEmptyUploadForm(workspaceQuery.data.categories),
    );
  }, [workspaceQuery.data]);

  async function chooseReceiptFile() {
    try {
      setUploadSourceError(null);

      const result = await DocumentPicker.getDocumentAsync({
        multiple: false,
        type: ["application/pdf", "image/*"],
      });

      if (!result.canceled && result.assets[0]) {
        setUploadForm((current) => ({
          ...current,
          file: toUploadFileFromDocumentPicker(result.assets[0]),
        }));
      }
    } catch {
      setUploadSourceError("Unable to open local storage right now. Please try again.");
    }
  }

  async function takeReceiptPhoto() {
    try {
      setUploadSourceError(null);

      const permission = await ImagePicker.requestCameraPermissionsAsync();

      if (!permission.granted) {
        setUploadSourceError(
          permission.canAskAgain
            ? "Allow camera access to take a receipt photo."
            : "Camera access is blocked. Enable it in your device settings to scan a receipt.",
        );
        return;
      }

      const result = await ImagePicker.launchCameraAsync({
        cameraType: ImagePicker.CameraType.back,
        mediaTypes: ["images"],
      });

      if (!result.canceled && result.assets[0]) {
        setUploadForm((current) => ({
          ...current,
          file: toUploadFileFromCapturedImage(result.assets[0]),
        }));
      }
    } catch {
      setUploadSourceError("Unable to open the camera right now. Please try again.");
    }
  }

  if (workspaceQuery.isLoading) {
    return (
      <Screen>
        <Hero
          eyebrow="Year Workspace"
          title={`Loading year ${year}...`}
          detail="Pulling the year summary, profile context, and receipt ledger."
        />
      </Screen>
    );
  }

  if (workspaceQuery.error instanceof Error) {
    return (
      <Screen>
        <Hero
          eyebrow="Year Workspace"
          title={`Year ${year} is not ready`}
          detail={workspaceQuery.error.message}
        />
        <Panel>
          <Button label="Create year workspace" onPress={() => router.push("/year/create")} />
        </Panel>
      </Screen>
    );
  }

  if (!workspace) {
    return (
      <Screen>
        <EmptyState>Year workspace data is not available.</EmptyState>
      </Screen>
    );
  }

  return (
    <Screen>
      <Hero
        eyebrow="Year Workspace"
        title={`Assessment year ${year}`}
        detail="The mobile year screen keeps the same overview-first rhythm as the web workspace: summary totals, saved-profile context, category coverage, then receipt actions and the ledger."
      />

      <View style={styles.summaryTiles}>
        <SummaryCard
          label="Categories"
          value={String(workspace.totalCategories)}
          detail="Relief categories available in this year workspace."
        />
        <SummaryCard
          label="Saved receipts"
          value={String(workspace.totalReceiptCount)}
          detail="Receipts already attached to this year."
        />
        <SummaryCard
          label="Claimed amount"
          value={formatCurrency(workspace.totalClaimedAmount)}
          detail="Total amount currently represented in the workspace."
          accent
        />
      </View>

      <Panel tone="muted">
        <SectionTitle
          eyebrow="Saved Profile"
          title="Profile-aware filing context"
          detail="The same saved profile facts from the web app stay visible here so year-by-year review remains consistent."
        />
        <View style={styles.chipWrap}>
          {facts.map((fact) => (
            <Pill key={fact}>{fact}</Pill>
          ))}
        </View>
      </Panel>

      <Panel>
        <SectionTitle
          eyebrow="Category Summary"
          title={`Relief coverage for ${year}`}
          detail="Review category usage and jump into the upload flow from the most relevant relief categories."
        />
        <View style={styles.sectionStack}>
          {categories.map((category) => (
            <CategorySummaryCard
              key={category.reliefCategoryId}
              category={category}
              emphasized={highlightedCategory?.reliefCategoryId === category.reliefCategoryId}
              onUploadClick={(categoryId) =>
                setUploadForm((current) => ({ ...current, reliefCategoryId: categoryId }))
              }
            />
          ))}
        </View>
      </Panel>

      <Panel>
        <SectionTitle
          eyebrow="Upload Receipt"
          title="Add another receipt"
          detail="The mobile upload form keeps the same required fields as the web app, with native options to scan a receipt photo or choose a local file."
        />
        <View style={styles.sectionStack}>
          <InputField
            label="Merchant name"
            value={uploadForm.merchantName}
            onChangeText={(value) => setUploadForm((current) => ({ ...current, merchantName: value }))}
            placeholder="Merchant or provider"
          />
          <InputField
            label="Receipt date"
            value={uploadForm.receiptDate}
            onChangeText={(value) => setUploadForm((current) => ({ ...current, receiptDate: value }))}
            placeholder="YYYY-MM-DD"
          />
          <InputField
            label="Amount"
            value={uploadForm.amount}
            onChangeText={(value) => setUploadForm((current) => ({ ...current, amount: value }))}
            placeholder="0.00"
            keyboardType="decimal-pad"
          />
          <InputField
            label="Notes"
            value={uploadForm.notes}
            onChangeText={(value) => setUploadForm((current) => ({ ...current, notes: value }))}
            placeholder="Optional notes"
            multiline
            numberOfLines={4}
            style={styles.notesInput}
          />

          <View style={styles.optionGroup}>
            <Text style={styles.groupTitle}>Relief category</Text>
            <View style={styles.chipWrap}>
              {categories.map((category) => (
                <ChoiceChip
                  key={category.reliefCategoryId}
                  label={category.name}
                  active={uploadForm.reliefCategoryId === category.reliefCategoryId}
                  onPress={() =>
                    setUploadForm((current) => ({
                      ...current,
                      reliefCategoryId: category.reliefCategoryId,
                    }))
                  }
                />
              ))}
            </View>
          </View>

          <View style={styles.fileCard}>
            <Text style={styles.fileTitle}>Selected file</Text>
            <Text style={styles.fileCopy}>
              {uploadForm.file ? uploadForm.file.name : "No file selected yet."}
            </Text>
            <View style={styles.fileActionStack}>
              <Button
                label="Take photo"
                variant="secondary"
                onPress={takeReceiptPhoto}
                disabled={uploadMutation.isPending}
              />
              <Button
                label={uploadForm.file ? "Choose another file" : "Choose file"}
                variant="secondary"
                onPress={chooseReceiptFile}
                disabled={uploadMutation.isPending}
              />
            </View>
          </View>

          {uploadSourceError ? (
            <ErrorBanner message={uploadSourceError} />
          ) : null}
          {uploadMutation.error instanceof Error ? (
            <ErrorBanner message={uploadMutation.error.message} />
          ) : null}

          <Button
            label={uploadMutation.isPending ? "Uploading receipt..." : "Upload receipt"}
            onPress={() => uploadMutation.mutate()}
            disabled={uploadMutation.isPending}
          />
        </View>
      </Panel>

      <Panel>
        <SectionTitle
          eyebrow="Saved Receipts"
          title="Receipt ledger"
          detail="Review, edit, open, or delete the receipts already attached to this year."
        />

        {receiptsQuery.error instanceof Error ? (
          <ErrorBanner message={receiptsQuery.error.message} />
        ) : null}

        {receipts.length > 0 ? (
          <View style={styles.sectionStack}>
            {receipts.map((receipt) => {
              const fileUrl = resolveReceiptFileUrl(receipt.fileUrl);
              const isEditing = editingReceiptId === receipt.id && editForm !== null;

              return (
                <View key={receipt.id} style={styles.receiptCard}>
                  <View style={styles.receiptHeader}>
                    <View style={styles.receiptCopy}>
                      <Text style={styles.receiptTitle}>{receipt.merchantName}</Text>
                      <Text style={styles.receiptMeta}>
                        {formatReceiptDate(receipt.receiptDate)} · {formatCurrency(receipt.amount)}
                      </Text>
                      <Text style={styles.receiptMeta}>
                        {receipt.reliefCategoryName ?? "Unassigned category"}
                      </Text>
                      {receipt.notes ? (
                        <Text style={styles.receiptNote}>{receipt.notes}</Text>
                      ) : null}
                    </View>
                    <View style={styles.receiptActions}>
                      {fileUrl ? (
                        <Button
                          label="Open file"
                          variant="secondary"
                          onPress={() => Linking.openURL(fileUrl)}
                        />
                      ) : null}
                      <Button
                        label={isEditing ? "Close editor" : "Edit"}
                        variant="secondary"
                        onPress={() => {
                          if (isEditing) {
                            setEditingReceiptId(null);
                            setEditForm(null);
                            return;
                          }

                          setEditingReceiptId(receipt.id);
                          setEditForm(toEditForm(receipt));
                        }}
                      />
                    </View>
                  </View>

                  {isEditing && editForm ? (
                    <View style={styles.editCard}>
                      <InputField
                        label="Merchant name"
                        value={editForm.merchantName}
                        onChangeText={(value) => setEditForm((current) => current ? { ...current, merchantName: value } : current)}
                      />
                      <InputField
                        label="Receipt date"
                        value={editForm.receiptDate}
                        onChangeText={(value) => setEditForm((current) => current ? { ...current, receiptDate: value } : current)}
                        placeholder="YYYY-MM-DD"
                      />
                      <InputField
                        label="Amount"
                        value={editForm.amount}
                        onChangeText={(value) => setEditForm((current) => current ? { ...current, amount: value } : current)}
                        keyboardType="decimal-pad"
                      />
                      <InputField
                        label="Notes"
                        value={editForm.notes}
                        onChangeText={(value) => setEditForm((current) => current ? { ...current, notes: value } : current)}
                        multiline
                        numberOfLines={3}
                        style={styles.notesInput}
                      />
                      <View style={styles.optionGroup}>
                        <Text style={styles.groupTitle}>Relief category</Text>
                        <View style={styles.chipWrap}>
                          {categories.map((category) => (
                            <ChoiceChip
                              key={category.reliefCategoryId}
                              label={category.name}
                              active={editForm.reliefCategoryId === category.reliefCategoryId}
                              onPress={() =>
                                setEditForm((current) =>
                                  current
                                    ? {
                                        ...current,
                                        reliefCategoryId: category.reliefCategoryId,
                                      }
                                    : current,
                                )
                              }
                            />
                          ))}
                        </View>
                      </View>

                      {updateMutation.error instanceof Error ? (
                        <ErrorBanner message={updateMutation.error.message} />
                      ) : null}
                      {deleteMutation.error instanceof Error ? (
                        <ErrorBanner message={deleteMutation.error.message} />
                      ) : null}

                      <View style={styles.editButtonStack}>
                        <Button
                          label={updateMutation.isPending ? "Saving..." : "Save receipt"}
                          onPress={() => updateMutation.mutate(receipt.id)}
                          disabled={updateMutation.isPending}
                        />
                        <Button
                          label={deleteMutation.isPending ? "Deleting..." : "Delete receipt"}
                          variant="secondary"
                          onPress={() => deleteMutation.mutate(receipt.id)}
                          disabled={deleteMutation.isPending}
                        />
                      </View>
                    </View>
                  ) : null}
                </View>
              );
            })}
          </View>
        ) : (
          <EmptyState>
            No receipts have been uploaded for this year yet. Add one from the upload form to start the ledger.
          </EmptyState>
        )}
      </Panel>
    </Screen>
  );
}

const styles = StyleSheet.create({
  summaryTiles: {
    gap: 14,
  },
  sectionStack: {
    gap: 14,
  },
  chipWrap: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
  },
  categorySummaryCard: {
    borderRadius: radii.card,
    borderWidth: 1,
    borderColor: colors.line,
    backgroundColor: colors.white,
    padding: 16,
    gap: 14,
  },
  categorySummaryCardAccent: {
    borderColor: colors.blue,
    backgroundColor: colors.ice,
  },
  categorySummaryHeader: {
    gap: 12,
  },
  categorySummaryCopy: {
    gap: 8,
  },
  categorySummaryTitle: {
    fontSize: 16,
    lineHeight: 22,
    fontWeight: "700",
    color: colors.black,
  },
  categorySummaryDetail: {
    fontSize: 14,
    lineHeight: 24,
    color: colors.muted,
  },
  categorySummaryTotals: {
    gap: 4,
  },
  claimedLabel: {
    fontSize: 11,
    lineHeight: 16,
    fontWeight: "700",
    letterSpacing: 2.0,
    textTransform: "uppercase",
    color: colors.muted,
  },
  claimedValue: {
    fontSize: 24,
    lineHeight: 28,
    fontWeight: "700",
    color: colors.black,
  },
  claimedHint: {
    fontSize: 14,
    lineHeight: 20,
    color: colors.muted,
  },
  progressTrack: {
    height: 14,
    borderRadius: radii.pill,
    borderWidth: 1,
    borderColor: colors.line,
    backgroundColor: colors.ice,
    overflow: "hidden",
  },
  progressFill: {
    height: "100%",
    borderRadius: radii.pill,
    backgroundColor: colors.blue,
  },
  categorySummaryFooter: {
    gap: 10,
  },
  categorySummaryMeta: {
    fontSize: 14,
    lineHeight: 22,
    color: colors.muted,
  },
  notesInput: {
    minHeight: 96,
    textAlignVertical: "top",
  },
  optionGroup: {
    gap: 10,
  },
  groupTitle: {
    fontSize: 14,
    lineHeight: 18,
    fontWeight: "700",
    color: colors.black,
  },
  fileCard: {
    borderRadius: radii.card,
    borderWidth: 1,
    borderColor: colors.line,
    backgroundColor: colors.ice,
    padding: 16,
    gap: 8,
  },
  fileTitle: {
    fontSize: 14,
    lineHeight: 18,
    fontWeight: "700",
    color: colors.black,
  },
  fileCopy: {
    fontSize: 14,
    lineHeight: 22,
    color: colors.muted,
  },
  fileActionStack: {
    gap: 10,
  },
  receiptCard: {
    borderRadius: radii.card,
    borderWidth: 1,
    borderColor: colors.line,
    backgroundColor: colors.white,
    padding: 16,
    gap: 14,
  },
  receiptHeader: {
    gap: 12,
  },
  receiptCopy: {
    gap: 6,
  },
  receiptTitle: {
    fontSize: 16,
    lineHeight: 22,
    fontWeight: "700",
    color: colors.black,
  },
  receiptMeta: {
    fontSize: 14,
    lineHeight: 20,
    color: colors.muted,
  },
  receiptNote: {
    fontSize: 14,
    lineHeight: 22,
    color: colors.black,
  },
  receiptActions: {
    gap: 10,
  },
  editCard: {
    borderTopWidth: 1,
    borderTopColor: colors.line,
    paddingTop: 14,
    gap: 12,
  },
  editButtonStack: {
    gap: 10,
  },
});
