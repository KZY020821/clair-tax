import { redirect } from "next/navigation";

export default function LegacyNewYearPage() {
  redirect("/year/create");
}
