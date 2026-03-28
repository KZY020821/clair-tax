import { Slot } from "expo-router";
import { MobileShell } from "@/components/shell/mobile-shell";

export default function ShellLayout() {
  return (
    <MobileShell>
      <Slot />
    </MobileShell>
  );
}
