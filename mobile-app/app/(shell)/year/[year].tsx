import { useLocalSearchParams } from "expo-router";
import { Hero, Screen } from "@/components/ui";
import { YearWorkspaceScreen } from "@/components/year/year-workspace";

export default function YearPage() {
  const params = useLocalSearchParams<{ year?: string }>();
  const parsedYear = Number(params.year);

  if (!Number.isInteger(parsedYear)) {
    return (
      <Screen>
        <Hero
          eyebrow="Year Workspace"
          title="Year parameter is invalid"
          detail="Use a numeric assessment year route to open a year workspace."
        />
      </Screen>
    );
  }

  return <YearWorkspaceScreen year={parsedYear} />;
}
