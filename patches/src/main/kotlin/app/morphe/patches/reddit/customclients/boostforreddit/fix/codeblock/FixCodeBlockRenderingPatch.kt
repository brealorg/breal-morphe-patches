/*
 * Copyright 2026 brealorg.
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.codeblock

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.boostforreddit.BoostCompatible
import app.morphe.patches.reddit.customclients.boostforreddit.misc.extension.sharedExtensionPatch

private const val CODEBLOCK_NORMALIZER_DESCRIPTOR =
    "Lapp/morphe/extension/boostforreddit/codeblock/CodeBlockHtmlNormalizer;"

@Suppress("unused")
val fixCodeBlockRenderingPatch = bytecodePatch(
    name = "Fix Boost code block rendering",
    description = "Preserves Reddit code blocks by normalizing multiline <code> HTML and malformed fenced selftext to Boost\'s native <pre> renderer path.",
    default = true
) {
    dependsOn(sharedExtensionPatch)
    compatibleWith(*BoostCompatible)

    execute {
        boostHtmlSanitizerFingerprint.method.addInstructions(
            0,
            """
                invoke-static {p0}, $CODEBLOCK_NORMALIZER_DESCRIPTOR->normalize(Ljava/lang/String;)Ljava/lang/String;
                move-result-object p0
                """
        )

        boostTableTextViewSetTextHtmlFingerprint.method.addInstructions(
            0,
            """
                invoke-static {p1}, $CODEBLOCK_NORMALIZER_DESCRIPTOR->normalize(Ljava/lang/String;)Ljava/lang/String;
                move-result-object p1
                """
        )

        boostLinkTextViewSetTextHtmlParsedFingerprint.method.addInstructions(
            0,
            """
                invoke-static {p1}, $CODEBLOCK_NORMALIZER_DESCRIPTOR->normalize(Ljava/lang/String;)Ljava/lang/String;
                move-result-object p1
            """,
        )

        boostSubmissionViewHolderSelfPreviewHtmlFingerprint.method.addInstructions(
            7,
            """
                invoke-static {p1}, $CODEBLOCK_NORMALIZER_DESCRIPTOR->normalize(Ljava/lang/String;)Ljava/lang/String;
                move-result-object p1
            """,
        )


    }
}
