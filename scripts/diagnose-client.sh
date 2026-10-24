#!/usr/bin/env bash
set -euo pipefail

mkdir -p .cache/thread-dumps
bash scripts/run_client.sh &
client_launcher=$!

(
  while sleep 300; do
    kill -0 "$client_launcher" 2>/dev/null || break
    echo 'Capturing Minecraft thread dump for startup/export diagnostics.'
    processes=$(jcmd -l 2>&1) || { echo "$processes"; continue; }
    echo "$processes"
    while read -r pid; do
      [[ -n "$pid" ]] || continue
      timeout --kill-after=5s 30s jcmd "$pid" Thread.print -l 2>&1 \
        | tee ".cache/thread-dumps/client-$(date -u +%Y%m%dT%H%M%SZ)-$pid.txt" || true
    done < <(awk '/cpw.mods.bootstraplauncher.BootstrapLauncher/ { print $1 }' <<< "$processes")
  done
) &
diagnostics=$!
trap 'kill "$diagnostics" 2>/dev/null || true' EXIT
trap 'kill "$client_launcher" 2>/dev/null || true; exit 130' INT TERM
wait "$client_launcher"
