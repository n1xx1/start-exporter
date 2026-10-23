#!/usr/bin/env bash
set -euo pipefail

if (( $# != 0 )); then
  echo 'Usage: bash scripts/run_client.sh' >&2
  exit 2
fi
if [[ ! -f run/saves/empty-world/level.dat ]]; then
  echo 'Missing empty-world save. Run pnpm prepare-world first.' >&2
  exit 1
fi

mkdir -p HeadlessMC .cache
cat > HeadlessMC/config.properties <<EOF
hmc.java.versions=$JAVA_HOME
hmc.gamedir=$PWD/run
hmc.gameargs=--quickPlaySingleplayer empty-world
hmc.mcdir=$HOME/.minecraft
hmc.offline=true
hmc.rethrow.launch.exceptions=true
hmc.exit.on.failed.command=true
hmc.jline.enabled=false
EOF

curl --fail --location --retry 3 -o headlessmc.jar \
  https://github.com/headlesshq/headlessmc/releases/download/2.10.0/headlessmc-launcher-2.10.0.jar
echo '52bd5006f478377b3893011d458562977d38c65ead6d2b31089beb4d614f13cd  headlessmc.jar' | sha256sum --check

mc=$(node --input-type=module -e 'import fs from "node:fs"; console.log(JSON.parse(fs.readFileSync("run/pack-lock.json", "utf8")).minecraft)')
forge=$(node --input-type=module -e 'import fs from "node:fs"; console.log(JSON.parse(fs.readFileSync("run/pack-lock.json", "utf8")).forge)')

if [[ "$mc" != 1.20.1 ]]; then
  echo "Pinned HMC-Specifics supports Minecraft 1.20.1; update it before launching $mc." >&2
  exit 1
fi
specifics=run/mods/hmc-specifics-forge-1.20.1-1.2.2.jar
specifics_sha=00f590f8202f350dfa3542b18b0b31b340b5d79bfa281dae0ee9412abb2784cc
if ! echo "$specifics_sha  $specifics" | sha256sum --check --status; then
  curl --fail --location --retry 3 -o "$specifics.part" \
    https://github.com/headlesshq/hmc-specifics/releases/download/v1.20.1-1.2.2/hmc-specifics-forge-1.20.1-1.2.2.jar
  echo "$specifics_sha  $specifics.part" | sha256sum --check
  mv "$specifics.part" "$specifics"
fi

if [[ ! -f "$HOME/.minecraft/versions/$mc/$mc.json" || ! -f "$HOME/.minecraft/versions/$mc/$mc.jar" ]]; then
  java -jar headlessmc.jar --command download "$mc"
fi

forge_id="$mc-forge-$forge"
if [[ ! -f "$HOME/.minecraft/versions/$forge_id/$forge_id.json" ]]; then
  java -jar headlessmc.jar --command forge "$mc" --uid "$forge"
fi

console_dir=/tmp/start-export-data-console
mkdir "$console_dir" # Refuse concurrent launchers sharing the same command input.
trap 'exec 9>&-; rm -f "$console_dir/input" "$console_dir/write.lock"; rmdir "$console_dir"' EXIT
mkfifo -m 600 "$console_dir/input"
# Holding both ends open prevents EOF between separate docker exec commands.
exec 9<>"$console_dir/input"

# timeout --signal=TERM --kill-after=30s 35m \
xvfb-run -a -s '-screen 0 1280x720x24 +extension GLX +render -noreset' \
  java -Dhmc.check.xvfb=true -jar headlessmc.jar --command launch \
  "^${mc//./\.}-forge-${forge//./\.}$" -regex \
  --jvm '-Xms1G -Xmx6G' <&9 9>&- 2>&1 | tee .cache/client-console.log
