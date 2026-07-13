package app.morphe.patches.reddit.customclients.boostforreddit.fix.searchnav
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.boostforreddit.BoostCompatible
import app.morphe.patches.reddit.customclients.boostforreddit.misc.extension.sharedExtensionPatch
import com.android.tools.smali.dexlib2.Opcode

private const val UNIFIED_MATERIAL_NAV_PATCH_MARKER =
    "MORPHE_BOOST_UNIFIED_MATERIAL_BOTTOM_NAV_V771"


private const val INBOX_PROFILE_UNDERLAY_MARKER =
    "MORPHE_BOOST_INBOX_PROFILE_UNDERLAY_LIFECYCLE_V74"

private const val INBOX_PROFILE_EXTENSION_DESCRIPTOR =
    "Lapp/morphe/extension/boostforreddit/utils/BoostSearchBottomNavigation;"

private val messagesActivityUnderlayLifecycleFingerprint = Fingerprint(
    returnType = "V",
    parameters = emptyList(),
    custom = { method, classDef ->
        classDef.type ==
            "Lcom/rubenmayayo/reddit/ui/messages/MessagesActivity;" &&
            method.name == "onResume"
    },
)

private val userActivityUnderlayLifecycleFingerprint = Fingerprint(
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
    custom = { method, classDef ->
        classDef.type ==
            "Lcom/rubenmayayo/reddit/ui/profile/UserActivity;" &&
            method.name == "onCreate"
    },
)

@Suppress("unused")
val boostInboxProfileSystemUnderlayPatch = bytecodePatch(
    name = "Standardize Boost Inbox and Profile bottom navigation",
    description = "Applies the canonical five-destination Material bottom-navigation contract after Inbox and Profile complete lifecycle setup.",
    default = true,
) {
    compatibleWith(*BoostCompatible)
    dependsOn(sharedExtensionPatch)

    execute {
        check(
            UNIFIED_MATERIAL_NAV_PATCH_MARKER.startsWith(
                "MORPHE_BOOST_UNIFIED_MATERIAL_"
            )
        )

        check(
            INBOX_PROFILE_UNDERLAY_MARKER ==
                "MORPHE_BOOST_INBOX_PROFILE_UNDERLAY_LIFECYCLE_V74"
        )

        messagesActivityUnderlayLifecycleFingerprint.method.apply {
            val returnIndices =
                implementation!!.instructions
                    .withIndex()
                    .filter { (_, instruction) ->
                        instruction.opcode == Opcode.RETURN_VOID
                    }
                    .map { (index, _) -> index }

            require(returnIndices.isNotEmpty()) {
                "MessagesActivity.onResume has no RETURN_VOID instruction"
            }

            returnIndices.asReversed().forEach { returnIndex ->
                addInstructions(
                    returnIndex,
                    """
                        invoke-static {p0}, $INBOX_PROFILE_EXTENSION_DESCRIPTOR->standardizeInbox(Landroid/app/Activity;)V
                    """.trimIndent(),
                )
            }
        }

        userActivityUnderlayLifecycleFingerprint.method.apply {
            val returnIndices =
                implementation!!.instructions
                    .withIndex()
                    .filter { (_, instruction) ->
                        instruction.opcode == Opcode.RETURN_VOID
                    }
                    .map { (index, _) -> index }

            require(returnIndices.isNotEmpty()) {
                "UserActivity.onCreate has no RETURN_VOID instruction"
            }

            returnIndices.asReversed().forEach { returnIndex ->
                addInstructions(
                    returnIndex,
                    """
                        invoke-static {p0}, $INBOX_PROFILE_EXTENSION_DESCRIPTOR->standardizeProfile(Landroid/app/Activity;)V
                    """.trimIndent(),
                )
            }
        }
    }
}
