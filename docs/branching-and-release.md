# Branching And Release Policy

TRI:READ backend uses `dev` as the integration branch and `main` as the production deployment branch.

## Branch Roles

- `dev`: daily development branch. Feature branches and regular commits should land here first.
- `main`: production branch. Deployments should be based on this branch only.

## Main Branch Protection

Direct commits to `main` cannot be blocked by GitHub Actions alone. Set this in GitHub repository settings:

1. Open `TRI-READ/tri-read-be`.
2. Go to `Settings` -> `Rules` -> `Rulesets`.
3. Create a branch ruleset for `main`.
4. Enable:
   - Restrict updates
   - Require a pull request before merging
   - Require status checks to pass
   - Require branches to be up to date before merging
   - Block force pushes
   - Block deletions
5. Add the `Backend CI / build` check as a required status check after it appears at least once.
6. Enable auto-merge in repository settings if scheduled promotion should merge automatically after checks pass.

## Initial Bootstrap

Scheduled workflows run from the repository default branch. After the first `dev` push, create or merge the initial `dev` -> `main` pull request once, then set `main` as the default branch.

After that, apply the `main` branch ruleset. From that point on, production changes should reach `main` only through pull requests.

## Scheduled Promotion

`.github/workflows/promote-dev-to-main.yml` runs every day at 09:00 Asia/Seoul.

The workflow:

1. Checks whether `dev` has commits that `main` does not have.
2. Creates a `dev` -> `main` pull request when needed.
3. Reuses an existing open promotion PR if one already exists.
4. Enables auto-merge when repository rules and GitHub settings allow it.

If branch protection requires approval, the PR will wait for approval instead of merging immediately.

## Why This Shape

This keeps `main` stable and deployment-oriented while allowing fast development on `dev`. CI validates changes before they can reach production. The scheduled promotion makes releases predictable, and the pull request keeps an auditable record of what moved from development to production.
