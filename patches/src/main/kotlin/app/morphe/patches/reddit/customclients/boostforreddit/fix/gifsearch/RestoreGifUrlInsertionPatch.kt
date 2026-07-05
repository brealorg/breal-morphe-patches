package app.morphe.patches.reddit.customclients.boostforreddit.fix.gifsearch

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.boostforreddit.BoostCompatible

@Suppress("unused")
val restoreGifUrlInsertionPatch = bytecodePatch(
    name = "Restore GIF URL insertion",
    description = "Adds provider-independent manual GIF URL insertion to Boost's existing image menu.",
    default = false
) {
    compatibleWith(*BoostCompatible)

    execute {
        val imageMenuMethod = formattingBarImageMenuOptionsFingerprint.method
        val imageMenuReturnIndex = imageMenuMethod.implementation!!.instructions.lastIndex

        imageMenuMethod.addInstructions(
            imageMenuReturnIndex,
            """
                new-instance v1, Lcom/rubenmayayo/reddit/ui/customviews/menu/MenuOption;
                invoke-direct {v1}, Lcom/rubenmayayo/reddit/ui/customviews/menu/MenuOption;-><init>()V
                const v2, 0x7f0a029c
                invoke-virtual {v1, v2}, Lcom/rubenmayayo/reddit/ui/customviews/menu/MenuOption;->d0(I)Lcom/rubenmayayo/reddit/ui/customviews/menu/MenuOption;
                move-result-object v1
                const v2, 0x7f1306f8
                invoke-virtual {v1, v2}, Lcom/rubenmayayo/reddit/ui/customviews/menu/MenuOption;->i0(I)Lcom/rubenmayayo/reddit/ui/customviews/menu/MenuOption;
                move-result-object v1
                const v2, 0x7f08026c
                invoke-virtual {v1, v2}, Lcom/rubenmayayo/reddit/ui/customviews/menu/MenuOption;->a0(I)Lcom/rubenmayayo/reddit/ui/customviews/menu/MenuOption;
                move-result-object v1
                invoke-interface {v0, v1}, Ljava/util/List;->add(Ljava/lang/Object;)Z
            """
        )

        val menuHandlerMethod = formattingBarHandleImageMenuOptionFingerprint.method
        menuHandlerMethod.addInstructions(
            0,
            """
                invoke-static {p0, p1}, Lapp/morphe/extension/boostforreddit/gifsearch/GifUrlInsertion;->handleImageMenuOption(Ljava/lang/Object;Ljava/lang/Object;)V
                return-void
            """
        )
    }
}
