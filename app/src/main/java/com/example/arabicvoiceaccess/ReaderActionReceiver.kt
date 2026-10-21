package com.example.arabicvoiceaccess

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReaderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == "com.example.arabicvoiceaccess.READ_SCREEN") {
            ReaderAccessibilityService.instance?.readNow()
        }
    }
}
