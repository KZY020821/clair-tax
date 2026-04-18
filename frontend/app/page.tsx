"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import type { ComponentType, SVGProps } from "react";
import AiDemoPanel from "./components/ai-demo-panel";
import PolicyYearsPanel from "./components/policy-years-panel";
import DynamicSuggestionCard from "./components/suggestion/dynamic-suggestion-card";
import { fetchAiDemoSummary } from "./lib/ai-demo-summary";
import { formatCurrency } from "./lib/format-currency";
import { fetchPolicyYears } from "./lib/policy-years";
import { fetchTaxSuggestions } from "./lib/tax-suggestions";

type IconProps = SVGProps<SVGSVGElement>;

type Suggestion = {
  title: string;
  detail: string;
  hint: string;
  icon: ComponentType<IconProps>;
};

const suggestionCards: Suggestion[] = [
  {
    title: "Child care relief",
    detail: "Keep child care receipts tidy so eligible family expenses are easier to review.",
    hint: "Family-related claims often depend on complete supporting documents.",
    icon: FamilyIcon,
  },
  {
    title: "Self-education relief",
    detail: "Group course fees and training receipts early before the filing period gets busy.",
    hint: "Use the calculator to compare potential claim amounts against the annual cap.",
    icon: EducationIcon,
  },
  {
    title: "Medical check-up relief",
    detail: "Store check-up receipts and treatment records in one place before submitting claims.",
    hint: "Medical claims benefit from a clearer review trail when every receipt is accounted for.",
    icon: MedicalIcon,
  },
];

function FamilyIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 64 64" fill="none" stroke="currentColor" {...props}>
      <circle cx="22" cy="20" r="6" strokeWidth="2.4" />
      <circle cx="42" cy="18" r="5" strokeWidth="2.4" />
      <circle cx="32" cy="31" r="4.5" strokeWidth="2.4" />
      <path
        d="M13 43c2.5-7.5 7-11 13-11s10.5 3.5 13 11M34 42c1.6-4.7 5-7 10-7 3.4 0 6.2 1.2 8 3.5"
        strokeLinecap="round"
        strokeWidth="2.4"
      />
      <path d="M18 50h28" strokeLinecap="round" strokeWidth="2.4" />
    </svg>
  );
}

function EducationIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 64 64" fill="none" stroke="currentColor" {...props}>
      <path
        d="M10 18h18c4.4 0 8 3.6 8 8v20H18c-4.4 0-8-3.6-8-8V18Zm44 0H36c-4.4 0-8 3.6-8 8v20h18c4.4 0 8-3.6 8-8V18Z"
        strokeWidth="2.4"
      />
      <path d="M18 26h10M18 32h12M18 38h8M36 26h10M36 32h12M36 38h8" strokeLinecap="round" strokeWidth="2.4" />
      <path d="M22 50h20" strokeLinecap="round" strokeWidth="2.4" />
    </svg>
  );
}

function MedicalIcon(props: IconProps) {
  return (
    <svg viewBox="0 0 64 64" fill="none" stroke="currentColor" {...props}>
      <path
        d="M32 13c2.5 6 6 9 11 9 5.8 0 10 4.2 10 10 0 12.7-10.4 22-21 22S11 44.7 11 32c0-5.8 4.2-10 10-10 5 0 8.5-3 11-9Z"
        strokeWidth="2.4"
      />
      <path d="M32 24v16M24 32h16" strokeLinecap="round" strokeWidth="2.4" />
    </svg>
  );
}

function MetricCard({
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
    <article className={accent ? "metric-card-accent" : "metric-card"}>
      <p className="text-sm font-medium text-brand-black">{label}</p>
      <p className="mt-3 text-4xl text-brand-black">{value}</p>
      <p className="mt-3 text-sm leading-6 text-brand-muted">{detail}</p>
    </article>
  );
}

function ReadinessStatus({
  label,
  complete,
}: Readonly<{
  label: string;
  complete: boolean;
}>) {
  return (
    <div className="rounded-card border border-brand-line bg-brand-white px-4 py-3">
      <div className="flex items-center gap-3">
        <span
          className={`h-2.5 w-2.5 rounded-full ${
            complete ? "bg-brand-blue" : "bg-brand-line-strong"
          }`}
        />
        <p className="text-sm font-medium text-brand-black">{label}</p>
      </div>
    </div>
  );
}

function SuggestionCard({
  title,
  detail,
  hint,
  icon: Icon,
}: Suggestion) {
  return (
    <article className="metric-card flex h-full flex-col">
      <div className="flex h-16 w-16 items-center justify-center rounded-card border border-brand-line bg-brand-ice text-brand-blue">
        <Icon className="h-9 w-9" />
      </div>
      <h2 className="mt-6 text-2xl text-brand-black">{title}</h2>
      <p className="mt-3 text-sm leading-6 text-brand-muted">{detail}</p>
      <p className="mt-4 text-sm leading-6 text-brand-muted">{hint}</p>
      <div className="mt-auto pt-6">
        <Link href="/calculator" className="app-button-secondary">
          View details
        </Link>
      </div>
    </article>
  );
}

export default function HomePage() {
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

  const suggestionsQuery = useQuery({
    queryKey: ["tax-suggestions", latestYear],
    queryFn: () => fetchTaxSuggestions(latestYear),
    enabled: latestYear > 0,
    staleTime: 60_000, // 60 seconds
  });

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
    <div className="space-y-8 pb-2">
      <section className="mx-auto max-w-4xl px-1 pt-2 text-center">
        <p className="app-eyebrow">Dashboard</p>
        <h1 className="mt-4 text-4xl text-brand-black sm:text-5xl">
          Welcome back, Khor Ze Yi
        </h1>
        <p className="mt-4 text-base leading-8 text-brand-muted sm:text-lg">
          Keep the tax workspace simple, calm, and ready for review. The dashboard
          surfaces the latest filing context first, then keeps live backend and AI
          status within reach.
        </p>
      </section>

      <section className="grid gap-4 lg:grid-cols-3">
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
          label="Receipts to review"
          value={aiSummaryQuery.isLoading ? "..." : String(pendingReceipts)}
          detail={
            aiSummaryQuery.error instanceof Error
              ? "The AI summary is not connected yet."
              : `${formatCurrency(extractedTotal)} detected from the current AI preview.`
          }
          accent
        />
      </section>

      <section className="app-panel p-6 sm:p-7">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <p className="app-eyebrow">Workspace Readiness</p>
            <h2 className="mt-3 text-3xl text-brand-black">
              Progress toward a ready filing workspace
            </h2>
            <p className="mt-3 max-w-3xl text-sm leading-7 text-brand-muted">
              Before claims move into review, the frontend should surface policy
              years clearly, keep the calculator available, and show whether the AI
              extraction service is connected.
            </p>
          </div>
          <div className="rounded-card border border-brand-line bg-brand-ice px-5 py-4 text-left lg:min-w-[14rem]">
            <p className="text-sm font-medium text-brand-black">Status</p>
            <p className="mt-2 text-3xl text-brand-black">{readinessPercent}%</p>
            <p className="mt-2 text-sm leading-6 text-brand-muted">
              {readinessChecks.filter((check) => check.complete).length} of{" "}
              {readinessChecks.length} checks complete
            </p>
          </div>
        </div>

        <div className="mt-6 progress-track">
          <div className="progress-bar" style={{ width: `${readinessPercent}%` }} />
        </div>

        <div className="mt-6 grid gap-3 lg:grid-cols-3">
          {readinessChecks.map((check) => (
            <ReadinessStatus
              key={check.label}
              label={check.label}
              complete={check.complete}
            />
          ))}
        </div>
      </section>

      <section className="space-y-5">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <p className="app-eyebrow">Suggestions</p>
            <h2 className="mt-3 text-3xl text-brand-black">Tax relief suggestions</h2>
          </div>
          <Link href="/calculator" className="app-button-secondary">
            Open calculator
          </Link>
        </div>

        {suggestionsQuery.isLoading && (
          <div className="app-panel p-6 text-center text-brand-muted">
            Loading suggestions...
          </div>
        )}

        {suggestionsQuery.isError && (
          <div className="app-panel p-6 text-center text-brand-muted">
            Unable to load suggestions. Please try again later.
          </div>
        )}

        {suggestionsQuery.data && suggestionsQuery.data.suggestions.length === 0 && (
          <div className="app-panel p-6 text-center">
            <p className="text-brand-black font-medium">No suggestions yet</p>
            <p className="mt-2 text-sm text-brand-muted">
              Upload verified receipts or update your profile to unlock tailored relief suggestions.
            </p>
          </div>
        )}

        {suggestionsQuery.data && suggestionsQuery.data.suggestions.length > 0 && (
          <div className="grid gap-4 xl:grid-cols-3">
            {suggestionsQuery.data.suggestions.map((suggestion) => (
              <DynamicSuggestionCard
                key={suggestion.id}
                suggestion={suggestion}
                policyYear={latestYear}
              />
            ))}
          </div>
        )}
      </section>

      <section className="space-y-5">
        <div>
          <p className="app-eyebrow">Live Status</p>
          <h2 className="mt-3 text-3xl text-brand-black">Operational panels</h2>
        </div>

        <div className="grid gap-4 xl:grid-cols-2">
          <PolicyYearsPanel />
          <AiDemoPanel />
        </div>
      </section>
    </div>
  );
}
