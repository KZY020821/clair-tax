import { notFound } from "next/navigation";
import EditReceiptWorkspace from "../../../../../components/year/edit-receipt-workspace";

type EditReceiptPageProps = Readonly<{
  params: Promise<{ year: string; receiptId: string }>;
}>;

export default async function EditReceiptPage({ params }: EditReceiptPageProps) {
  const { year, receiptId } = await params;
  const parsedYear = Number(year);

  if (!Number.isInteger(parsedYear) || parsedYear <= 0 || !receiptId) {
    notFound();
  }

  return (
    <div className="space-y-6 pb-2">
      <EditReceiptWorkspace year={parsedYear} receiptId={receiptId} />
    </div>
  );
}
