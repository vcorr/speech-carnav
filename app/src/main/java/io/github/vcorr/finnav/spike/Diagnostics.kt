package io.github.vcorr.finnav.spike

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import io.github.vcorr.finnav.core.NavUri

/** Read-only facts about the device, for S2 (recognisers), S4 (launch context) and S5 (audio routing). */
object Diagnostics {

    fun recognitionServices(context: Context): List<ComponentName> =
        context.packageManager
            .queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), PackageManager.ResolveInfoFlags.of(0L))
            .map { ComponentName(it.serviceInfo.packageName, it.serviceInfo.name) }

    /** The user-selected default recogniser. The key is not public API, so it may read as null. */
    fun defaultRecogniser(context: Context): String? =
        runCatching { Settings.Secure.getString(context.contentResolver, "voice_recognition_service") }.getOrNull()

    fun report(context: Context): List<String> {
        val pm = context.packageManager
        val mapsVersion = runCatching {
            pm.getPackageInfo(NavUri.GOOGLE_MAPS_PACKAGE, PackageManager.PackageInfoFlags.of(0L)).versionName
        }.getOrNull()
        return buildList {
            add("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            add("Recognition available: ${SpeechRecognizer.isRecognitionAvailable(context)}, " +
                "on-device available: ${SpeechRecognizer.isOnDeviceRecognitionAvailable(context)}")
            add("Default recogniser: ${defaultRecogniser(context) ?: "(unreadable)"}")
            recognitionServices(context).forEach { add("Recognition service: ${it.flattenToShortString()}") }
            add("Google Maps: ${mapsVersion ?: "NOT INSTALLED"}")
            add("Draw over other apps: ${Settings.canDrawOverlays(context)}")
            add("Audio inputs: ${devices(context, AudioManager.GET_DEVICES_INPUTS)}")
            add("Audio outputs: ${devices(context, AudioManager.GET_DEVICES_OUTPUTS)}")
        }
    }

    /** S5: which input device the recogniser is actually recording from, if the system reveals it. */
    fun activeRecordingDevices(context: Context): String {
        val am = context.getSystemService(AudioManager::class.java)
        val devices = am.activeRecordingConfigurations.mapNotNull { it.audioDevice }
        return if (devices.isEmpty()) "(none visible)" else devices.joinToString(", ") { describe(it) }
    }

    /** S4: how and by whom the activity was opened. */
    fun launchContext(activity: Activity, intent: Intent?): String {
        val component = intent?.component?.className?.substringAfterLast('.') ?: "?"
        val secure = intent?.getBooleanExtra(RecognizerIntent.EXTRA_SECURE, false) ?: false
        return "action=${intent?.action} component=$component referrer=${activity.referrer} secureExtra=$secure"
    }

    /** Short trigger label for the run log. */
    fun trigger(intent: Intent?): String = when (intent?.action) {
        RecognizerIntent.ACTION_VOICE_SEARCH_HANDS_FREE -> "hands-free"
        Intent.ACTION_MAIN -> "launcher:" + (intent.component?.className?.substringAfterLast('.') ?: "?")
        else -> intent?.action ?: "none"
    }

    private fun devices(context: Context, flags: Int): String =
        context.getSystemService(AudioManager::class.java).getDevices(flags).joinToString(", ") { describe(it) }

    private fun describe(d: AudioDeviceInfo): String = "${typeName(d.type)}(${d.productName})"

    private fun typeName(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "builtin-mic"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "builtin-speaker"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "earpiece"
        AudioDeviceInfo.TYPE_TELEPHONY -> "telephony"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "bt-sco"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "bt-a2dp"
        AudioDeviceInfo.TYPE_BLE_HEADSET -> "ble-headset"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "usb-device"
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> "usb-accessory"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "wired-headset"
        AudioDeviceInfo.TYPE_REMOTE_SUBMIX -> "remote-submix"
        AudioDeviceInfo.TYPE_BUS -> "bus"
        else -> "type$type"
    }
}
