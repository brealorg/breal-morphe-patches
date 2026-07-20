# Supported app catalog

This catalog lists the package identities currently declared by patch
compatibility metadata and published in `patches-list.json`. It is a metadata
contract, not a claim that every app/version combination has received the same
runtime validation.

`tools/compatible_app_catalog.py` is the shared tooling catalog. Project source
contracts keep it aligned with Kotlin `Compatibility` declarations and the
published feed.

| Package name | Display name |
|---|---|
| `com.andrewshu.android.reddit` | rif is fun |
| `com.andrewshu.android.redditdonation` | rif is fun golden platinum |
| `com.imgur.mobile` | Imgur |
| `com.laurencedawson.reddit_sync` | Sync for Reddit |
| `com.laurencedawson.reddit_sync.dev` | Sync for Reddit Dev |
| `com.laurencedawson.reddit_sync.pro` | Sync for Reddit Pro |
| `com.onelouder.baconreader` | BaconReader |
| `com.onelouder.baconreader.premium` | BaconReader Premium |
| `com.rubenmayayo.reddit` | Boost for Reddit |
| `free.reddit.news` | Relay for Reddit |
| `io.syncapps.lemmy_sync` | Sync for Lemmy |
| `me.edgan.redditslide` | Slide (fork) |
| `ml.docilealligator.infinityforreddit.patreon` | Infinity for Reddit (Patreon) |
| `ml.docilealligator.infinityforreddit.plus` | Infinity for Reddit+ |
| `o.o.joey` | Joey for Reddit |
| `o.o.joey.dev` | Joey for Reddit Dev |
| `o.o.joey.pro` | Joey for Reddit Pro |
| `org.cygnusx1.continuum` | Continuum |
| `reddit.news` | Relay for Reddit Pro |

Legacy aliases retained by normalization tooling are not additional supported
patch targets.
