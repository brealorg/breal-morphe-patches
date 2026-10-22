package app.morphe.patches.reddit.customclients.boostforreddit.fix.medialinks

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.reddit.customclients.boostforreddit.BoostCompatible
import app.morphe.patches.reddit.customclients.boostforreddit.fix.slink.handleNavigationFingerprint
import app.morphe.patches.reddit.customclients.boostforreddit.misc.extension.sharedExtensionPatch
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import org.w3c.dom.Element
import app.morphe.patches.all.misc.PatchCategory

private const val MEDIA_LINKS_DESCRIPTOR =
    "Lapp/morphe/extension/boostforreddit/utils/RedditMediaLinks;"

private const val DEEP_LINKING_ACTIVITY =
    "com.rubenmayayo.reddit.ui.activities.DeepLinkingActivity"

// preview.redd.it is left out: Boost's link opener does not treat those URLs as images and
// sends them to the browser.
private val MEDIA_HOSTS = listOf("i.redd.it")

// Reads the opened URL and passes it to Boost's Reddit link router.
private val deepLinkingRouteFingerprint = Fingerprint(
    definingClass = "Lcom/rubenmayayo/reddit/ui/activities/DeepLinkingActivity;",
    returnType = "V",
    strings = listOf("reddit.app.link", "\$og_redirect"),
)

// Boost's in-app link opener, used for links in post and comment text. It classifies media
// URLs and opens images in Boost's own viewer.
private val openLinkFingerprint = Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "V",
    parameters = listOf("Landroid/content/Context;", "Ljava/lang/String;"),
    strings = listOf("/u/", "/wiki"),
)

private fun Method.smaliReference() =
    "$definingClass->$name(${parameterTypes.joinToString("")})$returnType"

private val openRedditMediaLinksManifestPatch = resourcePatch {
    execute {
        document("AndroidManifest.xml").use { document ->
            val activities = document.getElementsByTagName("activity")
            val activity = (0 until activities.length)
                .map { activities.item(it) as Element }
                .single { it.getAttribute("android:name") == DEEP_LINKING_ACTIVITY }

            // The browsable filter that already lists Boost's v.redd.it host.
            val filters = activity.getElementsByTagName("intent-filter")
            val filter = (0 until filters.length)
                .map { filters.item(it) as Element }
                .first { filter ->
                    val data = filter.getElementsByTagName("data")
                    (0 until data.length).any { (data.item(it) as Element).getAttribute("android:host") == "v.redd.it" }
                }

            val existing = filter.getElementsByTagName("data")
            val existingHosts = (0 until existing.length)
                .map { (existing.item(it) as Element).getAttribute("android:host") }
                .toSet()
            MEDIA_HOSTS.filterNot { it in existingHosts }.forEach { host ->
                listOf("http", "https").forEach { scheme ->
                    filter.appendChild(document.createElement("data").apply {
                        setAttribute("android:scheme", scheme)
                        setAttribute("android:host", host)
                    })
                }
            }
        }
    }
}

@Suppress("unused")
val openRedditMediaLinksPatch = bytecodePatch(
    name = "Open Reddit image links in Boost",
    description = "Opens i.redd.it image links in Boost's image viewer instead of a web page.",
    default = true,
) {
    category(PatchCategory.LINKS)
    dependsOn(sharedExtensionPatch, openRedditMediaLinksManifestPatch)
    compatibleWith(*BoostCompatible)

    execute {
        val routerReference = handleNavigationFingerprint.method.smaliReference()
        val openLinkReference = openLinkFingerprint.method.smaliReference()

        deepLinkingRouteFingerprint.method.apply {
            val routerIndex = indexOfFirstInstructionOrThrow {
                opcode == Opcode.INVOKE_STATIC && getReference<MethodReference>()?.toString() == routerReference
            }
            val router = getInstruction<FiveRegisterInstruction>(routerIndex)
            val contextRegister = router.registerC
            val urlRegister = router.registerD
            val flagRegister = router.registerE
            // Earlier branches jump to the flag constant right before the router call, so the
            // hook goes between the constant and the call and restores the flag afterwards.
            val flagInstruction = getInstruction<NarrowLiteralInstruction>(routerIndex - 1)
            check(flagInstruction.opcode == Opcode.CONST_4) { "Unexpected instruction before Boost's link router" }

            addInstructionsWithLabels(
                routerIndex,
                """
                    invoke-static {v$urlRegister}, $MEDIA_LINKS_DESCRIPTOR->isRedditMediaLink(Ljava/lang/String;)Z
                    move-result v$flagRegister
                    if-eqz v$flagRegister, :restore_flag
                    invoke-static {v$contextRegister, v$urlRegister}, $openLinkReference
                    return-void
                    :restore_flag
                    const/4 v$flagRegister, ${flagInstruction.narrowLiteral}
                """.trimIndent(),
            )
        }
    }
}
