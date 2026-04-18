import { redirect } from "next/navigation";

type LegacyReceiptsYearPageProps = Readonly<{
  params: Promise<{
    year: string;
  }>;
}>;

export default async function LegacyReceiptsYearPage({
  params,
}: LegacyReceiptsYearPageProps) {
  const { year } = await params;
  redirect(`/year/${year}`);
}
