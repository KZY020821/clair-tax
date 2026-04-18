import TaxCalculator from "../components/calculator/tax-calculator";

export default function CalculatorPage() {
  return (
    <div className="space-y-8 pb-2">
      <section className="mx-auto max-w-4xl px-1 pt-2 text-center">
        <p className="app-eyebrow">Tax Calculator</p>
        <h1 className="mt-4 text-4xl text-brand-black sm:text-5xl">
            Calculate your Malaysian income tax
        </h1>
        <p className="mt-4 text-base leading-8 text-brand-muted sm:text-lg">
          Select your Year of Assessment, enter your income, and apply eligible tax reliefs. Instantly see your chargeable income, tax payable, and full breakdown.
        </p>
      </section>

      <TaxCalculator />
    </div>
  );
}
