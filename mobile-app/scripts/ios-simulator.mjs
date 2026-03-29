#!/usr/bin/env bun

import { spawnSync } from "node:child_process";
import { existsSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const projectRoot = path.resolve(__dirname, "..");
const isCheckOnly = process.argv.includes("--check");
const metroPort = process.env.CLAIR_IOS_METRO_PORT?.trim() || "8082";

function formatList(items) {
  return items.length > 0 ? items.join(", ") : "none";
}

function printLines(lines) {
  for (const line of lines) {
    console.error(line);
  }
}

function fail(summary, details = []) {
  printLines([summary, ...details]);
  process.exit(1);
}

function runCommand(command, args) {
  const result = spawnSync(command, args, {
    cwd: projectRoot,
    encoding: "utf8",
    env: process.env,
  });

  if (result.error) {
    fail(`Failed to run ${command}.`, [result.error.message]);
  }

  return result;
}

function runJsonCommand(command, args, summary) {
  const result = runCommand(command, args);
  if (result.status !== 0) {
    fail(summary, [result.stderr.trim(), result.stdout.trim()].filter(Boolean));
  }

  try {
    return JSON.parse(result.stdout);
  } catch (error) {
    fail(summary, [
      error instanceof Error ? error.message : "Unknown JSON parse error",
    ]);
  }
}

function parseActiveSimulatorSdkVersion() {
  const result = runCommand("xcodebuild", ["-showsdks"]);
  if (result.status !== 0) {
    fail("Failed to read Xcode SDK information.", [
      result.stderr.trim(),
      result.stdout.trim(),
    ].filter(Boolean));
  }

  const matches = [...result.stdout.matchAll(/Simulator - iOS\s+([0-9]+(?:\.[0-9]+)?)/g)];
  const activeVersion = matches.at(-1)?.[1];

  if (!activeVersion) {
    fail("Failed to detect the active iOS simulator SDK version from Xcode.", [
      "Expected to find a line like `Simulator - iOS <version>` in `xcodebuild -showsdks` output.",
    ]);
  }

  return activeVersion;
}

function compareVersions(left, right) {
  const leftParts = left.split(".").map((part) => Number(part));
  const rightParts = right.split(".").map((part) => Number(part));
  const length = Math.max(leftParts.length, rightParts.length);

  for (let index = 0; index < length; index += 1) {
    const leftPart = leftParts[index] ?? 0;
    const rightPart = rightParts[index] ?? 0;

    if (leftPart !== rightPart) {
      return leftPart - rightPart;
    }
  }

  return 0;
}

function loadAvailableIosRuntimes() {
  const payload = runJsonCommand(
    "xcrun",
    ["simctl", "list", "runtimes", "--json"],
    "Failed to read iOS simulator runtimes.",
  );

  return (payload.runtimes ?? [])
    .filter((runtime) => runtime.platform === "iOS" && runtime.isAvailable === true)
    .sort((left, right) => compareVersions(left.version, right.version));
}

function loadAvailableDevices() {
  const payload = runJsonCommand(
    "xcrun",
    ["simctl", "list", "devices", "--json"],
    "Failed to read iOS simulator devices.",
  );

  return payload.devices ?? {};
}

function selectSimulator(runtimeIdentifier, runtimeVersion, devicesByRuntime) {
  const matchingDevices = (devicesByRuntime[runtimeIdentifier] ?? []).filter(
    (device) =>
      device.isAvailable === true &&
      typeof device.deviceTypeIdentifier === "string" &&
      device.deviceTypeIdentifier.includes("iPhone"),
  );

  if (matchingDevices.length === 0) {
    fail("No available iPhone simulator matches the active Xcode runtime.", [
      `Required runtime: iOS ${runtimeVersion}`,
      "Create or enable an iPhone simulator for that runtime in Simulator or Xcode, then retry.",
    ]);
  }

  const forcedUdid = process.env.CLAIR_IOS_SIMULATOR_UDID?.trim();
  if (forcedUdid) {
    const forcedDevice = matchingDevices.find((device) => device.udid === forcedUdid);
    if (!forcedDevice) {
      fail("CLAIR_IOS_SIMULATOR_UDID does not match an available iPhone simulator on the active runtime.", [
        `Required runtime: iOS ${runtimeVersion}`,
        `Requested UDID: ${forcedUdid}`,
        `Matching iPhone simulators: ${formatList(matchingDevices.map((device) => `${device.name} (${device.udid})`))}`,
      ]);
    }

    return forcedDevice;
  }

  return (
    matchingDevices.find((device) => device.state === "Booted") ??
    matchingDevices[0]
  );
}

function findLocalExpoBinary() {
  const binaryPath = path.join(projectRoot, "node_modules", ".bin", "expo");
  if (!existsSync(binaryPath)) {
    fail("Local Expo CLI is not installed in mobile-app.", [
      "Run `bun install` in `mobile-app` and retry.",
    ]);
  }

  return binaryPath;
}

function run() {
  const activeSdkVersion = parseActiveSimulatorSdkVersion();
  const availableRuntimes = loadAvailableIosRuntimes();
  const installedVersions = availableRuntimes.map((runtime) => runtime.version);
  const matchingRuntime = availableRuntimes.find(
    (runtime) => runtime.version === activeSdkVersion,
  );

  if (!matchingRuntime) {
    fail("iOS simulator preflight failed.", [
      `Active Xcode simulator SDK: ${activeSdkVersion}`,
      `Installed iOS simulator runtimes: ${formatList(installedVersions)}`,
      `Install iOS ${activeSdkVersion} from Xcode > Settings > Components, then retry.`,
    ]);
  }

  const devicesByRuntime = loadAvailableDevices();
  const selectedDevice = selectSimulator(
    matchingRuntime.identifier,
    matchingRuntime.version,
    devicesByRuntime,
  );

  printLines([
    "iOS simulator preflight passed.",
    `Active Xcode simulator SDK: ${activeSdkVersion}`,
    `Matched runtime: iOS ${matchingRuntime.version}`,
    `Selected simulator: ${selectedDevice.name} (${selectedDevice.udid}) [${selectedDevice.state}]`,
  ]);

  if (isCheckOnly) {
    return;
  }

  const expoBinary = findLocalExpoBinary();
  const launchResult = spawnSync(
    expoBinary,
    ["run:ios", "--device", selectedDevice.udid, "--port", metroPort],
    {
      cwd: projectRoot,
      env: process.env,
      stdio: "inherit",
    },
  );

  if (launchResult.error) {
    fail("Failed to launch Expo iOS.", [launchResult.error.message]);
  }

  process.exit(launchResult.status ?? 1);
}

run();
