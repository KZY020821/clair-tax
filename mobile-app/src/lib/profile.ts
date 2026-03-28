import { z } from "zod";
import { fetchJson, getApiErrorMessage, getBackendUrl } from "./http";

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

export async function fetchProfile(): Promise<UserProfile> {
  return fetchJson(getBackendUrl("/api/profile"), (value) => profileSchema.parse(value));
}

export async function updateProfile(
  payload: UpdateProfileRequest,
): Promise<UserProfile> {
  const response = await fetch(getBackendUrl("/api/profile"), {
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

  return profileSchema.parse(await response.json());
}

export async function deleteAccount(): Promise<void> {
  const response = await fetch(getBackendUrl("/api/profile/account"), {
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
