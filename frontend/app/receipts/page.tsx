import { redirect } from "next/navigation";

export default function LegacyReceiptsPage() {
  redirect("/year/create");
}
