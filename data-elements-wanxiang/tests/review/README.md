# Source-driven DOM review

This is an explicitly isolated layout test, not a replacement for React, Ant Design or a production build.

1. `npm run review:dom` executes page TSX with local Mock query values and small DOM adapters for hooks/Ant components.
2. `python tests/review/review.py --screenshots` loads the generated local HTML in Chromium and measures 384 light/dark/layout cases.
3. Generated artifacts stay in `.review-3.2/` and are not application assets. Use `--modules=review,mapping,landing` for a targeted rerun.

Install Playwright for Python and Chromium independently. `CHROMIUM_PATH` can select the browser; `TYPESCRIPT_PATH` can select a test compiler. Do not report these snapshots as production application screenshots or interactive E2E results.
