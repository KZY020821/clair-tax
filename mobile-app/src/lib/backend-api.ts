import * as Linking from "expo-linking";
import { Platform } from "react-native";

function inferLocalHost(): string {
  try {
    const url = Linking.createURL("/");
    const hostname = new URL(url).hostname;

    if (!hostname || hostname === "localhost") {
      return Platform.OS === "android" ? "10.0.2.2" : "127.0.0.1";
    }

    if (hostname === "127.0.0.1" && Platform.OS === "android") {
      return "10.0.2.2";
    }

    return hostname;
  } catch {
    return Platform.OS === "android" ? "10.0.2.2" : "127.0.0.1";
  }
}

const inferredHost = inferLocalHost();

export const backendApiBaseUrl =
  process.env.EXPO_PUBLIC_API_BASE_URL ?? `http://${inferredHost}:8080`;

export const aiServiceBaseUrl =
  process.env.EXPO_PUBLIC_AI_SERVICE_BASE_URL ?? `http://${inferredHost}:8000`;
