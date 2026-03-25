import { z } from "zod";
import { backendApiBaseUrl } from "./backend-api";

const apiErrorSchema = z.object({
  message: z.string().min(1),
});

const maritalStatusSchema = z.enum([
  "single",
  "married",
  "previously_married",
]);

const profileSchema = z.object({
  id: z.string().uuid(),
  email: z.string().email(),
  isDisabled: z.boolean(),
  maritalStatus: maritalStatusSchema,
  spouseDisabled: z.boolean().nullable(),
  spouseWorking: z.boolean().nullable(),
  hasChildren: z.boolean().nullable(),
});

export type MaritalStatus = z.infer<typeof maritalStatusSchema>;
export type UserProfile = z.infer<typeof profileSchema>;
export type UpdateProfileRequest = {
  isDisabled: boolean;
  maritalStatus: MaritalStatus;
  spouseDisabled?: boolean | null;
  spouseWorking?: boolean | null;
  hasChildren?: boolean | null;
};

async function getApiErrorMessage(
  response: Response,
  fallbackMessage: string,
): Promise<string> {
  try {
    const data: unknown = await response.json();
    const parsed = apiErrorSchema.safeParse(data);

    if (parsed.success) {
      return parsed.data.message;
    }
  } catch {
    return fallbackMessage;
  }

  return fallbackMessage;
}

export async function fetchProfile(): Promise<UserProfile> {
  const response = await fetch(`${backendApiBaseUrl}/api/profile`, {
    headers: {
      Accept: "application/json",
    },
  });

  if (!response.ok) {
    throw new Error(
      await getApiErrorMessage(
        response,
        `Failed to load the saved profile (${response.status})`,
      ),
    );
  }

  const data: unknown = await response.json();

  return profileSchema.parse(data);
}

export async function updateProfile(
  payload: UpdateProfileRequest,
): Promise<UserProfile> {
  const response = await fetch(`${backendApiBaseUrl}/api/profile`, {
    method: "PUT",
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
    },
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    throw new Error(
      await getApiErrorMessage(
        response,
        `Failed to save the profile (${response.status})`,
      ),
    );
  }

  const data: unknown = await response.json();

  return profileSchema.parse(data);
}

export async function deleteAccount(): Promise<void> {
  const response = await fetch(`${backendApiBaseUrl}/api/profile/account`, {
    method: "DELETE",
  });

  if (!response.ok) {
    throw new Error(
      await getApiErrorMessage(
        response,
        `Failed to reset the dev account (${response.status})`,
      ),
    );
  }
}
