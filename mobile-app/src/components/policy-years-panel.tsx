import { useQuery } from "@tanstack/react-query";
import { PolicyYear, fetchPolicyYears } from "@/lib/policy-years";
import { Eyebrow, EmptyState, ErrorBanner, LoadingBlock, Panel, Pill, SectionTitle } from "@/components/ui";
import { StyleSheet, Text, View } from "react-native";
import { colors, radii } from "@/theme/tokens";

function formatStatusLabel(status: PolicyYear["status"]) {
  return status === "published" ? "Published" : "Draft";
}

export function PolicyYearsPanel() {
  const { data, error, isLoading, isFetching } = useQuery({
    queryKey: ["policy-years"],
    queryFn: fetchPolicyYears,
  });

  if (isLoading) {
    return (
      <Panel>
        <Eyebrow>Backend</Eyebrow>
        <LoadingBlock message="Loading seeded policy years from Spring Boot..." />
      </Panel>
    );
  }

  if (error instanceof Error) {
    return (
      <Panel tone="strong">
        <Eyebrow>Backend</Eyebrow>
        <Text style={styles.strongTitle}>Policy years unavailable</Text>
        <Text style={styles.strongCopy}>{error.message}</Text>
      </Panel>
    );
  }

  if (!data?.length) {
    return (
      <Panel>
        <SectionTitle eyebrow="Backend" title="Policy years" />
        <EmptyState>No policy years were returned from the backend yet.</EmptyState>
      </Panel>
    );
  }

  return (
    <Panel>
      <SectionTitle
        eyebrow="Backend"
        title="Policy years"
        detail="Confirm which filing years are live before you switch workflows or start working in the calculator."
        right={<Pill tone={isFetching ? "blue" : "default"}>{isFetching ? "Refreshing" : "Connected"}</Pill>}
      />
      <View style={styles.list}>
        {data.map((policyYear) => (
          <View key={policyYear.id} style={styles.card}>
            <View style={styles.row}>
              <View>
                <Text style={styles.cardLabel}>Policy year</Text>
                <Text style={styles.cardValue}>{policyYear.year}</Text>
              </View>
              <View style={styles.meta}>
                <Pill tone={policyYear.status === "published" ? "blue" : "default"}>
                  {formatStatusLabel(policyYear.status)}
                </Pill>
                <Pill>{new Date(policyYear.createdAt).toLocaleDateString("en-MY")}</Pill>
              </View>
            </View>
          </View>
        ))}
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
  list: {
    gap: 12,
  },
  card: {
    borderRadius: radii.card,
    borderWidth: 1,
    borderColor: colors.line,
    backgroundColor: colors.ice,
    paddingHorizontal: 18,
    paddingVertical: 16,
  },
  row: {
    gap: 12,
  },
  meta: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
  },
  cardLabel: {
    fontSize: 12,
    lineHeight: 16,
    fontWeight: "700",
    letterSpacing: 1.8,
    textTransform: "uppercase",
    color: colors.muted,
  },
  cardValue: {
    marginTop: 8,
    fontSize: 30,
    lineHeight: 34,
    fontWeight: "700",
    color: colors.black,
  },
});
