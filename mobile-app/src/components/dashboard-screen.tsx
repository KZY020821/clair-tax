import { useQuery } from "@tanstack/react-query";
import { useRouter } from "expo-router";
import { Pressable, StyleSheet, Text, View } from "react-native";
import { fetchAiDemoSummary } from "@/lib/ai-demo-summary";
import { formatCurrency } from "@/lib/format-currency";
import { fetchPolicyYears } from "@/lib/policy-years";
import { AiDemoPanel } from "@/components/ai-demo-panel";
import { PolicyYearsPanel } from "@/components/policy-years-panel";
import { Button, Hero, MetricCard, Panel, Pill, Screen, SectionTitle } from "@/components/ui";
import { colors, radii } from "@/theme/tokens";

const suggestionCards = [
  {
    title: "Child care relief",
    detail: "Keep child care receipts tidy so eligible family expenses are easier to review.",
    hint: "Family-related claims often depend on complete supporting documents.",
  },
  {
    title: "Self-education relief",
    detail: "Group course fees and training receipts early before the filing period gets busy.",
    hint: "Use the calculator to compare potential claim amounts against the annual cap.",
  },
  {
    title: "Medical check-up relief",
    detail: "Store check-up receipts and treatment records in one place before submitting claims.",
    hint: "Medical claims benefit from a clearer review trail when every receipt is accounted for.",
  },
];

function ReadinessStatus({
  label,
  complete,
}: Readonly<{
  label: string;
  complete: boolean;
}>) {
  return (
    <View style={styles.readinessCard}>
      <View
        style={[
          styles.readinessDot,
          complete ? styles.readinessDotComplete : null,
        ]}
      />
      <Text style={styles.readinessLabel}>{label}</Text>
    </View>
  );
}

export function DashboardScreen() {
  const router = useRouter();
  const policyYearsQuery = useQuery({
    queryKey: ["policy-years"],
    queryFn: fetchPolicyYears,
  });
  const aiSummaryQuery = useQuery({
    queryKey: ["ai-demo-summary"],
    queryFn: fetchAiDemoSummary,
  });

  const policyYears = policyYearsQuery.data ?? [];
  const latestYear = policyYears.reduce(
    (latest, policyYear) => Math.max(latest, policyYear.year),
    0,
  );
  const publishedYears = policyYears.filter(
    (policyYear) => policyYear.status === "published",
  ).length;
  const pendingReceipts = aiSummaryQuery.data?.detected_receipt_count ?? 0;
  const extractedTotal = aiSummaryQuery.data?.extracted_total_amount ?? 0;

  const readinessChecks = [
    {
      label: "Policy year feed connected",
      complete: policyYearsQuery.isSuccess,
    },
    {
      label: "AI extraction service connected",
      complete: aiSummaryQuery.isSuccess,
    },
    {
      label: "Calculator workspace ready",
      complete: true,
    },
  ];
  const readinessPercent = Math.round(
    (readinessChecks.filter((check) => check.complete).length /
      readinessChecks.length) *
      100,
  );

  return (
    <Screen>
      <Hero
        eyebrow="Dashboard"
        title="Welcome back, Khor Ze Yi"
        detail="Keep the tax workspace simple, calm, and ready for review. The dashboard surfaces the latest filing context first, then keeps live backend and AI status within reach."
      />

      <View style={styles.cardStack}>
        <MetricCard
          label="Published years"
          value={policyYearsQuery.isLoading ? "..." : String(publishedYears)}
          detail={
            policyYearsQuery.error instanceof Error
              ? "Backend policy years are not available yet."
              : `${policyYears.length || 0} total years visible in the workspace.`
          }
        />
        <MetricCard
          label="Current filing year"
          value={latestYear ? String(latestYear) : "Pending"}
          detail={
            latestYear
              ? "Use the years rail to shift the dashboard into a different filing context."
              : "Load backend policy data to populate the active filing year."
          }
        />
        <MetricCard
          label="Pending receipts"
          value={String(pendingReceipts)}
          detail={
            pendingReceipts > 0
              ? `${formatCurrency(extractedTotal)} already extracted by the AI demo service.`
              : "No receipt extraction summary is available yet."
          }
          accent
        />
      </View>

      <Panel>
        <SectionTitle
          eyebrow="Workspace Readiness"
          title={`Operational status ${readinessPercent}%`}
          detail="Stay focused on whether the core filing surfaces are online before moving into year-specific work."
          right={<Pill tone="blue">{readinessPercent}% ready</Pill>}
        />
        <View style={styles.readinessStack}>
          {readinessChecks.map((check) => (
            <ReadinessStatus
              key={check.label}
              label={check.label}
              complete={check.complete}
            />
          ))}
        </View>
      </Panel>

      <Panel>
        <SectionTitle
          eyebrow="Suggestions"
          title="Tax relief suggestions"
          detail="Keep the next most useful filing clean-up actions visible, but short enough to scan in a few seconds."
          right={<Button label="Open calculator" variant="secondary" onPress={() => router.push("/calculator")} />}
        />
        <View style={styles.suggestionStack}>
          {suggestionCards.map((suggestion) => (
            <View key={suggestion.title} style={styles.suggestionCard}>
              <View style={styles.suggestionIcon}>
                <Text style={styles.suggestionIconText}>
                  {suggestion.title
                    .split(" ")
                    .map((part) => part[0])
                    .join("")
                    .slice(0, 2)}
                </Text>
              </View>
              <Text style={styles.suggestionTitle}>{suggestion.title}</Text>
              <Text style={styles.suggestionDetail}>{suggestion.detail}</Text>
              <Text style={styles.suggestionHint}>{suggestion.hint}</Text>
              <Pressable
                onPress={() => router.push("/calculator")}
                style={styles.suggestionLink}
              >
                <Text style={styles.suggestionLinkText}>View details</Text>
              </Pressable>
            </View>
          ))}
        </View>
      </Panel>

      <Panel tone="muted">
        <SectionTitle
          eyebrow="Live Status"
          title="Operational panels"
          detail="Backend policy data and the AI demo status sit together here so they stay visible, but do not compete with the rest of the workspace."
        />
      </Panel>

      <PolicyYearsPanel />
      <AiDemoPanel />
    </Screen>
  );
}

const styles = StyleSheet.create({
  cardStack: {
    gap: 14,
  },
  readinessStack: {
    gap: 10,
  },
  readinessCard: {
    borderRadius: radii.card,
    borderWidth: 1,
    borderColor: colors.line,
    backgroundColor: colors.white,
    paddingHorizontal: 16,
    paddingVertical: 14,
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
  },
  readinessDot: {
    width: 10,
    height: 10,
    borderRadius: 999,
    backgroundColor: colors.lineStrong,
  },
  readinessDotComplete: {
    backgroundColor: colors.blue,
  },
  readinessLabel: {
    fontSize: 14,
    lineHeight: 18,
    fontWeight: "600",
    color: colors.black,
  },
  suggestionStack: {
    gap: 14,
  },
  suggestionCard: {
    borderRadius: radii.card,
    borderWidth: 1,
    borderColor: colors.line,
    backgroundColor: colors.white,
    padding: 20,
    gap: 12,
  },
  suggestionIcon: {
    width: 64,
    height: 64,
    borderRadius: radii.card,
    borderWidth: 1,
    borderColor: colors.line,
    backgroundColor: colors.ice,
    alignItems: "center",
    justifyContent: "center",
  },
  suggestionIconText: {
    fontSize: 20,
    lineHeight: 24,
    fontWeight: "700",
    color: colors.blue,
  },
  suggestionTitle: {
    fontSize: 24,
    lineHeight: 28,
    fontWeight: "700",
    color: colors.black,
  },
  suggestionDetail: {
    fontSize: 14,
    lineHeight: 24,
    color: colors.muted,
  },
  suggestionHint: {
    fontSize: 14,
    lineHeight: 24,
    color: colors.muted,
  },
  suggestionLink: {
    marginTop: 4,
    minHeight: 44,
    alignSelf: "flex-start",
    borderRadius: radii.pill,
    borderWidth: 1,
    borderColor: colors.black,
    paddingHorizontal: 18,
    justifyContent: "center",
  },
  suggestionLinkText: {
    fontSize: 14,
    lineHeight: 18,
    fontWeight: "700",
    color: colors.black,
  },
});
