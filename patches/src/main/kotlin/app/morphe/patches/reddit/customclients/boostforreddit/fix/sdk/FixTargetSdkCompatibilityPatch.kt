/*
 * Modifications Copyright 2026 brealorg.
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.sdk

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.reddit.customclients.boostforreddit.BoostCompatible
import app.morphe.patches.reddit.customclients.boostforreddit.misc.extension.sharedExtensionPatch
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import org.w3c.dom.Element
import app.morphe.patches.all.misc.PatchCategory

private const val TARGET_SDK_VERSION = "35"
private const val POST_NOTIFICATIONS_PERMISSION = "android.permission.POST_NOTIFICATIONS"
private const val RECEIVER_COMPAT_DESCRIPTOR =
    "Lapp/morphe/extension/boostforreddit/utils/ReceiverCompat;"

private val setBoostTargetSdk35Patch = resourcePatch(
    name = "Set Boost target SDK 35",
    description = "Sets Boost for Reddit's target SDK to 35.",
    default = false
) {
    compatibleWith(*BoostCompatible)

    execute {
        document("AndroidManifest.xml").use { document ->
            val manifestNode = document.getElementsByTagName("manifest").item(0) as Element
            val usesSdkNodes = document.getElementsByTagName("uses-sdk")

            val hasPostNotificationsPermission =
                document.getElementsByTagName("uses-permission").let { nodes ->
                    (0 until nodes.length).any { index ->
                        val node = nodes.item(index) as? Element
                        node?.getAttribute("android:name") == POST_NOTIFICATIONS_PERMISSION
                    }
                }

            if (!hasPostNotificationsPermission) {
                document.createElement("uses-permission").also { node ->
                    node.setAttribute("android:name", POST_NOTIFICATIONS_PERMISSION)

                    val firstApplicationNode = document.getElementsByTagName("application").item(0)
                    if (firstApplicationNode != null) {
                        manifestNode.insertBefore(node, firstApplicationNode)
                    } else {
                        manifestNode.appendChild(node)
                    }
                }
            }

            val usesSdkNode =
                if (usesSdkNodes.length > 0) {
                    usesSdkNodes.item(0) as Element
                } else {
                    document.createElement("uses-sdk").also { node ->
                        val firstChild = manifestNode.firstChild
                        if (firstChild != null) {
                            manifestNode.insertBefore(node, firstChild)
                        } else {
                            manifestNode.appendChild(node)
                        }
                    }
                }

            usesSdkNode.setAttribute("android:targetSdkVersion", TARGET_SDK_VERSION)
        }
    }
}

@Suppress("unused")
val fixTargetSdk35CompatibilityPatch = bytecodePatch(
    name = "Fix Boost target SDK 35 compatibility",
    description = "Makes Boost target Android 15 (SDK 35) and fixes billing setup on newer Android versions.",
    default = false
) {
    category(PatchCategory.ADVANCED)
    dependsOn(setBoostTargetSdk35Patch, sharedExtensionPatch)
    compatibleWith(*BoostCompatible)

    execute {
        billingClientReceiverRegistrationFingerprint.method.apply {
            val registerReceiverIndex = indexOfFirstInstructionOrThrow {
                opcode == Opcode.INVOKE_VIRTUAL &&
                    getReference<MethodReference>()?.toString() ==
                    "Landroid/content/Context;->registerReceiver(Landroid/content/BroadcastReceiver;Landroid/content/IntentFilter;)Landroid/content/Intent;"
            }

            replaceInstruction(
                registerReceiverIndex,
                """
                    invoke-static {p1, v0, p2}, $RECEIVER_COMPAT_DESCRIPTOR->registerReceiver(Landroid/content/Context;Landroid/content/BroadcastReceiver;Landroid/content/IntentFilter;)Landroid/content/Intent;
                """
            )
        }
    }
}
