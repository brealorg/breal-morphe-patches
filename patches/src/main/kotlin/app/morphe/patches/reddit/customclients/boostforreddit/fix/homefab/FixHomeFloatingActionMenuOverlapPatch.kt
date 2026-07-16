package app.morphe.patches.reddit.customclients.boostforreddit.fix.homefab

import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.reddit.customclients.boostforreddit.BoostCompatible
import org.w3c.dom.Element

private const val HOME_CONTENT_LAYOUT =
    "res/layout/content_main.xml"

private const val SUBREDDIT_FAB_LAYOUT =
    "@layout/fab_subreddit"

private const val HOME_FAB_BOTTOM_MARGIN =
    "72.0dp"

@Suppress("unused")
val fixBoostHomeFloatingActionMenuOverlapPatch = resourcePatch(
    name = "Fix Boost Home floating action menu overlap",
    description =
        "Raises the Home floating action menu above the bottom navigation " +
            "without changing Comments or split-screen layouts.",
    default = true,
) {
    compatibleWith(*BoostCompatible)

    execute {
        document(HOME_CONTENT_LAYOUT).use { document ->
            val includeNodes = document.getElementsByTagName("include")

            val matches =
                (0 until includeNodes.length)
                    .mapNotNull { includeNodes.item(it) as? Element }
                    .filter {
                        it.getAttribute("layout") ==
                            SUBREDDIT_FAB_LAYOUT
                    }

            require(matches.size == 1) {
                "Expected one fab_subreddit include in content_main.xml, " +
                    "found ${matches.size}"
            }

            matches.single().apply {
                /*
                 * Native bottom-navigation height: 56dp.
                 * Standard Boost FAB separation: 16dp.
                 * Total required Home offset: 72dp.
                 *
                 * Width and height are specified on the include so Android
                 * applies the overridden layout parameters reliably.
                 */
                setAttribute(
                    "android:layout_width",
                    "wrap_content",
                )
                setAttribute(
                    "android:layout_height",
                    "wrap_content",
                )
                setAttribute(
                    "android:layout_gravity",
                    "end|bottom",
                )
                setAttribute(
                    "android:layout_marginBottom",
                    HOME_FAB_BOTTOM_MARGIN,
                )
            }
        }
    }
}
