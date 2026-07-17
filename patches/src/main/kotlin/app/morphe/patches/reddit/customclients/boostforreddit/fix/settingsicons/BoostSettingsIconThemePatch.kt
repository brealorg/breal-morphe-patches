package app.morphe.patches.reddit.customclients.boostforreddit.fix.settingsicons

import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.reddit.customclients.boostforreddit.BoostCompatible
import org.w3c.dom.Element

private const val SETTINGS_ICON_THEME_MARKER =
    "MORPHE_BOOST_SETTINGS_ICON_THEME_V1"

private val preferenceLayouts = listOf(
    "res/layout/preference.xml",
    "res/layout/preference_dropdown.xml",
    "res/layout/preference_widget_seekbar.xml",
    "res/layout/preference_information_material.xml",
    "res/layout/image_frame.xml",
)

@Suppress("unused")
val boostSettingsIconThemePatch = resourcePatch(
    name = "Theme Boost settings icons",
    description = "Uses the active theme primary text color for Boost preference icons.",
    default = true,
) {
    compatibleWith(*BoostCompatible)

    execute {
        preferenceLayouts.forEach { layout ->
            document(layout).use { document ->
                val nodes = document.getElementsByTagName("*")
                var matchedIcons = 0

                for (index in 0 until nodes.length) {
                    val element = nodes.item(index) as? Element
                        ?: continue

                    if (
                        element.getAttribute("android:id") ==
                        "@android:id/icon"
                    ) {
                        element.setAttribute(
                            "android:tint",
                            "?android:textColorPrimary",
                        )
                        matchedIcons++
                    }
                }

                require(matchedIcons == 1) {
                    "Expected one preference icon in $layout, " +
                        "found $matchedIcons; " +
                        "marker=$SETTINGS_ICON_THEME_MARKER"
                }
            }
        }
    }
}
