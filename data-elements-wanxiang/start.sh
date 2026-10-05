#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
command -v node >/dev/null || { echo 'Node.js 22.12+ is required'; exit 1; }
node -e "const [a,b]=process.versions.node.split('.').map(Number);process.exit(a<22||(a===22&&b<12)?1:0)" || { echo 'Node.js 22.12+ is required'; exit 1; }
node scripts/ensure-dependencies.mjs
exec npm run dev
