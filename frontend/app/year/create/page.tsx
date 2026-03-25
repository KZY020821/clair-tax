import CreateYearWorkspace from "../../components/year/create-year-workspace";

export default function CreateYearPage() {
  return (
    <div className="space-y-8 pb-2">
      <section className="mx-auto max-w-4xl px-1 pt-2 text-center">
        <p className="app-eyebrow">Years</p>
        <h1 className="mt-4 text-4xl text-brand-black sm:text-5xl">
          Create a filing year workspace
        </h1>
        <p className="mt-4 text-base leading-8 text-brand-muted sm:text-lg">
          Start from a backend policy year, create the workspace for the current
          dev account, then move directly into the year summary and receipt flow.
        </p>
      </section>

      <CreateYearWorkspace />
    </div>
  );
}
