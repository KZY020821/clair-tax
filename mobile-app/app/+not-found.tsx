import { useRouter } from "expo-router";
import { Button, Hero, Panel, Screen } from "@/components/ui";

export default function NotFoundScreen() {
  const router = useRouter();

  return (
    <Screen>
      <Hero
        eyebrow="Missing"
        title="This screen does not exist"
        detail="The mobile workspace keeps the same routes as the web app. Jump back to the dashboard to continue."
      />
      <Panel>
        <Button label="Back to dashboard" onPress={() => router.replace("/")} />
      </Panel>
    </Screen>
  );
}
