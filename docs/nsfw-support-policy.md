# NSFW and mature-content support policy

## Summary

Restoring or bypassing access to Reddit NSFW communities is not currently within the scope of this project.

This is a technical and maintenance decision, not a judgement about legal adult content or the people who use it.

## Reddit-controlled access

Reddit controls mature-content access through its servers, account state, API responses, regional requirements, and age-assurance systems.

Boost can only display content that Reddit returns to the authenticated client. When Reddit rejects access, requires age confirmation, returns an empty listing, or omits restricted data, a local Boost patch has no reliable content stream to repair.

Enabling “Show mature content” in Reddit or Boost only expresses a display preference. It cannot guarantee that a legacy third-party client will receive the requested community or post data.

This project will not implement:

- age-verification bypasses;
- moderator-status workarounds;
- client-identity or user-agent tricks intended to evade access restrictions;
- scraping or unofficial proxy services for restricted Reddit content;
- redistribution of restricted Reddit data.

These approaches would be brittle, difficult to validate, and could place users’ accounts, OAuth credentials, or API access at risk.

## Redgifs and other adult-oriented media providers

The patch bundle includes the inherited `Fix Redgifs API` patch as best-effort compatibility for an obsolete client API flow.

Its presence does not guarantee:

- access to Reddit NSFW communities;
- availability of individual Redgifs media;
- reliable playback, replay, audio, or performance;
- compatibility with future Redgifs API or delivery changes;
- ongoing provider-specific maintenance.

Redgifs-specific playback and performance work is not currently prioritized.

A generic media defect may still be considered when it is reproducible with ordinary non-NSFW media and can be corrected without introducing provider-specific adult-content infrastructure.

See:

- [Issue #12 — Redgifs playback](https://github.com/brealorg/breal-morphe-patches/issues/12)
- [Issue #13 — Random NSFW](https://github.com/brealorg/breal-morphe-patches/issues/13)

## Random NSFW

Reddit removed the original random-NSFW behavior used by older clients.

Recreating it would require a separate adult-community discovery system backed by external lists, scraping, proxying, or manually maintained data. That would be a new content-discovery service rather than a compatibility fix for Boost.

It would also introduce substantial reliability, trust, moderation, privacy, and maintenance obligations. Random-NSFW restoration is therefore not planned.

## What remains in scope

An issue is not automatically rejected merely because the affected post is marked NSFW.

The project may still investigate a defect when:

- the same bug is reproducible with non-NSFW content;
- Reddit returned the content successfully and Boost handles it incorrectly;
- it is a generic crash, layout, viewer, download, or media-routing regression;
- the proposed fix is local, narrowly scoped, testable, and maintainable.

## Reconsideration

This policy may be reconsidered if Reddit provides a documented and supported third-party access path that can be implemented without bypassing platform restrictions and can be maintained and runtime-tested through the project’s normal release process.

Until then, reports requesting restored NSFW access, age-gate bypasses, random-NSFW discovery, or dedicated adult-provider maintenance will normally be closed as not planned.

## Relevant upstream documentation

- [Reddit User Agreement](https://redditinc.com/policies/user-agreement)
- [Reddit Developer Terms](https://redditinc.com/policies/developer-terms)
- [Reddit Data API Terms](https://redditinc.com/policies/data-api-terms)
- [Why is Reddit asking for my age?](https://support.reddithelp.com/hc/en-us/articles/36429514849428-Why-is-Reddit-asking-for-my-age)
- [How old do I need to be to use Reddit?](https://support.reddithelp.com/hc/en-us/articles/360043066492-How-old-do-I-need-to-be-to-use-Reddit)
