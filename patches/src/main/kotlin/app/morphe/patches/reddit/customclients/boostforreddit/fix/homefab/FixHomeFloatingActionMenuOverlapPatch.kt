package app.morphe.patches.reddit.customclients.boostforreddit.fix.homefab

import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.reddit.customclients.boostforreddit.BoostCompatible
import org.w3c.dom.Document
import org.w3c.dom.Element

private const val MAIN_CONTENT_LAYOUT =
    "res/layout/content_main.xml"

private const val INBOX_LAYOUT =
    "res/layout/activity_inbox.xml"

private const val USER_LAYOUT =
    "res/layout/activity_user.xml"

private const val MATERIAL_FAB =
    "com.google.android.material.floatingactionbutton.FloatingActionButton"

private const val INCLUDED_FAB_BOTTOM_MARGIN =
    "56.0dp"

private const val DIRECT_FAB_BOTTOM_MARGIN =
    "72.0dp"

private const val FAB_SIDE_MARGIN =
    "@dimen/fab_margin"

private data class IncludedFab(
    val layout: String,
    val gravity: String,
)

private val MAIN_INCLUDED_FABS =
    listOf(
        IncludedFab(
            layout = "@layout/fab_subreddit",
            gravity = "end|bottom",
        ),
        IncludedFab(
            layout = "@layout/fab_random",
            gravity = "center|bottom",
        ),
    )

private val DIRECT_FAB_IDS =
    setOf(
        "@id/fab",
        "@id/fab_submit",
    )

private fun Document.raiseIncludedFabs(
    layoutName: String,
    targets: List<IncludedFab>,
) {
    val includeNodes = getElementsByTagName("include")
    val includes =
        (0 until includeNodes.length)
            .mapNotNull { includeNodes.item(it) as? Element }

    targets.forEach { target ->
        val matches =
            includes.filter {
                it.getAttribute("layout") == target.layout
            }

        require(matches.size == 1) {
            "Expected one ${target.layout} include in $layoutName, " +
                "found ${matches.size}"
        }

        matches.single().apply {
            /*
             * The Main host contributes one native 16dp FAB separation layer.
             * A 56dp include margin therefore produces the same visual 16dp
             * clearance as the direct 72dp Inbox/Profile geometry.
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
                target.gravity,
            )
            setAttribute(
                "android:layout_marginBottom",
                INCLUDED_FAB_BOTTOM_MARGIN,
            )
        }
    }
}

private fun Document.raiseDirectFabs(
    layoutName: String,
) {
    val fabNodes = getElementsByTagName(MATERIAL_FAB)
    val matches =
        (0 until fabNodes.length)
            .mapNotNull { fabNodes.item(it) as? Element }
            .filter {
                it.getAttribute("android:id") in DIRECT_FAB_IDS
            }

    require(matches.size == DIRECT_FAB_IDS.size) {
        "Expected ${DIRECT_FAB_IDS.size} navigation FABs in $layoutName, " +
            "found ${matches.size}"
    }

    require(
        matches.map {
            it.getAttribute("android:id")
        }.toSet() == DIRECT_FAB_IDS
    ) {
        "Unexpected navigation FAB ids in $layoutName"
    }

    matches.forEach { fab ->
        require(
            fab.getAttribute("android:layout_margin") ==
                FAB_SIDE_MARGIN
        ) {
            "Unexpected native FAB margin for " +
                "${fab.getAttribute("android:id")} in $layoutName"
        }

        /*
         * MarginLayoutParams gives the generic layout_margin precedence over
         * the directional margins. Expand it before overriding the bottom
         * margin so the 72dp navigation clearance survives inflation.
         */
        fab.removeAttribute(
            "android:layout_margin",
        )
        listOf(
            "android:layout_marginLeft",
            "android:layout_marginTop",
            "android:layout_marginRight",
            "android:layout_marginStart",
            "android:layout_marginEnd",
        ).forEach { marginAttribute ->
            fab.setAttribute(
                marginAttribute,
                FAB_SIDE_MARGIN,
            )
        }

        fab.setAttribute(
            "android:layout_marginBottom",
            DIRECT_FAB_BOTTOM_MARGIN,
        )
    }
}

@Suppress("unused")
val fixBoostHomeFloatingActionMenuOverlapPatch = resourcePatch(
    name = "Standardize Boost bottom-navigation FAB clearance",
    description =
        "Keeps Home/Subreddit, Random, Inbox and Profile FABs " +
            "16 dp above the canonical bottom navigation.",
    default = true,
) {
    compatibleWith(*BoostCompatible)

    execute {
        document(MAIN_CONTENT_LAYOUT).use { document ->
            document.raiseIncludedFabs(
                layoutName = MAIN_CONTENT_LAYOUT,
                targets = MAIN_INCLUDED_FABS,
            )
        }

        document(INBOX_LAYOUT).use { document ->
            document.raiseDirectFabs(INBOX_LAYOUT)
        }

        document(USER_LAYOUT).use { document ->
            document.raiseDirectFabs(USER_LAYOUT)
        }
    }
}
