import { readFileSync, writeFileSync } from 'node:fs';

const profilePath = process.argv[2];
if (!profilePath) {
  throw new Error('Usage: node scripts/configure-client-runtime.mjs <forge-profile.json>');
}

// HeadlessMC 2.10.0 selects Java from version metadata, not a launch --java option.
// Override the generated Forge profile so an inherited Java 17 cannot be selected.
const profile = JSON.parse(readFileSync(profilePath, 'utf8'));
profile.javaVersion = { component: 'java-runtime-delta', majorVersion: 21 };
writeFileSync(profilePath, JSON.stringify(profile, null, 2) + '\n');
