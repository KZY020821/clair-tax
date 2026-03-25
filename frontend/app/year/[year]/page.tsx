import { notFound } from "next/navigation";
import YearWorkspace from "../../components/year/year-workspace";

type YearPageProps = Readonly<{
  params: Promise<{
    year: string;
  }>;
}>;

export default async function YearPage({ params }: YearPageProps) {
  const { year } = await params;
  const parsedYear = Number(year);

  if (!Number.isInteger(parsedYear)) {
    notFound();
  }

  return (
    <div className="space-y-8 pb-2">
      <YearWorkspace year={parsedYear} />
    </div>
  );
}
