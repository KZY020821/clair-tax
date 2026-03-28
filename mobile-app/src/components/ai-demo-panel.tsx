import { useQuery } from "@tanstack/react-query";
import { StyleSheet, Text, View } from "react-native";
import { fetchAiDemoSummary } from "@/lib/ai-demo-summary";
import { colors, radii } from "@/theme/tokens";
import { Eyebrow, EmptyState, LoadingBlock, Panel, Pill, SectionTitle } from "@/components/ui";

export function AiDemoPanel() {
  const { data, error, isLoading, isFetching } = useQuery({
    queryKey: ["ai-demo-summary"],
    queryFn: fetchAiDemoSummary,
  });

  if (isLoading) {
    return (
      <Panel>
        <Eyebrow>AI Service</Eyebrow>
        <LoadingBlock message="Loading FastAPI demo summary..." />
      </Panel>
    );
  }

  if (error instanceof Error) {
    return (
      <Panel tone="strong">
        <Eyebrow>AI Service</Eyebrow>
        <Text style={styles.strongTitle}>AI summary unavailable</Text>
        <Text style={styles.strongCopy}>{error.message}</Text>
      </Panel>
    );
  }

  if (!data) {
    return (
      <Panel>
        <SectionTitle eyebrow="AI Service" title="Demo extraction summary" />
        <EmptyState>No AI summary was returned yet.</EmptyState>
      </Panel>
    );
  }

  return (
    <Panel>
      <SectionTitle
        eyebrow="AI Service"
        title="Demo extraction summary"
        detail="Keep AI extraction visible, but quiet. The panel should support review work without competing with the main filing tasks."
        right={<Pill tone={isFetching ? "blue" : "default"}>{isFetching ? "Refreshing" : "Connected"}</Pill>}
      />
      <View style={styles.grid}>
        <View style={styles.metricCard}>
          <Text style={styles.metricLabel}>Detected receipts</Text>
          <Text style={styles.metricValue}>{data.detected_receipt_count}</Text>
          <Text style={styles.metricDetail}>
            Extracted total RM {data.extracted_total_amount.toFixed(2)}
          </Text>
        </View>
        <View style={styles.previewCard}>
          <Text style={styles.previewLabel}>Suggestion preview</Text>
          <Text style={styles.previewText}>{data.suggestion_preview}</Text>
          <Text style={styles.previewStatus}>{data.status}</Text>
          <Text style={styles.previewDate}>
            Generated at {new Date(data.generated_at).toLocaleDateString("en-MY")}
          </Text>
        </View>
      </View>
    </Panel>
  );
}

const styles = StyleSheet.create({
  strongTitle: {
    fontSize: 28,
    lineHeight: 32,
    fontWeight: "700",
    color: colors.white,
  },
  strongCopy: {
    fontSize: 14,
    lineHeight: 22,
    color: "rgba(255,255,255,0.8)",
  },
  grid: {
    gap: 14,
  },
  metricCard: {
    borderRadius: radii.card,
    borderWidth: 1,
    borderColor: colors.line,
    backgroundColor: colors.ice,
    padding: 18,
  },
  metricLabel: {
    fontSize: 12,
    lineHeight: 16,
    fontWeight: "700",
    letterSpacing: 1.8,
    textTransform: "uppercase",
    color: colors.muted,
  },
  metricValue: {
    marginTop: 8,
    fontSize: 30,
    lineHeight: 34,
    fontWeight: "700",
    color: colors.black,
  },
  metricDetail: {
    marginTop: 12,
    fontSize: 14,
    lineHeight: 22,
    color: colors.muted,
  },
  previewCard: {
    borderRadius: radii.card,
    borderWidth: 1,
    borderColor: colors.line,
    backgroundColor: colors.white,
    padding: 18,
  },
  previewLabel: {
    fontSize: 12,
    lineHeight: 16,
    fontWeight: "700",
    letterSpacing: 1.8,
    textTransform: "uppercase",
    color: colors.blue,
  },
  previewText: {
    marginTop: 8,
    fontSize: 16,
    lineHeight: 28,
    color: colors.black,
  },
  previewStatus: {
    marginTop: 16,
    fontSize: 13,
    lineHeight: 16,
    fontWeight: "700",
    letterSpacing: 1.8,
    textTransform: "uppercase",
    color: colors.blue,
  },
  previewDate: {
    marginTop: 12,
    fontSize: 14,
    lineHeight: 22,
    color: colors.muted,
  },
});
