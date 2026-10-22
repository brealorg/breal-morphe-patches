/*
 * Modifications Copyright 2026 brealorg.
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.comments

import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.reddit.customclients.boostforreddit.BoostCompatible
import org.w3c.dom.Document
import org.w3c.dom.Element
import app.morphe.patches.all.misc.PatchCategory

private const val COMMENTS_SLIDR_NO_TITLE_MARKER =
    "morphe_boost_comments_slidrtheme_notitle_noactionbar_v12"

private fun Element.setOrAppendItem(document: Document, itemName: String, itemValue: String) {
    val items = getElementsByTagName("item")
    for (i in 0 until items.length) {
        val item = items.item(i) as? Element ?: continue
        if (item.getAttribute("name") == itemName) {
            item.textContent = itemValue
            return
        }
    }

    val item = document.createElement("item")
    item.setAttribute("name", itemName)
    item.textContent = itemValue
    appendChild(item)
}

@Suppress("unused")
val fixCommentsLemmyModelUiPatch = resourcePatch(
    name = "Fix Boost comments Lemmy-style toolbar UI",
    description = "Removes the duplicate title above comments, keeping Boost's theme, toolbar title and sort label.",
    default = true
) {
    category(PatchCategory.COMMENTS)
    compatibleWith(*BoostCompatible)

    execute {
        check(COMMENTS_SLIDR_NO_TITLE_MARKER.startsWith("morphe_boost_comments_"))

        document("res/values/styles.xml").use { document ->
            val styles = document.getElementsByTagName("style")
            var patched = false

            for (i in 0 until styles.length) {
                val style = styles.item(i) as? Element ?: continue
                if (style.getAttribute("name") == "SlidrTheme") {
                    style.removeAttribute("parent")
                    style.setOrAppendItem(document, "windowNoTitle", "true")
                    style.setOrAppendItem(document, "windowActionBar", "false")
                    style.setOrAppendItem(document, "android:windowNoTitle", "true")
                    style.setOrAppendItem(document, "android:windowActionBar", "false")
                    patched = true
                    break
                }
            }

            if (!patched) {
                error("Boost comments SlidrTheme style not found")
            }
        }
    }
}
