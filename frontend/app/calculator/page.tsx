import TaxCalculator from "../components/calculator/tax-calculator";

export default function CalculatorPage() {
  return (
    <div className="space-y-8 pb-2">
      <section className="mx-auto max-w-4xl px-1 pt-2 text-center">
        <p className="app-eyebrow">Calculator</p>
        <h1 className="mt-4 text-4xl text-brand-black sm:text-5xl">
          Tax calculator
        </h1>
        <p className="mt-4 text-base leading-8 text-brand-muted sm:text-lg">
          Load a filing year, enter income, and review relief claims in one clean
          workspace. The calculation stays backend-driven, while the interface keeps
          the steps easy to scan.
        </p>
      </section>

      <TaxCalculator />
    </div>
  );
}
