import { PropsWithChildren, ReactNode } from "react";
import {
  ActivityIndicator,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TextInputProps,
  View,
  ViewStyle,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { colors, radii, shadows, spacing } from "@/theme/tokens";

type ScreenProps = PropsWithChildren<{
  scroll?: boolean;
  contentContainerStyle?: ViewStyle;
}>;

type PanelProps = PropsWithChildren<{
  tone?: "default" | "muted" | "strong";
  style?: ViewStyle;
}>;

type ButtonProps = Readonly<{
  label: string;
  onPress?: () => void;
  variant?: "primary" | "secondary";
  disabled?: boolean;
}>;

type InputFieldProps = Readonly<{
  label: string;
  help?: string | null;
  error?: string | null;
}> &
  TextInputProps;

type ChoiceChipProps = Readonly<{
  label: string;
  active?: boolean;
  onPress?: () => void;
}>;

export function Screen({ children, scroll = true, contentContainerStyle }: ScreenProps) {
  if (!scroll) {
    return (
      <SafeAreaView style={styles.screen} edges={["bottom"]}>
        {children}
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.screen} edges={["bottom"]}>
      <ScrollView
        style={styles.scroll}
        contentContainerStyle={[styles.scrollContent, contentContainerStyle]}
        showsVerticalScrollIndicator={false}
      >
        {children}
      </ScrollView>
    </SafeAreaView>
  );
}

export function Hero({
  eyebrow,
  title,
  detail,
}: Readonly<{
  eyebrow: string;
  title: string;
  detail: string;
}>) {
  return (
    <View style={styles.hero}>
      <Eyebrow>{eyebrow}</Eyebrow>
      <Text style={styles.heroTitle}>{title}</Text>
      <Text style={styles.heroDetail}>{detail}</Text>
    </View>
  );
}

export function Panel({ children, tone = "default", style }: PanelProps) {
  return (
    <View
      style={[
        styles.panel,
        tone === "muted" ? styles.panelMuted : null,
        tone === "strong" ? styles.panelStrong : null,
        style,
      ]}
    >
      {children}
    </View>
  );
}

export function MetricCard({
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
    <View style={[styles.metricCard, accent ? styles.metricCardAccent : null]}>
      <Text style={styles.metricLabel}>{label}</Text>
      <Text style={styles.metricValue}>{value}</Text>
      <Text style={styles.metricDetail}>{detail}</Text>
    </View>
  );
}

export function Eyebrow({ children }: PropsWithChildren) {
  return <Text style={styles.eyebrow}>{children}</Text>;
}

export function Pill({
  children,
  tone = "default",
}: PropsWithChildren<{ tone?: "default" | "blue" }>) {
  return (
    <View style={[styles.pill, tone === "blue" ? styles.pillBlue : null]}>
      <Text style={[styles.pillText, tone === "blue" ? styles.pillBlueText : null]}>
        {children}
      </Text>
    </View>
  );
}

export function Button({
  label,
  onPress,
  variant = "primary",
  disabled = false,
}: ButtonProps) {
  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      style={({ pressed }) => [
        styles.button,
        variant === "secondary" ? styles.buttonSecondary : styles.buttonPrimary,
        disabled ? styles.buttonDisabled : null,
        pressed && !disabled ? styles.buttonPressed : null,
      ]}
    >
      <Text
        style={[
          styles.buttonText,
          variant === "secondary" ? styles.buttonSecondaryText : styles.buttonPrimaryText,
          disabled ? styles.buttonDisabledText : null,
        ]}
      >
        {label}
      </Text>
    </Pressable>
  );
}

export function InputField({
  label,
  help,
  error,
  style,
  ...props
}: InputFieldProps) {
  return (
    <View style={styles.fieldBlock}>
      <Text style={styles.fieldLabel}>{label}</Text>
      <TextInput
        {...props}
        placeholderTextColor="rgba(79, 88, 102, 0.7)"
        style={[styles.input, error ? styles.inputError : null, style]}
      />
      {error ? <Text style={styles.errorText}>{error}</Text> : help ? <Text style={styles.helpText}>{help}</Text> : null}
    </View>
  );
}

export function ChoiceChip({ label, active = false, onPress }: ChoiceChipProps) {
  return (
    <Pressable
      onPress={onPress}
      style={({ pressed }) => [
        styles.choiceChip,
        active ? styles.choiceChipActive : null,
        pressed ? styles.choiceChipPressed : null,
      ]}
    >
      <Text style={[styles.choiceChipText, active ? styles.choiceChipActiveText : null]}>
        {label}
      </Text>
    </Pressable>
  );
}

export function EmptyState({ children }: PropsWithChildren) {
  return <View style={styles.emptyState}>{typeof children === "string" ? <Text style={styles.emptyStateText}>{children}</Text> : children}</View>;
}

export function ErrorBanner({ message }: Readonly<{ message: string }>) {
  return (
    <View style={styles.errorBanner}>
      <Text style={styles.errorBannerText}>{message}</Text>
    </View>
  );
}

export function LoadingBlock({ message }: Readonly<{ message: string }>) {
  return (
    <View style={styles.loadingBlock}>
      <ActivityIndicator color={colors.blue} />
      <Text style={styles.loadingText}>{message}</Text>
    </View>
  );
}

export function SectionTitle({
  eyebrow,
  title,
  detail,
  right,
}: Readonly<{
  eyebrow: string;
  title: string;
  detail?: string;
  right?: ReactNode;
}>) {
  return (
    <View style={styles.sectionTitleRow}>
      <View style={styles.sectionTitleCopy}>
        <Eyebrow>{eyebrow}</Eyebrow>
        <Text style={styles.sectionTitle}>{title}</Text>
        {detail ? <Text style={styles.sectionDetail}>{detail}</Text> : null}
      </View>
      {right ? <View>{right}</View> : null}
    </View>
  );
}

export const uiStyles = StyleSheet.create({
  row: {
    flexDirection: "row",
    alignItems: "center",
  },
  wrapRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
  },
  sectionGap: {
    gap: spacing.sectionGap,
  },
});

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: colors.white,
  },
  scroll: {
    flex: 1,
  },
  scrollContent: {
    paddingHorizontal: spacing.shellHorizontal,
    paddingBottom: 32,
    gap: spacing.sectionGap,
  },
  hero: {
    alignItems: "center",
    paddingTop: 8,
    gap: 8,
  },
  heroTitle: {
    fontSize: 34,
    lineHeight: 40,
    fontWeight: "700",
    color: colors.black,
    textAlign: "center",
    letterSpacing: -1.1,
  },
  heroDetail: {
    maxWidth: 720,
    fontSize: 16,
    lineHeight: 28,
    color: colors.muted,
    textAlign: "center",
  },
  eyebrow: {
    fontSize: 11,
    lineHeight: 16,
    fontWeight: "700",
    letterSpacing: 2.8,
    color: colors.blue,
    textTransform: "uppercase",
  },
  panel: {
    borderRadius: radii.panel,
    borderWidth: 1,
    borderColor: colors.line,
    backgroundColor: colors.white,
    padding: spacing.panelPadding,
    gap: 12,
    ...shadows.panel,
  },
  panelMuted: {
    backgroundColor: colors.ice,
  },
  panelStrong: {
    backgroundColor: colors.black,
    borderColor: colors.black,
  },
  metricCard: {
    borderRadius: radii.card,
    borderWidth: 1,
    borderColor: colors.line,
    backgroundColor: colors.white,
    padding: 20,
    gap: 10,
    ...shadows.panel,
  },
  metricCardAccent: {
    borderColor: colors.blue,
    backgroundColor: colors.ice,
    ...shadows.accent,
  },
  metricLabel: {
    fontSize: 13,
    lineHeight: 18,
    fontWeight: "500",
    color: colors.black,
  },
  metricValue: {
    fontSize: 32,
    lineHeight: 38,
    fontWeight: "700",
    color: colors.black,
  },
  metricDetail: {
    fontSize: 14,
    lineHeight: 24,
    color: colors.muted,
  },
  pill: {
    borderRadius: radii.pill,
    borderWidth: 1,
    borderColor: colors.lineStrong,
    backgroundColor: colors.white,
    paddingHorizontal: 14,
    paddingVertical: 8,
    alignSelf: "flex-start",
  },
  pillBlue: {
    borderColor: colors.blue,
    backgroundColor: colors.blue,
  },
  pillText: {
    fontSize: 11,
    lineHeight: 14,
    fontWeight: "700",
    letterSpacing: 1.7,
    textTransform: "uppercase",
    color: colors.black,
  },
  pillBlueText: {
    color: colors.white,
  },
  button: {
    minHeight: 48,
    borderRadius: radii.pill,
    alignItems: "center",
    justifyContent: "center",
    paddingHorizontal: 20,
    borderWidth: 1,
  },
  buttonPrimary: {
    borderColor: colors.black,
    backgroundColor: colors.black,
  },
  buttonSecondary: {
    borderColor: colors.black,
    backgroundColor: colors.white,
  },
  buttonPressed: {
    opacity: 0.86,
  },
  buttonDisabled: {
    borderColor: colors.lineStrong,
    backgroundColor: colors.line,
  },
  buttonText: {
    fontSize: 14,
    lineHeight: 18,
    fontWeight: "700",
  },
  buttonPrimaryText: {
    color: colors.white,
  },
  buttonSecondaryText: {
    color: colors.black,
  },
  buttonDisabledText: {
    color: colors.muted,
  },
  fieldBlock: {
    gap: 8,
  },
  fieldLabel: {
    fontSize: 14,
    lineHeight: 18,
    fontWeight: "700",
    color: colors.black,
  },
  input: {
    borderRadius: radii.field,
    borderWidth: 1,
    borderColor: colors.lineStrong,
    backgroundColor: colors.white,
    paddingHorizontal: 16,
    paddingVertical: 14,
    fontSize: 16,
    lineHeight: 22,
    color: colors.black,
  },
  inputError: {
    borderColor: colors.blue,
  },
  helpText: {
    fontSize: 12,
    lineHeight: 20,
    color: colors.muted,
  },
  errorText: {
    fontSize: 12,
    lineHeight: 20,
    color: colors.blue,
  },
  choiceChip: {
    borderRadius: radii.card,
    borderWidth: 1,
    borderColor: colors.line,
    backgroundColor: colors.white,
    paddingHorizontal: 14,
    paddingVertical: 10,
  },
  choiceChipActive: {
    borderColor: colors.lineStrong,
    backgroundColor: "rgba(94, 155, 255, 0.2)",
  },
  choiceChipPressed: {
    opacity: 0.85,
  },
  choiceChipText: {
    fontSize: 14,
    lineHeight: 18,
    fontWeight: "600",
    color: colors.black,
  },
  choiceChipActiveText: {
    color: colors.black,
  },
  emptyState: {
    borderRadius: radii.card,
    borderWidth: 1,
    borderStyle: "dashed",
    borderColor: colors.line,
    padding: 18,
  },
  emptyStateText: {
    fontSize: 14,
    lineHeight: 24,
    color: colors.muted,
  },
  errorBanner: {
    borderRadius: radii.card,
    borderWidth: 1,
    borderColor: colors.dangerBorder,
    backgroundColor: colors.dangerBg,
    paddingHorizontal: 16,
    paddingVertical: 14,
  },
  errorBannerText: {
    fontSize: 14,
    lineHeight: 22,
    color: colors.dangerText,
  },
  loadingBlock: {
    borderRadius: radii.card,
    borderWidth: 1,
    borderColor: colors.line,
    backgroundColor: colors.ice,
    padding: 18,
    gap: 10,
  },
  loadingText: {
    fontSize: 14,
    lineHeight: 22,
    color: colors.muted,
  },
  sectionTitleRow: {
    flexDirection: "row",
    gap: 12,
    justifyContent: "space-between",
    alignItems: "flex-start",
  },
  sectionTitleCopy: {
    flex: 1,
    gap: 8,
  },
  sectionTitle: {
    fontSize: 30,
    lineHeight: 36,
    fontWeight: "700",
    color: colors.black,
    letterSpacing: -0.8,
  },
  sectionDetail: {
    fontSize: 14,
    lineHeight: 24,
    color: colors.muted,
  },
});
