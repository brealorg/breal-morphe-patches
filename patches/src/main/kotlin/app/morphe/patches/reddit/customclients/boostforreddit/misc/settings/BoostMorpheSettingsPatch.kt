package app.morphe.patches.reddit.customclients.boostforreddit.misc.settings

import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.resource.utf8Writer
import org.w3c.dom.Document
import org.w3c.dom.Element

private const val BOOST_MORPHE_FRAGMENT =
    "app.morphe.extension.boostforreddit.settings.BoostMorphePreferenceFragment"

val boostMorpheSettingsPatch = resourcePatch(
    description = "Adds a Morphe settings entry to Boost settings."
) {
    execute {
        get("res/xml/morphe_boost_settings.xml").utf8Writer().use { writer ->
            writer.write("""
                <?xml version="1.0" encoding="utf-8"?>
                <PreferenceScreen
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:app="http://schemas.android.com/apk/res-auto">
                    <PreferenceCategory
                        android:title="Media previews"
                        app:iconSpaceReserved="false">
                        <SwitchPreferenceCompat
                            android:key="morphe_boost_inline_previews"
                            android:title="Inline previews"
                            android:summary="Show GIF and image previews directly in comments."
                            android:defaultValue="true"
                            app:iconSpaceReserved="false" />
                        <SwitchPreferenceCompat
                            android:key="morphe_boost_left_align_previews"
                            android:title="Left-align previews"
                            android:summary="Align inline previews with the comment text instead of filling the row."
                            android:defaultValue="false"
                            app:iconSpaceReserved="false" />
                        <SwitchPreferenceCompat
                            android:key="morphe_boost_hide_source_after_preview"
                            android:title="Hide source text after preview loads"
                            android:summary="Keep the original link text hidden when a preview is displayed."
                            android:defaultValue="false"
                            app:iconSpaceReserved="false" />
                    </PreferenceCategory>
                </PreferenceScreen>
            """.trimIndent())
        }

        document("res/xml/pref_headers_v2.xml").use { document ->
            document.insertPreferenceScreenEntry()
        }

        document("res/xml/pref_headers.xml").use { document ->
            document.insertPreferenceHeadersEntry()
        }

        document("res/xml/pref_headers_simple.xml").use { document ->
            document.insertPreferenceHeadersEntry()
        }
    }
}

private fun Document.insertPreferenceScreenEntry() {
    val root = documentElement ?: return
    if (root.hasMorpheSettingsEntry()) return

    val preference = createElement("Preference").apply {
        setAttribute("android:icon", "@drawable/ic_settings_24dp")
        setAttribute("android:title", "Morphe")
        setAttribute("android:summary", "Boost media preview settings")
        setAttribute("android:fragment", BOOST_MORPHE_FRAGMENT)
        setAttribute("app:allowDividerAbove", "true")
    }

    root.insertBefore(preference, root.firstChildWithAttribute("android:key", "remove_ads"))
}

private fun Document.insertPreferenceHeadersEntry() {
    val root = documentElement ?: return
    if (root.hasMorpheSettingsEntry()) return

    val header = createElement("header").apply {
        setAttribute("android:icon", "@drawable/ic_settings_24dp")
        setAttribute("android:title", "Morphe")
        setAttribute("android:summary", "Boost media preview settings")
        setAttribute("android:fragment", BOOST_MORPHE_FRAGMENT)
    }

    root.insertBefore(header, root.firstChildWithAttribute("android:fragment", "com.rubenmayayo.reddit.ui.preferences.PreferenceFragmentAbout"))
}

private fun Element.hasMorpheSettingsEntry(): Boolean {
    val children = childNodes
    for (i in 0 until children.length) {
        val element = children.item(i) as? Element ?: continue
        if (element.getAttribute("android:fragment") == BOOST_MORPHE_FRAGMENT) {
            return true
        }
    }
    return false
}

private fun Element.firstChildWithAttribute(name: String, value: String): Element? {
    val children = childNodes
    for (i in 0 until children.length) {
        val element = children.item(i) as? Element ?: continue
        if (element.getAttribute(name) == value) {
            return element
        }
    }
    return null
}
