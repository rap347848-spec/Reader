package com.example.arabicvoiceaccess

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

class ReaderAccessibilityService : AccessibilityService(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var lastSpoken = ""
    private var lastSelection = ""
    private var lastScreenReadAt = 0L

    companion object {
        @Volatile var requestReadScreen = false
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        tts = TextToSpeech(this, this)
        createReaderNotification()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("ar"))
            ttsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !ttsReady) return

        // Speak text when Android reports a selected text range.
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_SELECTED) {
            val selected = event.text?.joinToString(" ")?.trim().orEmpty()
            if (selected.length >= 2 && selected != lastSelection) {
                lastSelection = selected
                speak(selected)
                return
            }
        }

        // Screen reading is intentionally user-triggered from the persistent notification.
        if (requestReadScreen) {
            requestReadScreen = false
            val now = System.currentTimeMillis()
            if (now - lastScreenReadAt > 1500) {
                lastScreenReadAt = now
                val collected = collectVisibleText(rootInActiveWindow)
                if (collected.isNotBlank()) speak(collected)
            }
        }
    }

    private fun collectVisibleText(root: AccessibilityNodeInfo?): String {
        if (root == null) return ""
        val parts = ArrayList<String>()
        fun walk(node: AccessibilityNodeInfo?) {
            if (node == null || parts.size >= 180) return
            val value = node.text?.toString()?.trim()
            if (!value.isNullOrBlank() && value.length > 1 && !parts.contains(value)) {
                parts.add(value)
            }
            for (i in 0 until node.childCount) walk(node.getChild(i))
        }
        walk(root)
        return parts.joinToString(". ").take(12000)
    }

    private fun speak(text: String) {
        val clean = text.trim().take(12000)
        if (clean.isBlank() || clean == lastSpoken) return
        lastSpoken = clean
        tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "screen-reader")
    }

    private fun createReaderNotification() {
        val channelId = "reader_controls"
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "أزرار قارئ الشاشة", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val readIntent = android.content.Intent(this, ReaderActionReceiver::class.java).apply {
            action = "com.example.arabicvoiceaccess.READ_SCREEN"
        }
        val pending = android.app.PendingIntent.getBroadcast(
            this, 17, readIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, channelId) else Notification.Builder(this)
        val notification = builder
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("قارئ الشاشة الصوتي")
            .setContentText("اضغط «قراءة الشاشة» لقراءة النص الظاهر في التطبيق المفتوح")
            .addAction(Notification.Action.Builder(null, "قراءة الشاشة", pending).build())
            .setOngoing(true)
            .build()
        manager.notify(1701, notification)
    }

    override fun onInterrupt() {
        tts?.stop()
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
