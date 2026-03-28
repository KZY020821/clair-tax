import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRouter } from "expo-router";
import { useState } from "react";
import { StyleSheet, Text, View } from "react-native";
import { createUserYear, fetchUserYears } from "@/lib/user-years";
import { fetchPolicyYears } from "@/lib/policy-years";
import {
  Button,
  ChoiceChip,
  EmptyState,
  ErrorBanner,
  Hero,
  Panel,
  Screen,
  SectionTitle,
} from "@/components/ui";
import { colors, radii } from "@/theme/tokens";

export function CreateYearWorkspaceScreen() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const policyYearsQuery = useQuery({
    queryKey: ["policy-years"],
    queryFn: fetchPolicyYears,
  });
  const userYearsQuery = useQuery({
    queryKey: ["user-years"],
    queryFn: fetchUserYears,
  });
  const existingYears = userYearsQuery.data?.map((userYear) => userYear.year) ?? [];
  const availablePolicyYears =
    policyYearsQuery.data?.filter(
      (policyYear) => !existingYears.includes(policyYear.year),
    ) ?? [];
  const [selectedYear, setSelectedYear] = useState<number | null>(null);
  const activeSelectedYear =
    selectedYear !== null &&
    availablePolicyYears.some((policyYear) => policyYear.year === selectedYear)
      ? selectedYear
      : availablePolicyYears[0]?.year ?? null;

  const createYearMutation = useMutation({
    mutationFn: createUserYear,
    onSuccess: async (createdYear) => {
      await queryClient.invalidateQueries({ queryKey: ["user-years"] });
      router.push(`/year/${createdYear.year}`);
    },
  });

  return (
    <Screen>
      <Hero
        eyebrow="Years"
        title="Open a filing year"
        detail="Create the same year workspaces the web app exposes in the sidebar so receipts, relief summaries, and year-specific review all stay aligned."
      />

      <View style={styles.grid}>
        <Panel>
          <SectionTitle
            eyebrow="Temporary Account"
            title="Open a year workspace before adding receipts"
            detail="This route uses the temporary developer account already shown in the shell. Pick a policy year from backend data, create the workspace once, and Clair Tax will keep that year available in the menu."
          />
        </Panel>

        <Panel tone="muted">
          <SectionTitle eyebrow="Current State" title={userYearsQuery.isLoading ? "..." : String(existingYears.length)} />
          <Text style={styles.panelCopy}>
            {existingYears.length > 0
              ? "These are the year workspaces already created for the current dev user."
              : "No year workspaces exist yet for the current dev user."}
          </Text>
        </Panel>
      </View>

      <Panel>
        <SectionTitle
          eyebrow="Create Workspace"
          title="Choose a filing year"
          detail="Years come from `/api/policy-years`. Already-created workspaces drop out of the selector so this stays focused on the next year you need to open."
        />

        {policyYearsQuery.isLoading || userYearsQuery.isLoading ? (
          <EmptyState>Loading policy years and existing workspaces...</EmptyState>
        ) : policyYearsQuery.error instanceof Error ? (
          <ErrorBanner message={policyYearsQuery.error.message} />
        ) : availablePolicyYears.length === 0 ? (
          <View style={styles.sectionStack}>
            <EmptyState>
              <Text style={styles.emptyTitle}>
                All visible policy years already have a workspace.
              </Text>
              <Text style={styles.panelCopy}>
                Jump straight into an existing year or wait for another policy year to be added on the backend.
              </Text>
            </EmptyState>
            {existingYears.length > 0 ? (
              <View style={styles.chipWrap}>
                {existingYears.map((year) => (
                  <ChoiceChip
                    key={year}
                    label={`Open ${year}`}
                    onPress={() => router.push(`/year/${year}`)}
                  />
                ))}
              </View>
            ) : null}
          </View>
        ) : (
          <View style={styles.sectionStack}>
            <View style={styles.chipWrap}>
              {availablePolicyYears.map((policyYear) => (
                <ChoiceChip
                  key={policyYear.id}
                  label={`${policyYear.year} · ${policyYear.status}`}
                  active={policyYear.year === activeSelectedYear}
                  onPress={() => {
                    setSelectedYear(policyYear.year);
                    createYearMutation.reset();
                  }}
                />
              ))}
            </View>

            {availablePolicyYears.find((policyYear) => policyYear.year === activeSelectedYear) ? (
              <View style={styles.previewCard}>
                <Text style={styles.previewTitle}>Year {activeSelectedYear}</Text>
                <Text style={styles.panelCopy}>
                  Open this workspace to review available relief categories and attach receipts for the year.
                </Text>
              </View>
            ) : null}

            {createYearMutation.error instanceof Error ? (
              <ErrorBanner message={createYearMutation.error.message} />
            ) : null}

            <View style={styles.buttonStack}>
              <Button
                label={createYearMutation.isPending ? "Opening workspace..." : "Create year"}
                onPress={() => {
                  if (activeSelectedYear !== null) {
                    createYearMutation.mutate(activeSelectedYear);
                  }
                }}
                disabled={createYearMutation.isPending || activeSelectedYear === null}
              />
              <Button
                label="Back to dashboard"
                variant="secondary"
                onPress={() => router.push("/")}
              />
            </View>
          </View>
        )}
      </Panel>

      <Panel>
        <SectionTitle
          eyebrow="Created Years"
          title="Sidebar-ready workspaces"
          detail="Only years the current dev user has created will appear in the year rail and the year routes."
        />
        {existingYears.length > 0 ? (
          <View style={styles.sectionStack}>
            {existingYears.map((year) => (
              <View key={year} style={styles.listCard}>
                <Text style={styles.listTitle}>Year {year}</Text>
                <Text style={styles.panelCopy}>
                  Open the workspace, review summary totals, and manage receipts.
                </Text>
                <Button label={`Open ${year}`} variant="secondary" onPress={() => router.push(`/year/${year}`)} />
              </View>
            ))}
          </View>
        ) : (
          <EmptyState>
            No workspaces yet. Create one from the form to seed the menu and unlock the year summary page.
          </EmptyState>
        )}
      </Panel>
    </Screen>
  );
}

const styles = StyleSheet.create({
  grid: {
    gap: 14,
  },
  panelCopy: {
    fontSize: 14,
    lineHeight: 24,
    color: colors.muted,
  },
  sectionStack: {
    gap: 14,
  },
  chipWrap: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 10,
  },
  previewCard: {
    borderRadius: radii.card,
    borderWidth: 1,
    borderColor: colors.line,
    backgroundColor: colors.ice,
    padding: 18,
    gap: 8,
  },
  previewTitle: {
    fontSize: 16,
    lineHeight: 22,
    fontWeight: "700",
    color: colors.black,
  },
  buttonStack: {
    gap: 10,
  },
  listCard: {
    borderRadius: radii.card,
    borderWidth: 1,
    borderColor: colors.line,
    backgroundColor: colors.white,
    padding: 18,
    gap: 10,
  },
  listTitle: {
    fontSize: 16,
    lineHeight: 22,
    fontWeight: "700",
    color: colors.black,
  },
  emptyTitle: {
    fontSize: 15,
    lineHeight: 20,
    fontWeight: "700",
    color: colors.black,
    marginBottom: 8,
  },
});
