#!/usr/bin/env bash

set -euo pipefail

if ! command -v gitleaks >/dev/null 2>&1; then
  echo "gitleaks not found. Install: https://github.com/gitleaks/gitleaks"
  exit 1
fi

echo "Running gitleaks staged-file scan..."
gitleaks git --pre-commit --staged --redact
