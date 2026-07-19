/*
 * Modifications Copyright 2026 brealorg.
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.homefab

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patches.reddit.customclients.boostforreddit.BoostCompatible
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

private const val FAB_NESTED_SCROLL_FALLBACK_MARKER =
    "MORPHE_BOOST_FAB_NESTED_SCROLL_FALLBACK_ISSUE97_V1"
private const val FAB_NESTED_PRE_SCROLL_MARKER =
    "MORPHE_BOOST_FAB_NESTED_PRE_SCROLL_ISSUE97_V2"
private const val FAB_MENU_NESTED_SCROLL_FALLBACK_MARKER =
    "MORPHE_BOOST_FAB_MENU_NESTED_SCROLL_FALLBACK_ISSUE97_V3"
private const val FAB_MENU_NESTED_PRE_SCROLL_MARKER =
    "MORPHE_BOOST_FAB_MENU_NESTED_PRE_SCROLL_ISSUE97_V3"
private const val FAB_MINI_NESTED_SCROLL_FALLBACK_MARKER =
    "MORPHE_BOOST_FAB_MINI_NESTED_SCROLL_FALLBACK_ISSUE97_V3"
private const val FAB_MINI_NESTED_PRE_SCROLL_MARKER =
    "MORPHE_BOOST_FAB_MINI_NESTED_PRE_SCROLL_ISSUE97_V3"

private const val COORDINATOR_LAYOUT =
    "Landroidx/coordinatorlayout/widget/CoordinatorLayout;"
private const val VIEW = "Landroid/view/View;"
private const val FLOATING_ACTION_BUTTON =
    "Lcom/google/android/material/floatingactionbutton/FloatingActionButton;"
private const val FLOATING_ACTION_MENU =
    "Lcom/rubenmayayo/reddit/ui/customviews/fab/FloatingActionMenu;"
private const val COORDINATOR_BEHAVIOR =
    "Landroidx/coordinatorlayout/widget/CoordinatorLayout\$c;"

private data class ScrollAwareTarget(
    val fingerprint: Fingerprint,
    val childType: String,
    val nestedScrollMethodName: String,
    val nestedScrollMarker: String,
    val preScrollMarker: String,
)

@Suppress("unused")
val fixFabNestedScrollFallbackPatch = bytecodePatch(
    name = "Fix Boost FAB nested scroll",
    description =
        "Synchronizes Boost FAB hide/show with collapsing-header and nested-scroll motion.",
    default = true,
) {
    compatibleWith(*BoostCompatible)

    execute {
        val preScrollParameterTypes = listOf(
            COORDINATOR_LAYOUT,
            VIEW,
            VIEW,
            "I",
            "I",
            "[I",
            "I",
        )

        val scrollAwareTargets = listOf(
            ScrollAwareTarget(
                fabNestedScrollFingerprint,
                FLOATING_ACTION_BUTTON,
                "M",
                FAB_NESTED_SCROLL_FALLBACK_MARKER,
                FAB_NESTED_PRE_SCROLL_MARKER,
            ),
            ScrollAwareTarget(
                fabMenuNestedScrollFingerprint,
                FLOATING_ACTION_MENU,
                "H",
                FAB_MENU_NESTED_SCROLL_FALLBACK_MARKER,
                FAB_MENU_NESTED_PRE_SCROLL_MARKER,
            ),
            ScrollAwareTarget(
                fabMiniNestedScrollFingerprint,
                FLOATING_ACTION_BUTTON,
                "H",
                FAB_MINI_NESTED_SCROLL_FALLBACK_MARKER,
                FAB_MINI_NESTED_PRE_SCROLL_MARKER,
            ),
        )

        scrollAwareTargets.forEach { target ->
            target.fingerprint.method.apply {
                val nestedScrollSuperIndex = indexOfFirstInstructionOrThrow {
                    opcode == Opcode.INVOKE_SUPER_RANGE
                }

                // All three native Boost implementations only react to
                // dyConsumed (p5). At a scroll edge, the return delta may be
                // reported as dyUnconsumed (p7), so use it only when p5 is 0.
                addInstructionsWithLabels(
                    nestedScrollSuperIndex + 1,
                    """
                        if-nez p5, :morphe_fab_scroll_delta_ready
                        move p5, p7
                        :morphe_fab_scroll_delta_ready
                        const-string p3, "${target.nestedScrollMarker}"
                    """,
                )
            }

            val behaviorClass = target.fingerprint.classDef
            check(
                behaviorClass.methods.none { method ->
                    method.name == "q" &&
                        method.returnType == "V" &&
                        method.parameters.map { it.type } == preScrollParameterTypes
                },
            ) {
                "Boost pre-scroll override already exists for ${behaviorClass.type}"
            }

            behaviorClass.methods.add(
                ImmutableMethod(
                    behaviorClass.type,
                    "q",
                    preScrollParameterTypes.map { type ->
                        ImmutableMethodParameter(type, null, null)
                    },
                    "V",
                    AccessFlags.PUBLIC.value,
                    null,
                    null,
                    MutableMethodImplementation(8),
                ).toMutable().apply {
                    // Collapsing headers consume motion before nested scroll.
                    // Forward pre-scroll dx/dy to each component's own native
                    // implementation so its original animation/state policy
                    // remains authoritative across every Boost surface.
                    addInstructions(
                        0,
                        """
                            invoke-super/range {p0 .. p7}, $COORDINATOR_BEHAVIOR->q($COORDINATOR_LAYOUT$VIEW${VIEW}II[II)V
                            check-cast p2, ${target.childType}
                            const/4 p6, 0x0
                            const/4 p7, 0x0
                            invoke-virtual/range {p0 .. p7}, ${behaviorClass.type}->${target.nestedScrollMethodName}($COORDINATOR_LAYOUT${target.childType}${VIEW}IIII)V
                            const-string p3, "${target.preScrollMarker}"
                            return-void
                        """,
                    )
                },
            )
        }
    }
}
