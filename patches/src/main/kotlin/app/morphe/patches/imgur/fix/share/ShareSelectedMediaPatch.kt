package app.morphe.patches.imgur.fix.share

import app.morphe.patches.imgur.ImgurCompatible
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.stringOption
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val LINK_MODE_RAW_MEDIA = "raw_media"
private const val LINK_MODE_ITEM_PERMALINK = "item_permalink"

@Suppress("unused")
val shareSelectedMediaPatch = bytecodePatch(
    name = "Share selected media",
    description = "Makes Imgur direct media sharing use the selected media item instead of the parent gallery. " +
        "Can share either the raw media/download URL or the Imgur item permalink.",
    default = true
) {
    compatibleWith(*ImgurCompatible)

    val linkModeOption = stringOption(
        "link-mode",
        LINK_MODE_RAW_MEDIA,
        null,
        "Link mode",
        "raw_media shares the direct media/download URL. " +
            "item_permalink shares the Imgur gallery URL with the selected media fragment.",
        false,
        validator = { value ->
            value == LINK_MODE_RAW_MEDIA || value == LINK_MODE_ITEM_PERMALINK
        }
    )

    execute {
        val method = shareDirectImageLinkFingerprint.method

        /*
         * Method parameters:
         * p2 = parentPostTitle
         * p3 = parentPostUrl / shareText candidate
         * p5 = imageId
         * p6 -> v5 = copyImageUrl / item permalink
         * p7 -> v6 = downloadImageUrl / raw media
         *
         * The method performs six Intrinsics.checkNotNullParameter calls at the start:
         * context, imageId, copyImageUrl, downloadImageUrl, shareSourceType, shareContentType.
         *
         * Inject after the sixth one, before it starts checking p2/p3 and building
         * "title + postUrl".
         */
        var seenCheckNotNullParameter = 0
        val insertIndex = method.indexOfFirstInstructionOrThrow {
            val reference = getReference<MethodReference>() ?: return@indexOfFirstInstructionOrThrow false

            val matches = reference.definingClass == "Lkotlin/jvm/internal/Intrinsics;" &&
                reference.name == "checkNotNullParameter" &&
                reference.parameterTypes.size == 2

            if (matches) {
                seenCheckNotNullParameter++
            }

            matches && seenCheckNotNullParameter == 6
        } + 1

        val sourceRegister = when (linkModeOption.value) {
            LINK_MODE_RAW_MEDIA -> "v6"
            LINK_MODE_ITEM_PERMALINK -> "v5"
            else -> throw Exception("Invalid link-mode option: ${linkModeOption.value}")
        }

        method.addInstructions(
            insertIndex,
            """
                # breal patch: direct media share should not use parent post title/url.
                # p2 = parentPostTitle, p3 = shareText.
                # v5 = copyImageUrl/item permalink, v6 = downloadImageUrl/raw media.
                const/4 v0, 0x0
                move-object p2, v0
                move-object p3, $sourceRegister
            """
        )
    }
}
