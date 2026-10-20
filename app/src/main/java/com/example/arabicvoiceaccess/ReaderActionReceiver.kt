package com.example.arabicvoiceaccess

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReaderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == "com.example.arabicvoiceaccess.READ_SCREEN") {
            ReaderAccessibilityService.requestReadScreen = true
            // Accessibility services receive the next event; nudging notification is not required.
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            launch?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (launch != null) context.startActivity(launch)
        }
    }
}
