package app.morphe.patches.reddit.customclients.boostforreddit.fix.gifsearch

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.boostforreddit.BoostCompatible

@Suppress("unused")
val supportKeyboardGifInsertionPatch = bytecodePatch(
    name = "Support keyboard GIF insertion",
    description = "Enables keyboard GIF/image rich content insertion through Android receive-content when a public URL is available.",
    default = false
) {
    compatibleWith(*BoostCompatible)

    execute {
        formattingBarSetEditTextFingerprint.method.addInstructions(
            0,
            """
                invoke-static {p1}, Lapp/morphe/extension/boostforreddit/gifsearch/ImeGifInputConnection;->enableForEditText(Landroid/widget/EditText;)V
            """.trimIndent()
        )
    }
}
