package com.example.arabicvoiceaccess

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import java.util.Locale
import kotlin.math.abs

class ReaderAccessibilityService : AccessibilityService(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var wm: WindowManager? = null
    private var penView: PenView? = null
    private var penParams: WindowManager.LayoutParams? = null
    private var captureView: CaptureView? = null
    private var penMode = false
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        @Volatile var instance: ReaderAccessibilityService? = null
    }

    // ---------------------------------------------------------------- lifecycle

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        tts = TextToSpeech(this, this)
        showPen()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true
            val r = tts?.setLanguage(Locale.forLanguageTag("ar"))
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(this, "الصوت العربي غير مثبّت في محرك الكلام بالهاتف", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Nothing to do: reading is triggered only by the floating pen.
    }

    override fun onInterrupt() {
        tts?.stop()
    }

    override fun onDestroy() {
        hidePen()
        instance = null
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- floating pen

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    fun showPen() {
        if (penView != null) return
        val manager = wm ?: return
        val v = PenView(this)
        val size = dp(56)
        val p = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.START
        p.x = dp(8)
        p.y = dp(220)
        penParams = p
        penView = v
        attachTouch(v, p)
        try {
            manager.addView(v, p)
        } catch (e: Exception) {
            penView = null
        }
    }

    fun hidePen() {
        exitPenMode()
        val manager = wm
        penView?.let { v ->
            try { manager?.removeView(v) } catch (_: Exception) { }
        }
        penView = null
    }

    private fun attachTouch(v: PenView, p: WindowManager.LayoutParams) {
        var downX = 0f
        var downY = 0f
        var startPX = 0
        var startPY = 0
        var moved = false
        var longFired = false
        val slop = dp(8)
        val longRun = Runnable {
            longFired = true
            readWholeScreen()
        }
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startPX = p.x
                    startPY = p.y
                    moved = false
                    longFired = false
                    handler.postDelayed(longRun, 700)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!moved && (abs(dx) > slop || abs(dy) > slop)) {
                        moved = true
                        handler.removeCallbacks(longRun)
                    }
                    if (moved) {
                        p.x = startPX + dx.toInt()
                        p.y = startPY + dy.toInt()
                        try { wm?.updateViewLayout(v, p) } catch (_: Exception) { }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longRun)
                    if (!moved && !longFired) handler.post { togglePenMode() }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longRun)
                    true
                }
                else -> false
            }
        }
    }

    private fun togglePenMode() {
        if (penMode) {
            exitPenMode()
        } else {
            enterPenMode()
        }
    }

    private fun enterPenMode() {
        val manager = wm ?: return
        if (penMode) return
        val c = CaptureView(this)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        c.onTap = { x, y -> readAt(x, y) }
        try {
            manager.addView(c, lp)
        } catch (e: Exception) {
            return
        }
        captureView = c
        penMode = true
        // Put the pen back on top of the capture layer so it stays tappable.
        val pv = penView
        val pp = penParams
        if (pv != null && pp != null) {
            try {
                manager.removeView(pv)
                manager.addView(pv, pp)
            } catch (_: Exception) { }
            pv.active = true
            pv.invalidate()
        }
        speak("اضغط على النص")
    }

    private fun exitPenMode() {
        penMode = false
        captureView?.let { c ->
            try { wm?.removeView(c) } catch (_: Exception) { }
        }
        captureView = null
        penView?.let {
            it.active = false
            it.invalidate()
        }
        tts?.stop()
    }

    // ---------------------------------------------------------------- reading

    /** Kept for compatibility with the old notification receiver. */
    fun readNow() {
        handler.postDelayed({ readWholeScreen() }, 700)
    }

    private fun appRoots(): List<AccessibilityNodeInfo> {
        val list = ArrayList<AccessibilityNodeInfo>()
        try {
            for (w in windows) {
                if (w.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                    val r = w.root
                    if (r != null) list.add(r)
                }
            }
        } catch (_: Exception) { }
        if (list.isEmpty()) {
            val r = rootInActiveWindow
            if (r != null) list.add(r)
        }
        return list
    }

    private fun nodeText(n: AccessibilityNodeInfo): String {
        val t = n.text?.toString()?.trim().orEmpty()
        if (t.isNotBlank()) return t
        return n.contentDescription?.toString()?.trim().orEmpty()
    }

    private fun collectText(node: AccessibilityNodeInfo?, out: MutableList<String>, depth: Int) {
        if (node == null || out.size >= 300 || depth > 60) return
        if (!node.isVisibleToUser) return
        val t = nodeText(node)
        if (t.isNotBlank() && !out.contains(t)) out.add(t)
        for (i in 0 until node.childCount) {
            collectText(node.getChild(i), out, depth + 1)
        }
    }

    private fun readWholeScreen() {
        val parts = ArrayList<String>()
        for (root in appRoots()) collectText(root, parts, 0)
        val text = parts.joinToString(". ")
        if (text.isBlank()) {
            speak("لم أجد نصاً على الشاشة")
        } else {
            speak(text)
        }
    }

    private fun findDeepest(node: AccessibilityNodeInfo?, x: Int, y: Int, depth: Int): AccessibilityNodeInfo? {
        if (node == null || depth > 60 || !node.isVisibleToUser) return null
        val r = Rect()
        node.getBoundsInScreen(r)
        if (!r.contains(x, y)) return null
        for (i in node.childCount - 1 downTo 0) {
            val hit = findDeepest(node.getChild(i), x, y, depth + 1)
            if (hit != null) return hit
        }
        return node
    }

    private fun readAt(x: Int, y: Int) {
        var hit: AccessibilityNodeInfo? = null
        for (root in appRoots()) {
            hit = findDeepest(root, x, y, 0)
            if (hit != null) break
        }
        var node = hit
        var level = 0
        var text = ""
        val bounds = Rect()
        while (node != null && level < 4) {
            val own = nodeText(node)
            if (own.isNotBlank()) {
                text = own
            } else {
                val parts = ArrayList<String>()
                collectText(node, parts, 0)
                text = parts.joinToString(". ").take(4000)
            }
            if (text.isNotBlank()) {
                node.getBoundsInScreen(bounds)
                break
            }
            node = node.parent
            level++
        }
        if (text.isBlank()) {
            speak("لا يوجد نص هنا")
        } else {
            captureView?.showHighlight(Rect(bounds))
            speak(text)
        }
    }

    private fun speak(text: String) {
        val clean = text.trim().take(20000)
        if (clean.isEmpty()) return
        val engine = tts
        if (!ttsReady || engine == null) {
            Toast.makeText(this, "محرك الصوت غير جاهز بعد", Toast.LENGTH_SHORT).show()
            return
        }
        val max = 3000
        var i = 0
        var first = true
        while (i < clean.length) {
            var end = minOf(i + max, clean.length)
            if (end < clean.length) {
                val cut = clean.lastIndexOfAny(charArrayOf('.', '،', '\n', ' '), end)
                if (cut > i) end = cut + 1
            }
            engine.speak(
                clean.substring(i, end),
                if (first) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
                null,
                "reader-$i"
            )
            first = false
            i = end
        }
    }

    // ---------------------------------------------------------------- views

    /** The floating round button with a pencil drawn on it. */
    private class PenView(context: Context) : View(context) {
        var active = false
        private val circle = Paint(Paint.ANTI_ALIAS_FLAG)
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(c: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            circle.color = if (active) Color.rgb(255, 111, 0) else Color.rgb(24, 68, 120)
            c.drawCircle(w / 2f, h / 2f, w / 2f - 2f, circle)

            c.save()
            c.rotate(45f, w / 2f, h / 2f)
            val u = w / 56f
            c.scale(u, u)

            paint.color = Color.rgb(229, 115, 115) // eraser
            c.drawRect(24f, 10f, 32f, 14f, paint)
            paint.color = Color.rgb(189, 189, 189) // metal band
            c.drawRect(24f, 14f, 32f, 16f, paint)
            paint.color = Color.rgb(255, 193, 7) // body
            c.drawRect(24f, 16f, 32f, 38f, paint)

            val tip = Path()
            tip.moveTo(24f, 38f)
            tip.lineTo(32f, 38f)
            tip.lineTo(28f, 46f)
            tip.close()
            paint.color = Color.rgb(245, 222, 179)
            c.drawPath(tip, paint)

            val point = Path()
            point.moveTo(26.5f, 42.5f)
            point.lineTo(29.5f, 42.5f)
            point.lineTo(28f, 46f)
            point.close()
            paint.color = Color.rgb(51, 51, 51)
            c.drawPath(point, paint)
            c.restore()
        }
    }

    /** Transparent full-screen layer that catches the tap on the text to read. */
    private class CaptureView(context: Context) : View(context) {
        var onTap: ((Int, Int) -> Unit)? = null
        private var highlight: Rect? = null
        private val tint = Paint().apply { color = Color.argb(28, 255, 193, 7) }
        private val fill = Paint().apply { color = Color.argb(70, 255, 193, 7) }
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 111, 0)
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        private val loc = IntArray(2)
        private val clearRun = Runnable {
            highlight = null
            invalidate()
        }

        fun showHighlight(r: Rect) {
            getLocationOnScreen(loc)
            highlight = Rect(r.left - loc[0], r.top - loc[1], r.right - loc[0], r.bottom - loc[1])
            invalidate()
            removeCallbacks(clearRun)
            postDelayed(clearRun, 2000)
        }

        override fun onDraw(c: Canvas) {
            c.drawPaint(tint)
            highlight?.let {
                c.drawRect(it, fill)
                c.drawRect(it, stroke)
            }
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (e.action == MotionEvent.ACTION_UP) {
                onTap?.invoke(e.rawX.toInt(), e.rawY.toInt())
            }
            return true
        }
    }
}
