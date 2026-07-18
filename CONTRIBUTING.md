# Contribution guidelines

Thanks for helping improve the Breal Morphe patch bundle. This is an unofficial community project focused on practical, maintainable patches for tested app versions.

## Before starting

- Search the [open and closed issues](https://github.com/brealorg/breal-morphe-patches/issues) for existing work.
- Open an issue before starting a behavior change, compatibility fix, or larger contribution so scope and testing can be discussed.
- Small documentation and repository-maintenance corrections may be submitted directly as a pull request.
- Keep each pull request focused on one issue or one closely related change.

## Reporting bugs and requesting features

Use the repository's [issue chooser](https://github.com/brealorg/breal-morphe-patches/issues/new/choose) and select the appropriate form. Include the requested environment details, reproduction steps, links, screenshots, or logs when available.

## Development workflow

1. Fork the repository and create a work branch from the latest `main`.
2. Make the smallest change that fully addresses the agreed scope.
3. Run the relevant build, static checks, and repository verifier scripts.
4. For Boost behavior changes, build, install, and test the DEV package (`com.rubenmayayo.reddit.dev`). Do not overwrite normal Boost during development.
5. Capture concise runtime evidence for user-visible behavior, such as steps, result, screenshots, video, logs, or stable markers.
6. Open a pull request against `main` and link the related issue.

If you cannot perform runtime validation, state that clearly in the pull request so the missing validation is visible.

## Pull request expectations

A pull request should explain:

- what changed and why;
- which app versions and Android versions are affected;
- which build and runtime checks were performed;
- any known risks, limitations, or follow-up work;
- whether documentation, release metadata, or verification tooling also needs updating.

User-visible fixes are not considered fully delivered until they are released and verified through the normal installation path when applicable.

Contributions are reviewed for project scope, maintainability, licensing, and available testing. Submission does not guarantee inclusion in the patch bundle.
