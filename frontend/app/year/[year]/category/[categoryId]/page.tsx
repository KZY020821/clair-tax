import { notFound } from "next/navigation";
import CategoryReceiptsWorkspace from "../../../../components/year/category-receipts-workspace";

type CategoryPageProps = Readonly<{
  params: Promise<{ year: string; categoryId: string }>;
}>;

export default async function CategoryPage({ params }: CategoryPageProps) {
  const { year, categoryId } = await params;
  const parsedYear = Number(year);

  if (!Number.isInteger(parsedYear) || parsedYear <= 0 || !categoryId) {
    notFound();
  }

  return (
    <div className="space-y-6 pb-2">
      <CategoryReceiptsWorkspace year={parsedYear} categoryId={categoryId} />
    </div>
  );
}
