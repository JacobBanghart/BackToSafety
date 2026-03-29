import Constants from 'expo-constants';

const FALLBACK_APP_NAME = 'Back to Safety';
const FALLBACK_VERSION = '1.0.0';

export function getAppName(): string {
  return Constants.expoConfig?.name ?? FALLBACK_APP_NAME;
}

export function getAppVersionLabel(): string {
  const version = Constants.nativeAppVersion ?? Constants.expoConfig?.version ?? FALLBACK_VERSION;
  const build = Constants.nativeBuildVersion;

  if (version.startsWith('internal-')) {
    return version;
  }

  if (!build || build === version) {
    return version;
  }

  return `${version} (${build})`;
}
