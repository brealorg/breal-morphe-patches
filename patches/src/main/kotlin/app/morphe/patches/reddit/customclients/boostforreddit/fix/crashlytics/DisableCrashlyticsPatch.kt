/*
 * Modifications Copyright 2026 brealorg.
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.crashlytics

import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.reddit.customclients.boostforreddit.BoostCompatible
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import org.w3c.dom.Element
import app.morphe.patches.all.misc.PatchCategory

private const val CRASHLYTICS_REGISTRAR_META_DATA =
    "com.google.firebase.components:com.google.firebase.crashlytics.CrashlyticsRegistrar"

private const val CRASHLYTICS_GET_INSTANCE_REFERENCE =
    "Lcom/google/firebase/crashlytics/a;->a()Lcom/google/firebase/crashlytics/a;"

private const val CRASHLYTICS_SET_COLLECTION_REFERENCE =
    "Lcom/google/firebase/crashlytics/a;->c(Z)V"

private val removeBoostCrashlyticsRegistrarPatch = resourcePatch(
    name = "Remove Boost Crashlytics registrar",
    description = "Removes Firebase Crashlytics auto-registration while keeping other Firebase components.",
    default = true
) {
    compatibleWith(*BoostCompatible)

    execute {
        document("AndroidManifest.xml").use { document ->
            val metaDataNodes = document.getElementsByTagName("meta-data")
            val matches = (0 until metaDataNodes.length)
                .map { metaDataNodes.item(it) as Element }
                .filter { it.getAttribute("android:name") == CRASHLYTICS_REGISTRAR_META_DATA }

            require(matches.size == 1) {
                "Expected exactly one CrashlyticsRegistrar meta-data entry, found ${matches.size}"
            }

            matches.single().parentNode.removeChild(matches.single())
        }
    }
}

@Suppress("unused")
val disableBoostCrashlyticsPatch = bytecodePatch(
    name = "Disable Boost Crashlytics startup network calls",
    description = "Stops Boost's crash reporting (Crashlytics) from connecting at startup. Other Firebase services keep working.",
    default = true
) {
    category(PatchCategory.ADS)
    dependsOn(removeBoostCrashlyticsRegistrarPatch)
    compatibleWith(*BoostCompatible)

    execute {
        boostApplicationOnCreateFingerprint.method.apply {
            val instructions = implementation!!.instructions

            val getInstanceIndex = indexOfFirstInstructionOrThrow {
                opcode == Opcode.INVOKE_STATIC &&
                    getReference<MethodReference>()?.toString() == CRASHLYTICS_GET_INSTANCE_REFERENCE
            }

            val setCollectionIndex = instructions.withIndex().first { (index, instruction) ->
                index > getInstanceIndex &&
                    instruction.opcode == Opcode.INVOKE_VIRTUAL &&
                    instruction.getReference<MethodReference>()?.toString() == CRASHLYTICS_SET_COLLECTION_REFERENCE
            }.index

            removeInstructions(
                getInstanceIndex,
                setCollectionIndex - getInstanceIndex + 1
            )
        }
    }
}
