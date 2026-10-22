package app.morphe.patches.reddit.customclients.boostforreddit.fix.gifsearch

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.boostforreddit.BoostCompatible
import app.morphe.patches.all.misc.PatchCategory

@Suppress("unused")
val supportKeyboardGifInsertionPatch = bytecodePatch(
    name = "Support keyboard GIF insertion",
    description = "Lets you insert GIFs and images from your keyboard while writing, when the keyboard provides a public link.",
    default = false
) {
    category(PatchCategory.COMMENTS)
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
