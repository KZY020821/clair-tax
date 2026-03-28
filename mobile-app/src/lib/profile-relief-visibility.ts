import type { MaritalStatus, UserProfile } from "./profile";

type CategoryWithCode = {
  code: string;
};

const childReliefCodes = new Set([
  "breastfeeding_equipment",
  "childcare_fees",
  "sspn_net_savings",
  "child_below_18",
  "child_above_18_non_tertiary",
  "child_over_18_non_tertiary",
  "child_higher_education",
  "child_in_higher_education",
  "disabled_child",
  "disabled_child_higher_education",
  "disabled_child_in_higher_education",
  "child_learning_disability_support",
]);

function supportsChildReliefs(profile: UserProfile): boolean {
  return (
    (profile.maritalStatus === "married" ||
      profile.maritalStatus === "previously_married") &&
    profile.hasChildren === true
  );
}

export function isCategoryVisibleForProfile(
  category: CategoryWithCode,
  profile: UserProfile,
): boolean {
  if (category.code === "disabled_individual") {
    return profile.isDisabled;
  }

  if (category.code === "spouse_relief") {
    return profile.maritalStatus === "married" && profile.spouseWorking === false;
  }

  if (category.code === "disabled_spouse") {
    return (
      profile.maritalStatus === "married" &&
      profile.spouseWorking === false &&
      profile.spouseDisabled === true
    );
  }

  if (category.code === "alimony_paid") {
    return profile.maritalStatus === "previously_married";
  }

  if (childReliefCodes.has(category.code)) {
    return supportsChildReliefs(profile);
  }

  return true;
}

export function isProfileDrivenFixedCategoryActive(
  category: CategoryWithCode,
  profile: UserProfile,
): boolean {
  if (category.code === "disabled_individual") {
    return profile.isDisabled;
  }

  if (category.code === "spouse_relief") {
    return profile.maritalStatus === "married" && profile.spouseWorking === false;
  }

  if (category.code === "disabled_spouse") {
    return (
      profile.maritalStatus === "married" &&
      profile.spouseWorking === false &&
      profile.spouseDisabled === true
    );
  }

  return false;
}

export function describeMaritalStatus(maritalStatus: MaritalStatus): string {
  switch (maritalStatus) {
    case "single":
      return "Single";
    case "married":
      return "Married";
    case "previously_married":
      return "Previously married";
  }
}

export function buildProfileFactList(profile: UserProfile): string[] {
  const facts = [
    profile.isDisabled ? "Taxpayer is disabled" : "Taxpayer is not disabled",
    describeMaritalStatus(profile.maritalStatus),
  ];

  if (profile.maritalStatus === "married") {
    facts.push(
      profile.spouseWorking === true ? "Spouse is working" : "Spouse is not working",
    );
    facts.push(
      profile.spouseDisabled === true
        ? "Spouse is disabled"
        : "Spouse is not disabled",
    );
    facts.push(profile.hasChildren === true ? "Has children" : "No children recorded");
  }

  if (profile.maritalStatus === "previously_married") {
    facts.push(profile.hasChildren === true ? "Has children" : "No children recorded");
  }

  return facts;
}
