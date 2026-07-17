/*
 * Modifications Copyright 2026 brealorg.
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.gallery

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.boostforreddit.BoostCompatible
import app.morphe.patches.reddit.customclients.boostforreddit.misc.extension.sharedExtensionPatch
import app.morphe.util.addInstructionsAtControlFlowLabel
import com.android.tools.smali.dexlib2.Opcode

private const val GALLERY_ACTIVITY_DESCRIPTOR =
    "Lcom/rubenmayayo/reddit/ui/activities/GalleryActivity;"

private const val GALLERY_PAGE_LISTENER_DESCRIPTOR =
    "Lcom/rubenmayayo/reddit/ui/activities/GalleryActivity\$b;"

private const val GALLERY_PAGE_ADAPTER_DESCRIPTOR =
    "Lcom/rubenmayayo/reddit/ui/activities/GalleryActivity\$c;"

private const val GALLERY_PREVIEW_EXTENSION_DESCRIPTOR =
    "Lapp/morphe/extension/boostforreddit/gallery/GalleryAnimatedPreview;"

@Suppress("unused")
val galleryAnimatedPreviewPatch = bytecodePatch(
    name = "Animate media in Boost gallery previews",
    description = "Autoplays the selected Reddit gallery GIF while preserving Boost's poster, data preferences, and full-screen media route.",
    default = true,
) {
    dependsOn(sharedExtensionPatch)
    compatibleWith(*BoostCompatible)

    execute {
        /*
         * GalleryActivity keeps two pages on either side alive. Its legacy
         * FragmentStatePagerAdapter therefore cannot be used as a reliable
         * RESUMED/PAUSED visibility signal. Hook the authoritative
         * ViewPager.OnPageChangeListener.onPageSelected callback instead.
         */
        galleryPageSelectedFingerprint.method.addInstructions(
            0,
            """
                iget-object v0, p0, $GALLERY_PAGE_LISTENER_DESCRIPTOR->a:$GALLERY_ACTIVITY_DESCRIPTOR
                invoke-static {v0, p1}, $GALLERY_PREVIEW_EXTENSION_DESCRIPTOR->selectPage(Ljava/lang/Object;I)V
            """.trimIndent(),
        )

        /*
         * Do not create a player for an intermediate page while ViewPager is
         * dragging or settling. p0 is otherwise unused by this no-op callback,
         * so it can safely hold the activity without adding a local register.
         */
        galleryPagerStateFingerprint.method.addInstructions(
            0,
            """
                iget-object p0, p0, $GALLERY_PAGE_LISTENER_DESCRIPTOR->a:$GALLERY_ACTIVITY_DESCRIPTOR
                invoke-static {p0, p1}, $GALLERY_PREVIEW_EXTENSION_DESCRIPTOR->setPagerState(Ljava/lang/Object;I)V
            """.trimIndent(),
        )

        /*
         * Record the adapter position before Boost replaces p1 with its
         * ImageModel. Every RETURN_OBJECT then registers the created Fragment
         * against that position. v0 is an existing local in this method.
         */
        galleryPageFactoryFingerprint.method.apply {
            addInstructions(
                0,
                """
                    iget-object v0, p0, $GALLERY_PAGE_ADAPTER_DESCRIPTOR->j:$GALLERY_ACTIVITY_DESCRIPTOR
                    invoke-static {v0, p1}, $GALLERY_PREVIEW_EXTENSION_DESCRIPTOR->beginPage(Ljava/lang/Object;I)V
                """.trimIndent(),
            )

            val returnIndices = implementation!!.instructions
                .withIndex()
                .mapNotNull { (index, instruction) ->
                    if (instruction.opcode == Opcode.RETURN_OBJECT) index else null
                }

            check(returnIndices.isNotEmpty()) {
                "GalleryActivity page factory has no RETURN_OBJECT"
            }

            returnIndices.asReversed().forEach { index ->
                addInstructionsAtControlFlowLabel(
                    index,
                    """
                        iget-object v0, p0, $GALLERY_PAGE_ADAPTER_DESCRIPTOR->j:$GALLERY_ACTIVITY_DESCRIPTOR
                        invoke-static {v0, p1}, $GALLERY_PREVIEW_EXTENSION_DESCRIPTOR->registerPage(Ljava/lang/Object;Ljava/lang/Object;)V
                    """.trimIndent(),
                )
            }
        }

        imageModelFragmentOnCreateViewFingerprint.method.apply {
            val returnIndices = implementation!!.instructions
                .withIndex()
                .mapNotNull { (index, instruction) ->
                    if (instruction.opcode == Opcode.RETURN_OBJECT) index else null
                }

            check(returnIndices.isNotEmpty()) {
                "ImageModelFragment.onCreateView has no RETURN_OBJECT"
            }

            returnIndices.asReversed().forEach { index ->
                addInstructionsAtControlFlowLabel(
                    index,
                    """
                        invoke-static {p0, p1}, $GALLERY_PREVIEW_EXTENSION_DESCRIPTOR->bindPage(Ljava/lang/Object;Landroid/view/View;)V
                    """.trimIndent(),
                )
            }
        }

        imageModelFragmentOnDestroyFingerprint.method.addInstructions(
            0,
            """
                invoke-static {p0}, $GALLERY_PREVIEW_EXTENSION_DESCRIPTOR->unbindPage(Ljava/lang/Object;)V
            """.trimIndent(),
        )
    }
}
