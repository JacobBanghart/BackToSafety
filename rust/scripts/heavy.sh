#!/usr/bin/env bash
# Runs a heavy command (a cargo or dx build) the way this box can take it: one at a time
# across every worktree, only with enough free memory, and at low CPU and I/O priority so
# ssh stays responsive. Added after the 2026-10-10 reboot: 7 parallel builds plus 4
# emulators exhausted the 48 GB and drove I/O pressure to ~90%.
#
#   heavy.sh <command...>
#
# BUILD_MIN_AVAIL_GB (default 8): wait until MemAvailable is at least this.
# The lock is /tmp/nijii-build.lock; waiting builds say so on stderr.
set -euo pipefail
MIN_GB="${BUILD_MIN_AVAIL_GB:-8}"
avail_gb() { awk '/^MemAvailable:/ {printf "%d", $2 / 1048576}' /proc/meminfo; }

exec 8>/tmp/nijii-build.lock
if ! flock -n 8; then
  echo "heavy.sh: another build is running; waiting for /tmp/nijii-build.lock" >&2
  flock 8
fi
until [ "$(avail_gb)" -ge "$MIN_GB" ]; do
  echo "heavy.sh: $(avail_gb) GB available, waiting for ${MIN_GB} GB" >&2
  sleep 20
done
nice -n 15 ionice -c2 -n7 "$@"
