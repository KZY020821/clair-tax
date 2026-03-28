import { ReactNode, useEffect, useState } from "react";
import { AppState, Platform } from "react-native";
import {
  QueryClient,
  QueryClientProvider,
  focusManager,
  onlineManager,
} from "@tanstack/react-query";
import * as Network from "expo-network";
import { SafeAreaProvider } from "react-native-safe-area-context";

type AppProvidersProps = Readonly<{
  children: ReactNode;
}>;

function useReactQueryAppStateBridge() {
  useEffect(() => {
    const appStateSubscription = AppState.addEventListener("change", (status) => {
      if (Platform.OS !== "web") {
        focusManager.setFocused(status === "active");
      }
    });

    onlineManager.setEventListener((setOnline) => {
      const networkSubscription = Network.addNetworkStateListener((state) => {
        setOnline(Boolean(state.isConnected));
      });

      return () => {
        networkSubscription.remove();
      };
    });

    return () => {
      appStateSubscription.remove();
    };
  }, []);
}

export function AppProviders({ children }: AppProvidersProps) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            retry: 1,
            staleTime: 60_000,
          },
        },
      }),
  );

  useReactQueryAppStateBridge();

  return (
    <SafeAreaProvider>
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    </SafeAreaProvider>
  );
}
