package app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.user

import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.fingerprint
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.sync.SyncForRedditCompatible
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import app.morphe.patches.all.misc.PatchCategory

@Suppress("unused")
val useUserEndpointPatch = bytecodePatch(
    name = "Use /user/ endpoint",
    description = "Opens user profiles through /user/ instead of the deprecated /u/ endpoint.",
    default = true,
) {
    category(PatchCategory.LOGIN)
    compatibleWith(*SyncForRedditCompatible)

    execute {
        userEndpointFingerprints.forEach { fingerprint ->
            fingerprint.matchAll().forEach { match ->
                val stringIndex = match.stringMatches.first().index

                val userPathStringInstruction = match.method.getInstruction<OneRegisterInstruction>(stringIndex)

                val userPathStringRegister = userPathStringInstruction.registerA
                val fixedUserPathString = userPathStringInstruction.getReference<StringReference>()!!
                    .string.replace("u/", "user/")

                match.method.replaceInstruction(
                    stringIndex,
                    "const-string v$userPathStringRegister, \"${fixedUserPathString}\"",
                )
            }
        }
    }
}
