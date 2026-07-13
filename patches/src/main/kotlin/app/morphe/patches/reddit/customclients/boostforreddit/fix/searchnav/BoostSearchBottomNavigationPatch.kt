package app.morphe.patches.reddit.customclients.boostforreddit.fix.searchnav

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.reddit.customclients.boostforreddit.BoostCompatible
import app.morphe.patches.reddit.customclients.boostforreddit.misc.extension.sharedExtensionPatch
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import org.w3c.dom.Element

private const val MATERIAL_BOTTOM_NAVIGATION =
    "com.google.android.material.bottomnavigation.BottomNavigationView"

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/boostforreddit/utils/BoostSearchBottomNavigation;"

private val addSearchBottomNavigationHostPatch = resourcePatch(
    name = "Add Boost Search bottom navigation host",
    description = "Adds Boost's native Material bottom-navigation host to the generic Search layout.",
    default = false,
) {
    compatibleWith(*BoostCompatible)

    execute {
        document("res/layout/activity_search_generic.xml").use { document ->
            val root = document.documentElement as Element

            require(
                root.tagName ==
                    "androidx.coordinatorlayout.widget.CoordinatorLayout"
            ) {
                "Unexpected activity_search_generic root: ${root.tagName}"
            }

            val existing =
                document.getElementsByTagName(MATERIAL_BOTTOM_NAVIGATION)

            require(existing.length <= 1) {
                "Expected at most one Search BottomNavigationView, found ${existing.length}"
            }

            if (existing.length == 0) {
                val navigation = document
                    .createElement(MATERIAL_BOTTOM_NAVIGATION)
                    .apply {
                        setAttribute("android:id", "@id/bottom_navigation_view")
                        setAttribute("android:visibility", "visible")
                        setAttribute("android:layout_gravity", "bottom")
                        setAttribute("android:layout_width", "match_parent")
                        setAttribute("android:layout_height", "wrap_content")
                        setAttribute(
                            "app:layout_behavior",
                            "com.google.android.material.behavior.HideBottomViewOnScrollBehavior",
                        )
                        setAttribute(
                            "app:menu",
                            "@menu/bottom_navigation_menu",
                        )
                    }

                root.appendChild(navigation)
            } else {
                val navigation = existing.item(0) as Element

                require(
                    navigation.getAttribute("android:id") ==
                        "@id/bottom_navigation_view"
                ) {
                    "Existing Search BottomNavigationView has unexpected id"
                }

                navigation.setAttribute("android:visibility", "visible")
                navigation.setAttribute(
                    "app:menu",
                    "@menu/bottom_navigation_menu",
                )
            }
        }
    }
}

@Suppress("unused")
val boostSearchBottomNavigationPatch = bytecodePatch(
    name = "Add Boost Search bottom navigation",
    description = "Shows Boost's native Home, Search, Subscriptions, Inbox and Profile menu in Search and Go To.",
    default = true,
) {
    compatibleWith(*BoostCompatible)
    dependsOn(addSearchBottomNavigationHostPatch, sharedExtensionPatch)

    execute {
        searchAbstractActivityOnCreateFingerprint.method.apply {
            val setContentViewIndices =
                implementation!!.instructions
                    .withIndex()
                    .mapNotNull { (index, instruction) ->
                        val reference =
                            instruction.getReference<MethodReference>()
                                ?: return@mapNotNull null

                        if (
                            reference.name == "setContentView" &&
                            reference.parameterTypes.size == 1 &&
                            reference.parameterTypes[0].toString() == "I"
                        ) {
                            index
                        } else {
                            null
                        }
                    }

            require(setContentViewIndices.size == 1) {
                "Expected exactly one SearchAbstractActivity.setContentView call, found ${setContentViewIndices.size}"
            }

            addInstructions(
                setContentViewIndices.single() + 1,
                """
                    invoke-static {p0}, $EXTENSION_CLASS_DESCRIPTOR->install(Landroid/app/Activity;)V
                """.trimIndent(),
            )
        }
    }
}
