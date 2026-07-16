package app.morphe.patches.reddit.customclients.boostforreddit.fix.synccit

import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.stringOption
import app.morphe.patches.reddit.customclients.boostforreddit.BoostCompatible
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

@Suppress("unused")
val customSynccitUrlPatch = bytecodePatch(
    name = "Custom Synccit URL",
    description =
        "Allows Boost to use a custom self-hosted Synccit API endpoint.",
    default = true,
) {
    compatibleWith(*BoostCompatible)

    val synccitUrlOption = stringOption(
        "synccit-url",
        LEGACY_SYNCCIT_API_URL,
        null,
        "Synccit API URL",
        "Full HTTP or HTTPS URL to a Synccit-compatible API endpoint, including api.php.",
        false,
        validator = { value ->
            value != null &&
                value.length in 10..2048 &&
                (
                    value.startsWith("https://") ||
                        value.startsWith("http://")
                ) &&
                '"' !in value &&
                '\n' !in value &&
                '\r' !in value &&
                ' ' !in value
        },
    )

    execute {
        val replacement =
            synccitUrlOption.value
                ?: throw Exception("Missing synccit-url option")

        val fingerprints = listOf(
            "update" to synccitUpdateFingerprint,
            "add" to synccitAddFingerprint,
            "authenticate" to synccitAuthenticateFingerprint,
            "read" to synccitReadFingerprint,
        )

        fingerprints.forEach { (operation, fingerprint) ->
            val matches = fingerprint.stringMatches

            if (matches.size != 1) {
                throw Exception(
                    "Expected one Synccit URL in $operation, found ${matches.size}",
                )
            }

            val match = matches.single()
            val method = fingerprint.method

            val register =
                method
                    .getInstruction<OneRegisterInstruction>(match.index)
                    .registerA

            method.replaceInstruction(
                match.index,
                "const-string v$register, \"$replacement\"",
            )
        }
    }
}
