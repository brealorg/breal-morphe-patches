/*
 * Modifications Copyright 2026 brealorg.
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.media

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.boostforreddit.BoostCompatible
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val CONTRACT_MARKER =
    "MORPHE_BOOST_VIDEO_REWIND_BACKBUFFER_ISSUE188_V1"

// Boost 1.12.12 preserves the ExoPlayer source identity DefaultLoadControl.java
// at Ls3/c;. Static discovery and the issue contract prove that:
//   b()Z returns retainBackBufferFromKeyframe
//   c()J returns backBufferDurationUs
private val retainBackBufferFromKeyframeFingerprint = Fingerprint(
    definingClass = "Ls3/c;",
    name = "b",
    returnType = "Z",
    parameters = emptyList(),
)

private val backBufferDurationUsFingerprint = Fingerprint(
    definingClass = "Ls3/c;",
    name = "c",
    returnType = "J",
    parameters = emptyList(),
)

@Suppress("unused")
val fixBoostVideoRewindBackBufferPatch = bytecodePatch(
    name = "Fix Boost video rewind back buffer",
    description =
        "Retains 30 seconds of played video in ExoPlayer so backward seeks can reuse recently played media instead of reloading it.",
    default = true,
) {
    compatibleWith(*BoostCompatible)

    execute {
        check(CONTRACT_MARKER.endsWith("ISSUE188_V1"))

        backBufferDurationUsFingerprint.method.apply {
            val instructions = implementation?.instructions
                ?: error("DefaultLoadControl back-buffer getter has no implementation")

            val returnIndex = instructions.withIndex()
                .singleOrNull { (_, instruction) ->
                    instruction.opcode == Opcode.RETURN_WIDE
                }
                ?.index
                ?: error("Expected exactly one return-wide in DefaultLoadControl.c()")

            val returnRegister =
                (instructions[returnIndex] as? OneRegisterInstruction)?.registerA
                    ?: error("Could not resolve DefaultLoadControl.c() return register")

            // ExoPlayer exposes this getter in microseconds.
            // 30 seconds = 30,000,000 us.
            replaceInstruction(
                returnIndex,
                "const-wide/32 v$returnRegister, 0x1c9c380",
            )
            addInstruction(
                returnIndex + 1,
                "return-wide v$returnRegister",
            )
        }

        retainBackBufferFromKeyframeFingerprint.method.apply {
            val instructions = implementation?.instructions
                ?: error("DefaultLoadControl retain getter has no implementation")

            val returnIndex = instructions.withIndex()
                .singleOrNull { (_, instruction) ->
                    instruction.opcode == Opcode.RETURN
                }
                ?.index
                ?: error("Expected exactly one return in DefaultLoadControl.b()")

            val returnRegister =
                (instructions[returnIndex] as? OneRegisterInstruction)?.registerA
                    ?: error("Could not resolve DefaultLoadControl.b() return register")

            // Keep the decoder-safe start of the retained back buffer.
            replaceInstruction(
                returnIndex,
                "const/4 v$returnRegister, 0x1",
            )
            addInstruction(
                returnIndex + 1,
                "return v$returnRegister",
            )
        }
    }
}
