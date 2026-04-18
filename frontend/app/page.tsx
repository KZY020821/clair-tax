"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import DynamicSuggestionCard from "./components/suggestion/dynamic-suggestion-card";
import { fetchPolicyYears } from "./lib/policy-years";
import { fetchTaxSuggestions } from "./lib/tax-suggestions";

const QUICK_ACTIONS = [
  {
    eyebrow: "Calculate",
    title: "Tax Calculator",
    description: "Estimate your chargeable income and see how much tax you owe this year.",
    href: "/calculator",
  },
  {
    eyebrow: "Receipts",
    title: "View Receipts",
    description: "Browse and manage all receipts uploaded across your filing years.",
    href: "/receipts",
  },
  {
    eyebrow: "Profile",
    title: "Update Profile",
    description: "Set your marital status, disability, and dependants for accurate relief eligibility.",
    href: "/profile",
  },
  {
    eyebrow: "Workspace",
    title: "New Year",
    description: "Open a fresh filing year workspace to start tracking reliefs and receipts.",
    href: "/year/create",
  },
];

const GETTING_STARTED_STEPS = [
  {
    step: "01",
    title: "Set up your profile",
    description:
      "Add your marital status, disability status, and children so the system can determine which relief categories you are eligible for.",
  },
  {
    step: "02",
    title: "Create a year workspace",
    description:
      "Open a filing year to get a dedicated workspace for tracking your relief claims and uploading supporting receipts.",
  },
  {
    step: "03",
    title: "Upload receipts",
    description:
      "Attach receipts to each relief category. The AI service extracts the merchant, date, and amount automatically.",
  },
  {
    step: "04",
    title: "Run the calculator",
    description:
      "See your estimated chargeable income and tax payable based on the reliefs you have claimed this year.",
  },
];

export default function HomePage() {
  const policyYearsQuery = useQuery({
    queryKey: ["policy-years"],
    queryFn: fetchPolicyYears,
  });

  const policyYears = policyYearsQuery.data ?? [];
  const latestYear = policyYears.reduce(
    (latest, policyYear) => Math.max(latest, policyYear.year),
    0,
  );

  const suggestionsQuery = useQuery({
    queryKey: ["tax-suggestions", latestYear],
    queryFn: () => fetchTaxSuggestions(latestYear),
    enabled: latestYear > 0,
    staleTime: 60_000,
  });

  return (
    <div className="space-y-8 pb-2">
      <section className="mx-auto max-w-3xl px-1 pt-2 text-center">
        <p className="app-eyebrow">Dashboard</p>
        <h1 className="mt-4 text-3xl text-brand-black sm:text-4xl">
          Welcome back, Khor Ze Yi
        </h1>
        <p className="mt-3 text-sm leading-7 text-brand-muted sm:text-base">
          Your tax workspace — policy years, relief calculator, and receipt review
          all in one place.
        </p>
      </section>

      <section className="space-y-4">
        <div>
          <p className="app-eyebrow">Quick Actions</p>
          <h2 className="mt-2 text-2xl text-brand-black">Where do you want to go?</h2>
        </div>
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          {QUICK_ACTIONS.map(({ eyebrow, title, description, href }) => (
            <Link
              key={href}
              href={href}
              className="metric-card flex flex-col gap-3 hover:border-brand-blue hover:shadow-accent transition-shadow"
            >
              <p className="app-eyebrow">{eyebrow}</p>
              <div>
                <p className="text-lg font-semibold text-brand-black">{title}</p>
                <p className="mt-1.5 text-sm leading-6 text-brand-muted">{description}</p>
              </div>
              <p className="mt-auto text-sm font-semibold text-brand-blue">Go →</p>
            </Link>
          ))}
        </div>
      </section>

      <section className="app-panel p-6 sm:p-7">
        <div>
          <p className="app-eyebrow">Getting Started</p>
          <h2 className="mt-2 text-2xl text-brand-black">How Clair Tax works</h2>
          <p className="mt-2 text-sm leading-6 text-brand-muted">
            Follow these four steps to file your Malaysian personal income tax with confidence.
          </p>
        </div>
        <div className="mt-6 grid gap-3 sm:grid-cols-2">
          {GETTING_STARTED_STEPS.map(({ step, title, description }) => (
            <div key={step} className="data-tile">
              <p className="app-eyebrow">{step}</p>
              <p className="mt-2 text-sm font-semibold text-brand-black">{title}</p>
              <p className="mt-1.5 text-sm leading-6 text-brand-muted">{description}</p>
            </div>
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
    </div>
  );
}
