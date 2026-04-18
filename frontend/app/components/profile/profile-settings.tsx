"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";
import {
  deleteAccount,
  fetchProfile,
  updateProfile,
  type MaritalStatus,
  type UpdateProfileRequest,
  type UserProfile,
} from "../../lib/profile";
import {
  buildProfileFactList,
  describeMaritalStatus,
} from "../../lib/profile-relief-visibility";

type ProfileFormState = {
  isDisabled: boolean;
  maritalStatus: MaritalStatus;
  spouseDisabled: boolean;
  spouseWorking: boolean;
  hasChildren: boolean;
};

function toFormState(profile: UserProfile): ProfileFormState {
  return {
    isDisabled: profile.isDisabled,
    maritalStatus: profile.maritalStatus,
    spouseDisabled: profile.spouseDisabled === true,
    spouseWorking: profile.spouseWorking === true,
    hasChildren: profile.hasChildren === true,
  };
}

function buildPayload(formState: ProfileFormState): UpdateProfileRequest {
  if (formState.maritalStatus === "married") {
    return {
      isDisabled: formState.isDisabled,
      maritalStatus: formState.maritalStatus,
      spouseDisabled: formState.spouseDisabled,
      spouseWorking: formState.spouseWorking,
      hasChildren: formState.hasChildren,
    };
  }

  if (formState.maritalStatus === "previously_married") {
    return {
      isDisabled: formState.isDisabled,
      maritalStatus: formState.maritalStatus,
      hasChildren: formState.hasChildren,
    };
  }

  return {
    isDisabled: formState.isDisabled,
    maritalStatus: formState.maritalStatus,
  };
}

function CheckboxField({
  id,
  label,
  detail,
  checked,
  onChange,
}: Readonly<{
  id: string;
  label: string;
  detail: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
}>) {
  return (
    <label
      htmlFor={id}
      className="flex items-start gap-3 rounded-card border border-brand-line bg-brand-white px-4 py-4"
    >
      <input
        id={id}
        type="checkbox"
        checked={checked}
        onChange={(event) => onChange(event.target.checked)}
        className="mt-1 h-4 w-4 rounded border-brand-lineStrong text-brand-blue focus:ring-brand-blue/20"
      />
      <span className="min-w-0">
        <span className="block text-sm font-semibold text-brand-black">
          {label}
        </span>
        <span className="mt-1 block text-sm leading-6 text-brand-muted">
          {detail}
        </span>
      </span>
    </label>
  );
}

export default function ProfileSettings() {
  const queryClient = useQueryClient();
  const [draftFormState, setDraftFormState] = useState<ProfileFormState | null>(null);
  const [showDeleteModal, setShowDeleteModal] = useState(false);

  const profileQuery = useQuery({
    queryKey: ["profile"],
    queryFn: fetchProfile,
  });

  const updateMutation = useMutation({
    mutationFn: updateProfile,
    onSuccess: async (profile) => {
      setDraftFormState(toFormState(profile));
      queryClient.setQueryData(["profile"], profile);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["profile"] }),
        queryClient.invalidateQueries({ queryKey: ["user-year-workspace"] }),
      ]);
    },
  });

  const deleteMutation = useMutation({
    mutationFn: deleteAccount,
    onSuccess: async () => {
      if (profileQuery.data) {
        const resetProfile: UserProfile = {
          ...profileQuery.data,
          isDisabled: false,
          maritalStatus: "single",
          spouseDisabled: null,
          spouseWorking: null,
          hasChildren: null,
        };
        setDraftFormState(toFormState(resetProfile));
        queryClient.setQueryData(["profile"], resetProfile);
      }

      setShowDeleteModal(false);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["profile"] }),
        queryClient.invalidateQueries({ queryKey: ["user-years"] }),
        queryClient.invalidateQueries({ queryKey: ["user-year-workspace"] }),
        queryClient.invalidateQueries({ queryKey: ["user-year-receipts"] }),
        queryClient.invalidateQueries({ queryKey: ["receipt-years"] }),
        queryClient.invalidateQueries({ queryKey: ["receipts"] }),
      ]);
    },
  });

  if (profileQuery.isLoading) {
    return (
      <section className="app-panel p-6 sm:p-7">
        <p className="app-eyebrow">Profile</p>
        <h1 className="mt-3 text-4xl text-brand-black">Loading saved profile...</h1>
        <p className="mt-3 text-sm leading-7 text-brand-muted">
          Pulling the current signed-in account details and household settings.
        </p>
      </section>
    );
  }

  if (profileQuery.error instanceof Error) {
    return (
      <section className="app-panel p-6 sm:p-7">
        <p className="app-eyebrow">Profile</p>
        <h1 className="mt-3 text-4xl text-brand-black">Profile is unavailable</h1>
        <p className="mt-3 text-sm leading-7 text-brand-muted">
          {profileQuery.error.message}
        </p>
      </section>
    );
  }

  const profile = profileQuery.data;
  if (!profile) {
    return null;
  }

  const formState = draftFormState ?? toFormState(profile);
  const facts = buildProfileFactList(profile);

  return (
    <div className="space-y-6">
      <section className="app-panel p-6 sm:p-7">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <p className="app-eyebrow">Profile</p>
            <h1 className="mt-3 text-4xl text-brand-black">Saved filing profile</h1>
            <p className="mt-3 max-w-3xl text-sm leading-7 text-brand-muted">
              This saved profile drives family and disability relief visibility
              across the calculator and year workspace.
            </p>
          </div>
          <span className="app-pill-blue">Signed-in account</span>
        </div>

        <div className="mt-6 grid gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.1fr)]">
          <article className="rounded-card border border-brand-line bg-brand-ice px-5 py-5">
            <p className="text-xs font-semibold uppercase tracking-[0.2em] text-brand-muted">
              Email
            </p>
            <p className="mt-3 text-2xl text-brand-black">{profile.email}</p>
            <p className="mt-3 text-sm leading-6 text-brand-muted">
              This email is the active browser session for your current Clair Tax workspace.
            </p>
          </article>

          <article className="rounded-card border border-brand-line bg-brand-white px-5 py-5">
            <p className="text-xs font-semibold uppercase tracking-[0.2em] text-brand-muted">
              Current summary
            </p>
            <div className="mt-4 flex flex-wrap gap-2">
              {facts.map((fact) => (
                <span key={fact} className="app-pill">
                  {fact}
                </span>
              ))}
            </div>
          </article>
        </div>
      </section>

      <section className="app-panel p-6 sm:p-7">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <p className="app-eyebrow">Profile Details</p>
            <h2 className="mt-3 text-3xl text-brand-black">Household settings</h2>
            <p className="mt-3 text-sm leading-7 text-brand-muted">
              Save the current household status here once. The calculator and year
              detail page will reuse these values automatically.
            </p>
          </div>
          <Link href="/calculator" className="app-button-secondary">
            Open calculator
          </Link>
        </div>

        <form
          className="mt-6 space-y-6"
          onSubmit={(event) => {
            event.preventDefault();
            updateMutation.mutate(buildPayload(formState));
          }}
        >
          <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.2fr)]">
            <article className="rounded-card border border-brand-line bg-brand-white px-5 py-5">
              <label className="app-label" htmlFor="maritalStatus">
                Marital status
              </label>
              <select
                id="maritalStatus"
                className="app-input"
                value={formState.maritalStatus}
                onChange={(event) => {
                  const nextStatus = event.target.value as MaritalStatus;
                  setDraftFormState((current) => ({
                      ...(current ?? formState),
                      maritalStatus: nextStatus,
                      spouseDisabled:
                        nextStatus === "married"
                          ? (current ?? formState).spouseDisabled
                          : false,
                      spouseWorking:
                        nextStatus === "married"
                          ? (current ?? formState).spouseWorking
                          : false,
                      hasChildren:
                        nextStatus === "single"
                          ? false
                          : (current ?? formState).hasChildren,
                    }));
                }}
              >
                <option value="single">Single</option>
                <option value="married">Married</option>
                <option value="previously_married">Previously married</option>
              </select>
              <p className="app-help">
                Saved as {describeMaritalStatus(formState.maritalStatus)}.
              </p>
            </article>

            <CheckboxField
              id="isDisabled"
              label="Taxpayer is disabled"
              detail="This applies the saved-profile disability relief when the policy year supports it."
              checked={formState.isDisabled}
              onChange={(checked) => {
                setDraftFormState((current) => ({
                  ...(current ?? formState),
                  isDisabled: checked,
                }));
              }}
            />
          </div>

          {formState.maritalStatus === "married" ? (
            <div className="grid gap-4 md:grid-cols-3">
              <CheckboxField
                id="spouseDisabled"
                label="Spouse is disabled"
                detail="Used to determine disabled-spouse relief eligibility."
                checked={formState.spouseDisabled}
                onChange={(checked) => {
                  setDraftFormState((current) => ({
                    ...(current ?? formState),
                    spouseDisabled: checked,
                  }));
                }}
              />
              <CheckboxField
                id="spouseWorking"
                label="Spouse is working"
                detail="If unchecked, spouse relief can be applied from the saved profile."
                checked={formState.spouseWorking}
                onChange={(checked) => {
                  setDraftFormState((current) => ({
                    ...(current ?? formState),
                    spouseWorking: checked,
                  }));
                }}
              />
              <CheckboxField
                id="hasChildren"
                label="Has children"
                detail="Shows child-related relief categories in the calculator and year page."
                checked={formState.hasChildren}
                onChange={(checked) => {
                  setDraftFormState((current) => ({
                    ...(current ?? formState),
                    hasChildren: checked,
                  }));
                }}
              />
            </div>
          ) : null}

          {formState.maritalStatus === "previously_married" ? (
            <CheckboxField
              id="hasChildren"
              label="Has children"
              detail="Shows child-related relief categories for the saved profile."
              checked={formState.hasChildren}
              onChange={(checked) => {
                setDraftFormState((current) => ({
                  ...(current ?? formState),
                  hasChildren: checked,
                }));
              }}
            />
          ) : null}

          {updateMutation.error instanceof Error ? (
            <div className="rounded-card border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
              {updateMutation.error.message}
            </div>
          ) : null}

          <div className="flex flex-col gap-3 sm:flex-row">
            <button
              type="submit"
              className="app-button-primary"
              disabled={updateMutation.isPending}
            >
              {updateMutation.isPending ? "Saving profile..." : "Save profile"}
            </button>
            <Link href="/year/create" className="app-button-secondary">
              Open year workspace
            </Link>
          </div>
        </form>
      </section>

      <section className="app-panel p-6 sm:p-7">
        <p className="app-eyebrow">Danger</p>
        <h2 className="mt-3 text-3xl text-brand-black">Reset account data</h2>
        <p className="mt-3 max-w-3xl text-sm leading-7 text-brand-muted">
          This removes saved year workspaces, receipts, receipt files, and stored
          profile values for the current signed-in account. The email itself stays
          available after the reset.
        </p>
        <button
          type="button"
          className="mt-6 inline-flex items-center justify-center rounded-full border border-red-300 bg-red-50 px-5 py-3 text-sm font-semibold text-red-700 transition hover:bg-red-100"
          onClick={() => {
            setShowDeleteModal(true);
          }}
        >
          Delete account data
        </button>
      </section>

      {showDeleteModal ? (
        <div className="fixed inset-0 z-[70] flex items-center justify-center bg-brand-black/45 px-4">
          <div className="w-full max-w-lg rounded-panel border border-brand-line bg-brand-white p-6 shadow-accent">
            <p className="app-eyebrow">Confirm Reset</p>
            <h2 className="mt-3 text-3xl text-brand-black">Delete all account data?</h2>
            <p className="mt-3 text-sm leading-7 text-brand-muted">
              This clears the saved profile, year workspaces, receipts, and stored
              receipt files for {profile.email}. This action cannot be undone.
            </p>

            {deleteMutation.error instanceof Error ? (
              <div className="mt-4 rounded-card border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
                {deleteMutation.error.message}
              </div>
            ) : null}

            <div className="mt-6 flex flex-col gap-3 sm:flex-row">
              <button
                type="button"
                className="app-button-primary"
                disabled={deleteMutation.isPending}
                onClick={() => {
                  deleteMutation.mutate();
                }}
              >
                {deleteMutation.isPending ? "Deleting..." : "Confirm delete"}
              </button>
              <button
                type="button"
                className="app-button-secondary"
                disabled={deleteMutation.isPending}
                onClick={() => {
                  setShowDeleteModal(false);
                }}
              >
                Cancel
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}
