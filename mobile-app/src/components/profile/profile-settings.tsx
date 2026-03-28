import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRouter } from "expo-router";
import { useEffect, useState } from "react";
import { Alert, Pressable, StyleSheet, Text, View } from "react-native";
import {
  buildProfileFactList,
  describeMaritalStatus,
} from "@/lib/profile-relief-visibility";
import {
  MaritalStatus,
  UserProfile,
  deleteAccount,
  fetchProfile,
  updateProfile,
} from "@/lib/profile";
import {
  Button,
  ChoiceChip,
  EmptyState,
  ErrorBanner,
  Hero,
  Panel,
  Pill,
  Screen,
  SectionTitle,
} from "@/components/ui";
import { colors, radii } from "@/theme/tokens";

type ProfileFormState = {
  isDisabled: boolean;
  maritalStatus: MaritalStatus;
  spouseDisabled: boolean;
  spouseWorking: boolean;
  hasChildren: boolean;
};

function toFormState(profile: UserProfile): ProfileFormState {
  return {
    isDisabled: profile.isDisabled,
    maritalStatus: profile.maritalStatus,
    spouseDisabled: profile.spouseDisabled === true,
    spouseWorking: profile.spouseWorking === true,
    hasChildren: profile.hasChildren === true,
  };
}

function buildPayload(formState: ProfileFormState) {
  if (formState.maritalStatus === "married") {
    return {
      isDisabled: formState.isDisabled,
      maritalStatus: formState.maritalStatus,
      spouseDisabled: formState.spouseDisabled,
      spouseWorking: formState.spouseWorking,
      hasChildren: formState.hasChildren,
    };
  }

  if (formState.maritalStatus === "previously_married") {
    return {
      isDisabled: formState.isDisabled,
      maritalStatus: formState.maritalStatus,
      hasChildren: formState.hasChildren,
    };
  }

  return {
    isDisabled: formState.isDisabled,
    maritalStatus: formState.maritalStatus,
  };
}

function ToggleCard({
  label,
  detail,
  checked,
  onToggle,
}: Readonly<{
  label: string;
  detail: string;
  checked: boolean;
  onToggle: () => void;
}>) {
  return (
    <Pressable onPress={onToggle} style={[styles.toggleCard, checked ? styles.toggleCardActive : null]}>
      <View style={[styles.checkbox, checked ? styles.checkboxActive : null]} />
      <View style={styles.toggleCopy}>
        <Text style={styles.toggleLabel}>{label}</Text>
        <Text style={styles.toggleDetail}>{detail}</Text>
      </View>
    </Pressable>
  );
}

export function ProfileSettingsScreen() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const profileQuery = useQuery({
    queryKey: ["profile"],
    queryFn: fetchProfile,
  });
  const [draftFormState, setDraftFormState] = useState<ProfileFormState | null>(null);
  const updateMutation = useMutation({
    mutationFn: updateProfile,
    onSuccess: async (profile) => {
      setDraftFormState(toFormState(profile));
      queryClient.setQueryData(["profile"], profile);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["profile"] }),
        queryClient.invalidateQueries({ queryKey: ["user-year-workspace"] }),
      ]);
    },
  });
  const deleteMutation = useMutation({
    mutationFn: deleteAccount,
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["profile"] }),
        queryClient.invalidateQueries({ queryKey: ["dev-current-user"] }),
        queryClient.invalidateQueries({ queryKey: ["user-years"] }),
        queryClient.invalidateQueries({ queryKey: ["user-year-workspace"] }),
        queryClient.invalidateQueries({ queryKey: ["user-year-receipts"] }),
      ]);
    },
  });

  if (profileQuery.isLoading) {
    return (
      <Screen>
        <Hero
          eyebrow="Profile"
          title="Loading saved profile..."
          detail="Pulling the current dev account details and household settings."
        />
      </Screen>
    );
  }

  if (profileQuery.error instanceof Error) {
    return (
      <Screen>
        <Hero
          eyebrow="Profile"
          title="Profile is unavailable"
          detail={profileQuery.error.message}
        />
      </Screen>
    );
  }

  const profile = profileQuery.data;
  if (!profile) {
    return (
      <Screen>
        <EmptyState>Profile data is not available.</EmptyState>
      </Screen>
    );
  }

  useEffect(() => {
    setDraftFormState(toFormState(profile));
  }, [profile]);

  const formState = draftFormState ?? toFormState(profile);
  const profilePreview: UserProfile = {
    ...profile,
    ...buildPayload(formState),
    spouseDisabled:
      formState.maritalStatus === "married" ? formState.spouseDisabled : null,
    spouseWorking:
      formState.maritalStatus === "married" ? formState.spouseWorking : null,
    hasChildren:
      formState.maritalStatus === "single" ? null : formState.hasChildren,
  };
  const facts = buildProfileFactList(profilePreview);

  return (
    <Screen>
      <Hero
        eyebrow="Profile"
        title="Saved filing profile"
        detail="This dev-only profile drives family and disability relief visibility across the calculator and year workspace."
      />

      <Panel>
        <SectionTitle
          eyebrow="Profile"
          title="Saved filing profile"
          detail="This dev-only profile drives family and disability relief visibility across the calculator and year detail page."
          right={<Pill tone="blue">Dev account</Pill>}
        />

        <View style={styles.summaryCard}>
          <Text style={styles.summaryLabel}>Email</Text>
          <Text style={styles.summaryValue}>{profile.email}</Text>
          <Text style={styles.summaryCopy}>
            The app is still using the temporary dev-only current user flow.
          </Text>
        </View>

        <View style={styles.summaryCard}>
          <Text style={styles.summaryLabel}>Current summary</Text>
          <View style={styles.factWrap}>
            {facts.map((fact) => (
              <Pill key={fact}>{fact}</Pill>
            ))}
          </View>
        </View>
      </Panel>

      <Panel>
        <SectionTitle
          eyebrow="Profile Details"
          title="Household settings"
          detail="Save the current household status here once. The calculator and year detail page will reuse these values automatically."
          right={<Button label="Open calculator" variant="secondary" onPress={() => router.push("/calculator")} />}
        />

        <View style={styles.sectionStack}>
          <View style={styles.optionGroup}>
            <Text style={styles.groupTitle}>Marital status</Text>
            <View style={styles.factWrap}>
              {(["single", "married", "previously_married"] as const).map((status) => (
                <ChoiceChip
                  key={status}
                  label={describeMaritalStatus(status)}
                  active={formState.maritalStatus === status}
                    onPress={() => {
                    setDraftFormState({
                      ...formState,
                      maritalStatus: status,
                    });
                  }}
                />
              ))}
            </View>
          </View>

          <ToggleCard
            label="Taxpayer is disabled"
            detail="Expose the fixed disabled individual relief when this applies."
            checked={formState.isDisabled}
            onToggle={() => setDraftFormState({ ...formState, isDisabled: !formState.isDisabled })}
          />

          {formState.maritalStatus === "married" ? (
            <>
              <ToggleCard
                label="Spouse is working"
                detail="Spouse relief only remains visible when the spouse is not working."
                checked={formState.spouseWorking}
                onToggle={() =>
                  setDraftFormState({ ...formState, spouseWorking: !formState.spouseWorking })
                }
              />
              <ToggleCard
                label="Spouse is disabled"
                detail="Keep this on only when the spouse qualifies for disabled spouse relief."
                checked={formState.spouseDisabled}
                onToggle={() =>
                  setDraftFormState({ ...formState, spouseDisabled: !formState.spouseDisabled })
                }
              />
              <ToggleCard
                label="Has children"
                detail="Show child-related relief categories and workspace summaries."
                checked={formState.hasChildren}
                onToggle={() => setDraftFormState({ ...formState, hasChildren: !formState.hasChildren })}
              />
            </>
          ) : null}

          {formState.maritalStatus === "previously_married" ? (
            <ToggleCard
              label="Has children"
              detail="Show child-related relief categories and workspace summaries."
              checked={formState.hasChildren}
              onToggle={() => setDraftFormState({ ...formState, hasChildren: !formState.hasChildren })}
            />
          ) : null}

          {updateMutation.error instanceof Error ? (
            <ErrorBanner message={updateMutation.error.message} />
          ) : null}

          <Button
            label={updateMutation.isPending ? "Saving..." : "Save profile"}
            onPress={() => updateMutation.mutate(buildPayload(formState))}
            disabled={updateMutation.isPending}
          />
        </View>
      </Panel>

      <Panel>
        <SectionTitle
          eyebrow="Danger"
          title="Reset dev account data"
          detail="Use this when you want to clear the temporary profile and related dev-only workspace state."
        />
        {deleteMutation.error instanceof Error ? (
          <ErrorBanner message={deleteMutation.error.message} />
        ) : null}
        <Button
          label={deleteMutation.isPending ? "Resetting..." : "Delete all dev account data"}
          variant="secondary"
          onPress={() => {
            Alert.alert(
              "Delete all dev account data?",
              "This clears the temporary profile and resets the current dev account state.",
              [
                { text: "Cancel", style: "cancel" },
                {
                  text: "Delete",
                  style: "destructive",
                  onPress: () => deleteMutation.mutate(),
                },
              ],
            );
          }}
        />
      </Panel>
    </Screen>
  );
}

const styles = StyleSheet.create({
  sectionStack: {
    gap: 14,
  },
  summaryCard: {
    borderRadius: radii.card,
    borderWidth: 1,
    borderColor: colors.line,
    backgroundColor: colors.ice,
    padding: 18,
    gap: 10,
  },
  summaryLabel: {
    fontSize: 12,
    lineHeight: 16,
    fontWeight: "700",
    letterSpacing: 1.8,
    textTransform: "uppercase",
    color: colors.muted,
  },
  summaryValue: {
    fontSize: 26,
    lineHeight: 30,
    fontWeight: "700",
    color: colors.black,
  },
  summaryCopy: {
    fontSize: 14,
    lineHeight: 24,
    color: colors.muted,
  },
  factWrap: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
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
  toggleCard: {
    flexDirection: "row",
    alignItems: "flex-start",
    gap: 12,
    borderRadius: radii.card,
    borderWidth: 1,
    borderColor: colors.line,
    backgroundColor: colors.white,
    padding: 16,
  },
  toggleCardActive: {
    borderColor: colors.lineStrong,
    backgroundColor: "rgba(94, 155, 255, 0.08)",
  },
  checkbox: {
    width: 18,
    height: 18,
    marginTop: 2,
    borderRadius: 6,
    borderWidth: 1,
    borderColor: colors.lineStrong,
    backgroundColor: colors.white,
  },
  checkboxActive: {
    backgroundColor: colors.blue,
    borderColor: colors.blue,
  },
  toggleCopy: {
    flex: 1,
    gap: 4,
  },
  toggleLabel: {
    fontSize: 14,
    lineHeight: 18,
    fontWeight: "700",
    color: colors.black,
  },
  toggleDetail: {
    fontSize: 14,
    lineHeight: 22,
    color: colors.muted,
  },
});
