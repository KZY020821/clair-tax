import { useMutation, useQuery } from "@tanstack/react-query";
import { useRouter } from "expo-router";
import { Pressable, StyleSheet, Text, View } from "react-native";
import { useState } from "react";
import {
  buildProfileFactList,
  isCategoryVisibleForProfile,
  isProfileDrivenFixedCategoryActive,
} from "@/lib/profile-relief-visibility";
import { formatCurrency } from "@/lib/format-currency";
import { fetchPolicyYears } from "@/lib/policy-years";
import { fetchProfile } from "@/lib/profile";
import {
  CalculatorRequest,
  ReliefCategory,
  calculateTax,
  fetchPolicyYear,
} from "@/lib/tax-calculator";
import {
  Button,
  ChoiceChip,
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

const DEFAULT_POLICY_YEAR = 2025;

const sectionOrder = [
  "identity",
  "family",
  "medical",
  "education",
  "lifestyle",
  "retirement",
  "insurance",
  "property",
] as const;

const sectionContent: Record<string, { title: string; detail: string }> = {
  identity: {
    title: "Identity and status",
    detail: "Resident individual reliefs that are fixed or depend on personal status.",
  },
  family: {
    title: "Family reliefs",
    detail: "Parents, spouse, children, childcare, and related family deductions.",
  },
  medical: {
    title: "Medical and support",
    detail: "Medical treatment and support items with the selected year's caps, including any shared medical ceilings.",
  },
  education: {
    title: "Education and savings",
    detail: "Self-education, skill-upgrading, and SSPN-related deductions.",
  },
  lifestyle: {
    title: "Lifestyle",
    detail: "Lifestyle, sports, travel, and similar personal expenditure reliefs for the selected year.",
  },
  retirement: {
    title: "Retirement and statutory contributions",
    detail: "EPF, PRS, deferred annuity, and SOCSO-linked deductions.",
  },
  insurance: {
    title: "Insurance",
    detail: "Life, family takaful, education, and medical insurance deductions.",
  },
  property: {
    title: "Property and green reliefs",
    detail: "First-home loan interest plus eligible green and household sustainability reliefs.",
  },
};

function parseAmount(value: string): number | null {
  if (value.trim() === "") {
    return null;
  }

  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function parseCount(value: string): number | null {
  if (value.trim() === "") {
    return null;
  }

  const parsed = Number(value);
  return Number.isInteger(parsed) ? parsed : null;
}

function getMoneyFieldError(value: string, maxAmount: number): string | null {
  if (value.trim() === "") {
    return null;
  }

  const parsedValue = parseAmount(value);
  if (parsedValue === null) {
    return "Enter a valid amount.";
  }

  if (parsedValue < 0) {
    return "Amount cannot be negative.";
  }

  if (parsedValue > maxAmount) {
    return `Amount cannot exceed ${formatCurrency(maxAmount)}.`;
  }

  return null;
}

function getCountFieldError(value: string): string | null {
  if (value.trim() === "") {
    return null;
  }

  const parsedValue = parseCount(value);
  if (parsedValue === null) {
    return "Enter a whole number.";
  }

  if (parsedValue < 0) {
    return "Quantity cannot be negative.";
  }

  return null;
}

function getGrossIncomeError(value: string): string | null {
  if (value.trim() === "") {
    return "Enter your gross income before calculating tax.";
  }

  const parsedValue = parseAmount(value);
  if (parsedValue === null) {
    return "Enter a valid gross income amount.";
  }

  if (parsedValue < 0) {
    return "Gross income cannot be negative.";
  }

  return null;
}

function getZakatError(value: string): string | null {
  if (value.trim() === "") {
    return null;
  }

  const parsedValue = parseAmount(value);
  if (parsedValue === null) {
    return "Enter a valid zakat amount.";
  }

  if (parsedValue < 0) {
    return "Zakat cannot be negative.";
  }

  return null;
}

function CategoryField({
  category,
  amountValue,
  countValue,
  selected,
  profileApplied,
  validationMessage,
  onAmountChange,
  onCountChange,
  onSelectedChange,
}: Readonly<{
  category: ReliefCategory;
  amountValue: string;
  countValue: string;
  selected: boolean;
  profileApplied: boolean;
  validationMessage: string | null;
  onAmountChange: (value: string) => void;
  onCountChange: (value: string) => void;
  onSelectedChange: (value: boolean) => void;
}>) {
  const isFixed = category.inputType === "fixed";
  const isCount = category.inputType === "count";

  return (
    <View style={styles.categoryCard}>
      <View style={styles.categoryHeader}>
        <View style={styles.categoryMetaWrap}>
          <Pill tone="blue">Max {formatCurrency(category.maxAmount)}</Pill>
          <Pill>{category.requiresReceipt ? "Receipt required" : "Receipt not required"}</Pill>
          {profileApplied ? <Pill tone="blue">Applied from profile</Pill> : null}
        </View>
        <Text style={styles.categoryTitle}>{category.name}</Text>
        <Text style={styles.categoryDetail}>{category.description}</Text>
      </View>

      {isFixed ? (
        <ChoiceChip
          label={selected || profileApplied ? "Included" : "Tap to include"}
          active={selected || profileApplied}
          onPress={profileApplied ? undefined : () => onSelectedChange(!selected)}
        />
      ) : (
        <InputField
          label={isCount ? "Quantity" : "Claimed amount"}
          value={isCount ? countValue : amountValue}
          onChangeText={isCount ? onCountChange : onAmountChange}
          keyboardType="decimal-pad"
          help={
            validationMessage ??
            (isCount
              ? "Enter the quantity you want the backend to evaluate."
              : "Enter the amount you want the backend to evaluate for this relief category.")
          }
          error={validationMessage}
          placeholder="0"
        />
      )}
    </View>
  );
}

function CalculationResult({
  result,
  isPending,
  errorMessage,
}: Readonly<{
  result: Awaited<ReturnType<typeof calculateTax>> | undefined;
  isPending: boolean;
  errorMessage: string | null;
}>) {
  if (errorMessage) {
    return (
      <Panel>
        <SectionTitle eyebrow="Calculation Result" title="Calculation error" />
        <ErrorBanner message={errorMessage} />
      </Panel>
    );
  }

  if (!result && isPending) {
    return (
      <Panel>
        <SectionTitle
          eyebrow="Calculation Result"
          title="Preparing your report"
          detail="Calculating the tax summary and building a compact report from your income, reliefs, rebate, and zakat inputs."
        />
      </Panel>
    );
  }

  if (!result) {
    return (
      <Panel>
        <SectionTitle
          eyebrow="Calculation Result"
          title="Summary preview"
          detail="Submit the calculator to see a compact tax report with the main figures up front and the detailed tax bracket breakdown underneath."
        />
      </Panel>
    );
  }

  const taxableIncome = result.taxableIncome ?? result.chargeableIncome;

  return (
    <Panel>
      <SectionTitle
        eyebrow="Calculation Result"
        title={`Income tax report YA ${result.policyYear}`}
        detail="Review the key totals first, then inspect the tax bracket breakdown if you need the detailed computation."
        right={<Pill tone={isPending ? "blue" : "default"}>{isPending ? "Updating" : "Ready"}</Pill>}
      />

      <View style={styles.resultStack}>
        <MetricCard
          label="Final payable"
          value={formatCurrency(result.taxYouShouldPay)}
          detail="This is the remaining tax after reliefs, rebate, and zakat are applied."
          accent
        />
        <MetricCard
          label="Gross Income"
          value={formatCurrency(result.grossIncome)}
          detail="Income before deductions."
        />
        <MetricCard
          label="Total Relief"
          value={formatCurrency(result.totalRelief)}
          detail="Combined reliefs selected and accepted by the backend."
        />
        <MetricCard
          label="Taxable Income"
          value={formatCurrency(taxableIncome)}
          detail="Chargeable income after relief."
        />
      </View>

      <View style={styles.reportCard}>
        <Text style={styles.reportTitle}>Tax summary</Text>
        <View style={styles.reportRows}>
          <View style={styles.reportRow}>
            <Text style={styles.reportLabel}>Gross income before deduction</Text>
            <Text style={styles.reportValue}>{formatCurrency(result.grossIncome)}</Text>
          </View>
          <View style={styles.reportRow}>
            <Text style={styles.reportLabel}>Less total relief</Text>
            <Text style={styles.reportValue}>- {formatCurrency(result.totalRelief)}</Text>
          </View>
          <View style={styles.reportRow}>
            <Text style={styles.reportLabel}>Taxable income</Text>
            <Text style={styles.reportValue}>{formatCurrency(taxableIncome)}</Text>
          </View>
          <View style={styles.reportRow}>
            <Text style={styles.reportLabel}>Tax amount</Text>
            <Text style={styles.reportValue}>{formatCurrency(result.taxAmount)}</Text>
          </View>
          <View style={styles.reportRow}>
            <Text style={styles.reportLabel}>Less tax rebate</Text>
            <Text style={styles.reportValue}>- {formatCurrency(result.taxRebate)}</Text>
          </View>
          <View style={styles.reportRow}>
            <Text style={styles.reportLabel}>Less zakat</Text>
            <Text style={styles.reportValue}>- {formatCurrency(result.zakat)}</Text>
          </View>
          <View style={[styles.reportRow, styles.reportRowFinal]}>
            <Text style={styles.reportLabelFinal}>Tax you should pay</Text>
            <Text style={styles.reportValue}>{formatCurrency(result.taxYouShouldPay)}</Text>
          </View>
        </View>
      </View>
    </Panel>
  );
}

export function TaxCalculatorScreen() {
  const router = useRouter();
  const [selectedYear, setSelectedYear] = useState(DEFAULT_POLICY_YEAR);
  const [grossIncome, setGrossIncome] = useState("");
  const [zakat, setZakat] = useState("");
  const [expandedSections, setExpandedSections] = useState<Record<string, boolean>>({
    identity: true,
    family: true,
  });
  const [amountValues, setAmountValues] = useState<Record<string, string>>({});
  const [countValues, setCountValues] = useState<Record<string, string>>({});
  const [selectedValues, setSelectedValues] = useState<Record<string, boolean>>({});
  const [formError, setFormError] = useState<string | null>(null);

  const policyYearsQuery = useQuery({
    queryKey: ["policy-years"],
    queryFn: fetchPolicyYears,
  });
  const policyQuery = useQuery({
    queryKey: ["policy", selectedYear],
    queryFn: () => fetchPolicyYear(selectedYear),
  });
  const profileQuery = useQuery({
    queryKey: ["profile"],
    queryFn: fetchProfile,
  });
  const calculationMutation = useMutation({
    mutationFn: calculateTax,
  });

  const yearOptions = Array.from(
    new Set([
      DEFAULT_POLICY_YEAR,
      ...(policyYearsQuery.data?.map((policyYear) => policyYear.year) ?? []),
    ]),
  ).sort((left, right) => left - right);

  const categories = policyQuery.data?.reliefCategories ?? [];
  const savedProfile = profileQuery.data;
  const visibleProfileCategories = savedProfile
    ? categories.filter((category) => isCategoryVisibleForProfile(category, savedProfile))
    : [];
  const profileDrivenCodes = new Set(
    savedProfile
      ? visibleProfileCategories
          .filter((category) => isProfileDrivenFixedCategoryActive(category, savedProfile))
          .map((category) => category.code)
      : [],
  );
  const activeCodes = new Set<string>();

  for (const category of visibleProfileCategories) {
    if (category.autoApply || profileDrivenCodes.has(category.code)) {
      activeCodes.add(category.code);
      continue;
    }

    if (category.inputType === "fixed" && selectedValues[category.id]) {
      activeCodes.add(category.code);
      continue;
    }

    if (
      category.inputType === "amount" &&
      (parseAmount(amountValues[category.id] ?? "") ?? 0) > 0
    ) {
      activeCodes.add(category.code);
      continue;
    }

    if (
      category.inputType === "count" &&
      (parseCount(countValues[category.id] ?? "") ?? 0) > 0
    ) {
      activeCodes.add(category.code);
    }
  }

  const visibleCategories = visibleProfileCategories.filter(
    (category) =>
      category.requiresCategoryCode === null ||
      activeCodes.has(category.requiresCategoryCode),
  );

  const categoryValidationMessages: Record<string, string | null> = {};
  for (const category of visibleCategories) {
    if (category.inputType === "amount") {
      categoryValidationMessages[category.id] = getMoneyFieldError(
        amountValues[category.id] ?? "",
        category.maxAmount,
      );
      continue;
    }

    if (category.inputType === "count") {
      categoryValidationMessages[category.id] = getCountFieldError(
        countValues[category.id] ?? "",
      );
      continue;
    }

    categoryValidationMessages[category.id] = null;
  }

  const sectionedCategories = sectionOrder
    .map((section) => ({
      section,
      categories: visibleCategories.filter((category) => category.section === section),
    }))
    .filter((sectionGroup) => sectionGroup.categories.length > 0);

  function buildPayload(): CalculatorRequest | null {
    if (!savedProfile) {
      setFormError("Wait for the saved profile to load before calculating.");
      return null;
    }

    const grossIncomeError = getGrossIncomeError(grossIncome);
    if (grossIncomeError) {
      setFormError(grossIncomeError);
      return null;
    }

    const zakatError = getZakatError(zakat);
    if (zakatError) {
      setFormError(zakatError);
      return null;
    }

    const invalidCategory = visibleCategories.find(
      (category) => categoryValidationMessages[category.id],
    );
    if (invalidCategory) {
      setFormError("Fix the highlighted relief fields before calculating.");
      return null;
    }

    const selectedReliefs: CalculatorRequest["selectedReliefs"] = [];

    for (const category of visibleCategories) {
      if (
        category.autoApply ||
        (savedProfile && isProfileDrivenFixedCategoryActive(category, savedProfile))
      ) {
        continue;
      }

      if (
        category.requiresCategoryCode !== null &&
        !activeCodes.has(category.requiresCategoryCode)
      ) {
        continue;
      }

      if (category.inputType === "fixed") {
        if (selectedValues[category.id]) {
          selectedReliefs.push({
            reliefCategoryId: category.id,
            selected: true,
          });
        }
        continue;
      }

      if (category.inputType === "count") {
        const quantity = parseCount(countValues[category.id] ?? "");
        if (quantity && quantity > 0) {
          selectedReliefs.push({
            reliefCategoryId: category.id,
            quantity,
          });
        }
        continue;
      }

      const claimedAmount = parseAmount(amountValues[category.id] ?? "");
      if (claimedAmount && claimedAmount > 0) {
        selectedReliefs.push({
          reliefCategoryId: category.id,
          claimedAmount,
        });
      }
    }

    return {
      policyYear: selectedYear,
      grossIncome: parseAmount(grossIncome) ?? 0,
      zakat: parseAmount(zakat) ?? 0,
      selectedReliefs,
    };
  }

  return (
    <Screen>
      <Hero
        eyebrow="Tax Calculator"
        title="Estimate resident income tax"
        detail="The mobile calculator keeps the same backend-driven relief structure as the web workspace, including saved-profile visibility and the same year-by-year policy inputs."
      />

      <Panel>
        <SectionTitle eyebrow="Income Tax Calculator" title="Income tax calculator" />

        <View style={styles.summaryTiles}>
          <MetricCard label="Assessment year" value={String(selectedYear)} detail="Current policy year in view." />
          <MetricCard label="Reliefs shown" value={String(visibleCategories.length)} detail="Profile-aware categories currently visible." />
          <MetricCard label="Tax rebate" value="Auto up to RM 400" detail="Low-income rebate is handled by the backend." />
        </View>

        <View style={styles.formStack}>
          <View style={styles.yearChips}>
            {yearOptions.map((year) => (
              <ChoiceChip
                key={year}
                label={String(year)}
                active={selectedYear === year}
                onPress={() => {
                  setSelectedYear(year);
                  setAmountValues({});
                  setCountValues({});
                  setSelectedValues({});
                  setZakat("");
                  setFormError(null);
                  calculationMutation.reset();
                }}
              />
            ))}
          </View>

          <InputField
            label="Gross income before deduction"
            value={grossIncome}
            onChangeText={(value) => {
              setGrossIncome(value);
              setFormError(null);
            }}
            keyboardType="decimal-pad"
            placeholder="0.00"
            help="Enter your annual gross income before deductions and rebates."
            error={grossIncome ? getGrossIncomeError(grossIncome) : null}
          />

          <InputField
            label="Zakat or fitrah paid"
            value={zakat}
            onChangeText={(value) => {
              setZakat(value);
              setFormError(null);
            }}
            keyboardType="decimal-pad"
            placeholder="0.00"
            help="Zakat is deducted after the tax amount and rebate are calculated."
            error={zakat ? getZakatError(zakat) : null}
          />

          <Panel tone="muted">
            <SectionTitle
              eyebrow="Saved Profile"
              title="Family and disability settings come from your profile"
              detail="The calculator uses the saved profile to decide which spouse, child, and disability reliefs should appear and which fixed reliefs should be applied automatically."
              right={<Button label="Edit profile" variant="secondary" onPress={() => router.push("/profile")} />}
            />
            <View style={styles.factWrap}>
              {(savedProfile ? buildProfileFactList(savedProfile) : []).map((fact) => (
                <Pill key={fact}>{fact}</Pill>
              ))}
            </View>
          </Panel>

          {policyQuery.error instanceof Error ? (
            <ErrorBanner message={policyQuery.error.message} />
          ) : null}

          {profileQuery.error instanceof Error ? (
            <ErrorBanner message={profileQuery.error.message} />
          ) : null}

          {sectionedCategories.map((sectionGroup) => (
            <View key={sectionGroup.section} style={styles.accordion}>
              <Pressable
                onPress={() =>
                          setExpandedSections((current: Record<string, boolean>) => ({
                    ...current,
                    [sectionGroup.section]: !current[sectionGroup.section],
                  }))
                }
                style={styles.accordionHeader}
              >
                <View style={styles.accordionCopy}>
                  <Text style={styles.accordionEyebrow}>
                    {sectionGroup.section.replaceAll("_", " ")}
                  </Text>
                  <Text style={styles.accordionTitle}>
                    {sectionContent[sectionGroup.section]?.title ?? sectionGroup.section}
                  </Text>
                  <Text style={styles.accordionDetail}>
                    {sectionContent[sectionGroup.section]?.detail ?? ""}
                  </Text>
                </View>
                <Pill tone={sectionGroup.categories.length > 0 ? "blue" : "default"}>
                  {sectionGroup.categories.length} shown
                </Pill>
              </Pressable>
              {expandedSections[sectionGroup.section] ? (
                <View style={styles.accordionContent}>
                  {sectionGroup.categories.map((category) => (
                    <CategoryField
                      key={category.id}
                      category={category}
                      amountValue={amountValues[category.id] ?? ""}
                      countValue={countValues[category.id] ?? ""}
                      selected={selectedValues[category.id] ?? false}
                      profileApplied={
                        !!savedProfile &&
                        isProfileDrivenFixedCategoryActive(category, savedProfile)
                      }
                      validationMessage={categoryValidationMessages[category.id]}
                      onAmountChange={(value) => {
                        setAmountValues((current: Record<string, string>) => ({
                          ...current,
                          [category.id]: value,
                        }));
                        setFormError(null);
                      }}
                      onCountChange={(value) => {
                        setCountValues((current: Record<string, string>) => ({
                          ...current,
                          [category.id]: value,
                        }));
                        setFormError(null);
                      }}
                      onSelectedChange={(value) => {
                        setSelectedValues((current: Record<string, boolean>) => ({
                          ...current,
                          [category.id]: value,
                        }));
                        setFormError(null);
                      }}
                    />
                  ))}
                </View>
              ) : null}
            </View>
          ))}

          {formError ? <ErrorBanner message={formError} /> : null}

          <Button
            label={calculationMutation.isPending ? "Calculating..." : "Calculate tax"}
            onPress={() => {
              const payload = buildPayload();
              if (!payload) {
                return;
              }

              setFormError(null);
              calculationMutation.mutate(payload);
            }}
            disabled={
              calculationMutation.isPending ||
              policyQuery.isLoading ||
              profileQuery.isLoading ||
              !policyQuery.data ||
              !savedProfile ||
              !!policyQuery.error ||
              !!profileQuery.error
            }
          />
        </View>
      </Panel>

      <Panel tone="muted">
        <SectionTitle
          eyebrow="Notes"
          title="What this calculator covers"
          detail="The same scope as the web app, focused on gross income, reliefs, zakat, and final tax due."
        />
        <View style={styles.notesStack}>
          <Text style={styles.noteCard}>
            The calculator supports resident-individual assessment years 2018 through 2025 with year-specific relief categories, caps, and tax brackets.
          </Text>
          <Text style={styles.noteCard}>
            Self relief is applied automatically, saved-profile disability and spouse reliefs are resolved in the backend, and low-income rebates are calculated automatically.
          </Text>
          <Text style={styles.noteCard}>
            This flow focuses on gross income, reliefs, zakat, and final tax due. PCB and refund balancing are not included yet.
          </Text>
        </View>
      </Panel>

      <CalculationResult
        result={calculationMutation.data}
        isPending={calculationMutation.isPending}
        errorMessage={
          calculationMutation.error instanceof Error
            ? calculationMutation.error.message
            : null
        }
      />
    </Screen>
  );
}

const styles = StyleSheet.create({
  summaryTiles: {
    gap: 14,
  },
  formStack: {
    gap: 16,
  },
  yearChips: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 10,
  },
  factWrap: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
  },
  accordion: {
    borderRadius: radii.card,
    borderWidth: 1,
    borderColor: colors.line,
    backgroundColor: colors.white,
    overflow: "hidden",
  },
  accordionHeader: {
    padding: 18,
    gap: 12,
  },
  accordionCopy: {
    gap: 6,
  },
  accordionEyebrow: {
    fontSize: 11,
    lineHeight: 16,
    fontWeight: "700",
    letterSpacing: 2.4,
    textTransform: "uppercase",
    color: colors.blue,
  },
  accordionTitle: {
    fontSize: 24,
    lineHeight: 28,
    fontWeight: "700",
    color: colors.black,
  },
  accordionDetail: {
    fontSize: 14,
    lineHeight: 24,
    color: colors.muted,
  },
  accordionContent: {
    borderTopWidth: 1,
    borderTopColor: colors.line,
    padding: 16,
    gap: 14,
  },
  categoryCard: {
    borderRadius: radii.card,
    borderWidth: 1,
    borderColor: colors.line,
    backgroundColor: colors.ice,
    padding: 16,
    gap: 12,
  },
  categoryHeader: {
    gap: 8,
  },
  categoryMetaWrap: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
  },
  categoryTitle: {
    fontSize: 22,
    lineHeight: 26,
    fontWeight: "700",
    color: colors.black,
  },
  categoryDetail: {
    fontSize: 14,
    lineHeight: 24,
    color: colors.muted,
  },
  notesStack: {
    gap: 10,
  },
  noteCard: {
    borderRadius: radii.card,
    borderWidth: 1,
    borderColor: colors.line,
    backgroundColor: colors.white,
    padding: 16,
    fontSize: 14,
    lineHeight: 24,
    color: colors.black,
    overflow: "hidden",
  },
  resultStack: {
    gap: 12,
  },
  reportCard: {
    borderRadius: radii.card,
    borderWidth: 1,
    borderColor: colors.line,
    backgroundColor: colors.white,
    padding: 18,
    gap: 14,
  },
  reportTitle: {
    fontSize: 18,
    lineHeight: 24,
    fontWeight: "700",
    color: colors.black,
  },
  reportRows: {
    gap: 10,
  },
  reportRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    gap: 12,
    paddingTop: 10,
    borderTopWidth: 1,
    borderTopColor: colors.line,
  },
  reportRowFinal: {
    borderTopWidth: 2,
    borderTopColor: colors.black,
    paddingTop: 14,
  },
  reportLabel: {
    flex: 1,
    fontSize: 14,
    lineHeight: 20,
    color: colors.muted,
  },
  reportLabelFinal: {
    flex: 1,
    fontSize: 16,
    lineHeight: 22,
    fontWeight: "700",
    color: colors.black,
  },
  reportValue: {
    fontSize: 14,
    lineHeight: 20,
    fontWeight: "700",
    color: colors.black,
  },
});
