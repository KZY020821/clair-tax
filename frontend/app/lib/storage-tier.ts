export type StorageTier = "standard" | "standard-ia" | "glacier";

export function getStorageTier(uploadedAt: string): StorageTier {
  const days = (Date.now() - new Date(uploadedAt).getTime()) / 86_400_000;
  if (days >= 365) return "glacier";
  if (days >= 90) return "standard-ia";
  return "standard";
}
