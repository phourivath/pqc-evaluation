#!/usr/bin/env bash
set -euo pipefail

runner_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$runner_dir"

swift package resolve
bash "$runner_dir/prepare-dependencies.sh"
swift build -c release
