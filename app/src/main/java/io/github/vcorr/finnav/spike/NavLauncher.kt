package io.github.vcorr.finnav.spike

import android.app.Activity
import android.app.KeyguardManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import io.github.vcorr.finnav.core.NavUri

/** S1: hands a google.navigation: URI to Google Maps, optionally asking to dismiss the keyguard first. */
object NavLauncher {

    fun launch(activity: Activity, uri: String, dismissKeyguardFirst: Boolean, report: (String) -> Unit) {
        val keyguard = activity.getSystemService(KeyguardManager::class.java)
        if (dismissKeyguardFirst && keyguard.isKeyguardLocked) {
            report("Requesting keyguard dismissal")
            keyguard.requestDismissKeyguard(activity, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = fire(activity, uri, report)
                override fun onDismissCancelled() = report("Keyguard dismissal cancelled; Maps not started")
                override fun onDismissError() = report("Keyguard dismissal error; Maps not started")
            })
        } else {
            fire(activity, uri, report)
        }
    }

    private fun fire(activity: Activity, uri: String, report: (String) -> Unit) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            .setPackage(NavUri.GOOGLE_MAPS_PACKAGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            activity.startActivity(intent)
            // A blocked background start is silent; the car screen is the only reliable witness.
            report("Maps intent sent: $uri")
        } catch (e: ActivityNotFoundException) {
            report("Google Maps not installed")
        }
    }
}
