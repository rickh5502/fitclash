# Deploying FitClash

This repo auto-deploys the **frontend only** to GitHub Pages via
`.github/workflows/pages.yml` on every push to `main`.

> **Backend note:** the Spring Boot API in `backend/` is **not** deployed by
> this workflow. GitHub Pages only serves static files, so it cannot host a
> Java server. The deployed site runs entirely on the mock data already
> wired into `frontend/src/FitClash.jsx` — no backend required.

## 1. Create the GitHub repo and push

`gh` (the GitHub CLI) isn't installed here, so create the repo in the
browser first: go to https://github.com/new, pick a name (e.g. `fitclash`),
leave it empty (no README/.gitignore/license — this repo already has them),
and create it. Then, from this project directory:

```sh
git remote add origin https://github.com/<your-username>/<repo-name>.git
git branch -M main
git add -A
git commit -m "Initial commit"
git push -u origin main
```

GitHub no longer accepts your account password over HTTPS — when prompted
for one, use a [Personal Access Token](https://github.com/settings/tokens)
instead. If you'd rather use SSH (no token needed once your key is added to
GitHub), use this remote URL instead of the https one:

```sh
git remote add origin git@github.com:<your-username>/<repo-name>.git
```

## 2. Turn on GitHub Pages

In the repo on GitHub: **Settings → Pages → Source → "GitHub Actions"**.

That's it — the next push to `main` (including the one above) will trigger
the `pages.yml` workflow, which builds `frontend/` with Vite and publishes
`frontend/dist` to Pages. You can also trigger it manually from the
**Actions** tab via "Run workflow" (`workflow_dispatch`).

## 3. The public URL

Once the workflow finishes (check the **Actions** tab), the site is live at:

```
https://<your-username>.github.io/<repo-name>/
```

The build's base path is derived automatically from the repo name, so this
works regardless of what you named the repo — nothing to configure.

## No-toolchain alternative

To open the UI on any device with zero setup — no git, no Node, no
install — just open `frontend/preview/index.html` directly in a browser
(double-click it, or share the file). It's a generated, dependency-free
single-file copy of the same UI, kept in sync and committed to the repo for
exactly this purpose.
