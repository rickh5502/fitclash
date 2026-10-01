## What does this change?

<!-- One or two sentences. If it fixes an issue, link it (e.g. "Fixes #12"). -->

## Why?

<!-- The reasoning, not just the diff. -->

## Testing

<!-- What you ran and what passed. Be specific, e.g.
     "frontend: npx vitest run, 16/16"
     "backend: CI mvn test, 21/21" -->

## Invariant checklist

- [ ] N/A — this PR doesn't touch game math or `FitClash.jsx`
- [ ] Touches `Formulas.java` **and** the matching formula block in
      `frontend/src/FitClash.jsx` was updated in this same PR
- [ ] Touches `frontend/src/FitClash.jsx` **and**
      `node tools/build-preview.mjs` was re-run and
      `frontend/preview/index.html` is committed
