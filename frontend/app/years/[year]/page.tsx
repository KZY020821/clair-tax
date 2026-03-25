import { redirect } from "next/navigation";

type PolicyYearPageProps = Readonly<{
  params: Promise<{
    year: string;
  }>;
}>;

export default async function LegacyPolicyYearPage({
  params,
}: PolicyYearPageProps) {
  const { year } = await params;
  redirect(`/year/${year}`);
}
