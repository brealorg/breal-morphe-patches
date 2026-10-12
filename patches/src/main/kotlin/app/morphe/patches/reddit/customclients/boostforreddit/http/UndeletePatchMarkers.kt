package app.morphe.patches.reddit.customclients.boostforreddit.http

import app.morphe.patcher.patch.resourcePatch

// The settings skeleton always lists the undelete toggles. These string resources tell the
// settings UI at runtime which undelete patches were actually applied, so toggles that would
// do nothing are hidden.
internal const val REDDIT_UNDELETE_APPLIED_RESOURCE = "morphe_boost_undelete_reddit_applied"
internal const val IMGUR_UNDELETE_APPLIED_RESOURCE = "morphe_boost_undelete_imgur_applied"

internal fun undeleteAppliedMarkerPatch(resourceName: String) = resourcePatch {
    execute {
        document("res/values/strings.xml").use { document ->
            val resources = document.documentElement
            resources.appendChild(document.createElement("string").apply {
                setAttribute("name", resourceName)
                textContent = "true"
            })
        }
    }
}
