# Branch policy

`main` is the canonical development and pull-request target.

Use focused `work/*` branches for changes and open pull requests directly to
`main`. Dependency and Gradle-wrapper automation also target `main`.

Release metadata reaches `main` only through a pull request. Prepare it on a
focused `work/release-*` branch, run the normal required checks, and merge it
before dispatching the release workflow from that exact `main` commit.
Every required check must register on every pull request to `main`; path filters
must not leave an otherwise valid pull request permanently pending.

`dev` is retained only as a release mirror. After remote `main` is verified at
the immutable release commit, the release controller atomically advances only
`dev` and the annotated release tag. It never pushes `main`. Do not use `dev`
as a development, pull-request, or dependency-update target.

Changing or removing the release mirror is a release-controller migration. It
must update `scripts/releasectl.py`, release tests, release documentation, and
the publication workflow together. A custom GitHub App or personal token is not
an accepted bypass for ordinary release publication.

## Remote write preflight and handoff

Authorization and transport capability are separate gates. Before asking for
approval to run a real `git push`, prove the exact transport and refspec with a
non-mutating `git push --dry-run`. A successful `git ls-remote` proves read
access only; it does not prove write access.

If the dry run fails because credentials or write access are unavailable, do
not request push approval and do not attempt the real push. Produce a verified
Git bundle or patch handoff instead. Switching to an API or connector is a
different write path and requires its own explicit approval.

Handoff commands must fail closed. Brace shell variables next to punctuation,
for example `refs/heads/${BRANCH}:refs/heads/${BRANCH}`, guard each mutation,
verify the imported ref against the expected commit SHA, and print an OK result
marker only after that postcondition passes. On any failure, print a failure
marker and return a non-zero status.
