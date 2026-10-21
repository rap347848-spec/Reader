package com.example.arabicvoiceaccess

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import kotlin.math.abs

class ReaderAccessibilityService : AccessibilityService(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var wm: WindowManager? = null
    private var penView: PenView? = null
    private var penParams: WindowManager.LayoutParams? = null
    private var xView: XView? = null
    private var menuView: MenuLayout? = null
    private var captureView: CaptureView? = null
    private var selectMode = false
    private var penDownAt = 0L
    private var lastMenuHide = 0L
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
        // Reading is triggered only from the floating pen.
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

        // The close target (X) is added first so the pen always stays above it.
        val xv = XView(this)
        val xSize = dp(72)
        val xp = WindowManager.LayoutParams(
            xSize, xSize,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        xp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        xp.y = dp(72)
        xv.visibility = View.INVISIBLE
        try {
            manager.addView(xv, xp)
            xView = xv
        } catch (_: Exception) { }

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
        hideMenu()
        exitSelectMode()
        val manager = wm
        penView?.let { v ->
            try { manager?.removeView(v) } catch (_: Exception) { }
        }
        penView = null
        xView?.let { v ->
            try { manager?.removeView(v) } catch (_: Exception) { }
        }
        xView = null
    }

    private fun attachTouch(v: PenView, p: WindowManager.LayoutParams) {
        var downX = 0f
        var downY = 0f
        var startPX = 0
        var startPY = 0
        var moved = false
        val slop = dp(8)
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    penDownAt = SystemClock.uptimeMillis()
                    downX = e.rawX
                    downY = e.rawY
                    startPX = p.x
                    startPY = p.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!moved && (abs(dx) > slop || abs(dy) > slop)) {
                        moved = true
                        hideMenu()
                        showXTarget()
                    }
                    if (moved) {
                        p.x = startPX + dx.toInt()
                        p.y = startPY + dy.toInt()
                        try { wm?.updateViewLayout(v, p) } catch (_: Exception) { }
                        xView?.setHotState(overX(e.rawX, e.rawY))
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        if (overX(e.rawX, e.rawY)) {
                            handler.post { closePen() }
                        } else {
                            hideXTarget()
                        }
                    } else {
                        val down = penDownAt
                        handler.post { onPenClick(down) }
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    hideXTarget()
                    true
                }
                else -> false
            }
        }
    }

    private fun showXTarget() {
        xView?.let {
            it.setHotState(false)
            it.visibility = View.VISIBLE
        }
    }

    private fun hideXTarget() {
        xView?.visibility = View.INVISIBLE
    }

    private fun overX(rx: Float, ry: Float): Boolean {
        val v = xView ?: return false
        val loc = IntArray(2)
        v.getLocationOnScreen(loc)
        val cx = loc[0] + v.width / 2f
        val cy = loc[1] + v.height / 2f
        val dx = rx - cx
        val dy = ry - cy
        val r = dp(64).toFloat()
        return dx * dx + dy * dy <= r * r
    }

    private fun closePen() {
        hidePen()
        Toast.makeText(this, "تم إغلاق القلم. لإظهاره افتح التطبيق واضغط «إظهار القلم»", Toast.LENGTH_LONG).show()
    }

    private fun onPenClick(downTime: Long) {
        if (selectMode) {
            exitSelectMode()
            return
        }
        if (menuView != null) {
            hideMenu()
            return
        }
        // The same touch just dismissed the menu (tap outside it): do not reopen.
        if (lastMenuHide >= downTime - 80) return
        showMenu()
    }

    private fun updatePenState() {
        penView?.let {
            it.active = (menuView != null || selectMode)
            it.invalidate()
        }
    }

    // ---------------------------------------------------------------- pop-up menu

    private inner class MenuLayout(context: Context) : LinearLayout(context) {
        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
            if (ev.action == MotionEvent.ACTION_OUTSIDE) {
                post { hideMenu() }
                return true
            }
            return super.dispatchTouchEvent(ev)
        }
    }

    private fun makeItem(label: String, onClick: () -> Unit): TextView {
        val t = TextView(this)
        t.text = label
        t.textSize = 15f
        t.setTextColor(Color.WHITE)
        t.gravity = Gravity.CENTER
        t.setPadding(dp(18), dp(12), dp(18), dp(12))
        val bg = GradientDrawable()
        bg.setColor(Color.rgb(24, 68, 120))
        bg.cornerRadius = dp(22).toFloat()
        bg.setStroke(dp(2), Color.WHITE)
        t.background = bg
        t.setOnClickListener { onClick() }
        return t
    }

    private fun showMenu() {
        if (menuView != null) return
        val manager = wm ?: return
        val pp = penParams ?: return
        val m = MenuLayout(this)
        m.orientation = LinearLayout.VERTICAL

        val first = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        val second = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        second.topMargin = dp(8)
        m.addView(makeItem("قراءة كل الشاشة") {
            hideMenu()
            readWholeScreen()
        }, first)
        m.addView(makeItem("تحديد مربع حر") {
            hideMenu()
            enterSelectMode()
        }, second)

        m.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val mw = m.measuredWidth
        val mh = m.measuredHeight
        val dm = resources.displayMetrics
        val pen = dp(56)
        val onLeft = pp.x + pen / 2 < dm.widthPixels / 2
        val x = if (onLeft) pp.x + pen + dp(6) else pp.x - mw - dp(6)
        var y = pp.y + pen / 2 - mh / 2
        val maxY = dm.heightPixels - mh - dp(24)
        if (y > maxY) y = maxY
        if (y < dp(24)) y = dp(24)

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = x
        lp.y = y
        try {
            manager.addView(m, lp)
        } catch (_: Exception) {
            return
        }
        menuView = m
        updatePenState()

        // Pop out from the pen.
        m.pivotX = if (onLeft) 0f else mw.toFloat()
        m.pivotY = mh / 2f
        m.scaleX = 0.3f
        m.scaleY = 0.3f
        m.alpha = 0f
        m.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(160).start()
    }

    private fun hideMenu() {
        val m = menuView ?: return
        menuView = null
        lastMenuHide = SystemClock.uptimeMillis()
        try { wm?.removeView(m) } catch (_: Exception) { }
        updatePenState()
    }

    // ---------------------------------------------------------------- free rectangle selection

    private fun enterSelectMode() {
        val manager = wm ?: return
        if (selectMode) return
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
        c.onRect = { r -> readRect(r) }
        c.onTap = { x, y -> readAt(x, y) }
        try {
            manager.addView(c, lp)
        } catch (_: Exception) {
            return
        }
        captureView = c
        selectMode = true
        // Put the pen back on top of the capture layer so it stays usable.
        val pv = penView
        val pp = penParams
        if (pv != null && pp != null) {
            try {
                manager.removeView(pv)
                manager.addView(pv, pp)
            } catch (_: Exception) { }
        }
        updatePenState()
        speak("حدد مربعاً حول النص")
    }

    private fun exitSelectMode() {
        if (!selectMode && captureView == null) return
        selectMode = false
        captureView?.let { c ->
            try { wm?.removeView(c) } catch (_: Exception) { }
        }
        captureView = null
        tts?.stop()
        updatePenState()
    }

    private fun readRect(r: Rect) {
        val parts = ArrayList<String>()
        for (root in appRoots()) collectInRect(root, r, parts, 0)
        captureView?.showHighlight(r)
        val text = parts.joinToString(". ")
        if (text.isBlank()) {
            speak("لا يوجد نص داخل المربع")
        } else {
            speak(text)
        }
    }

    private fun overlapOk(a: Rect, b: Rect): Boolean {
        val l = maxOf(a.left, b.left)
        val t = maxOf(a.top, b.top)
        val r = minOf(a.right, b.right)
        val bt = minOf(a.bottom, b.bottom)
        if (r <= l || bt <= t) return false
        val inter = (r - l).toLong() * (bt - t)
        val areaA = maxOf(1L, a.width().toLong() * a.height())
        val areaB = maxOf(1L, b.width().toLong() * b.height())
        return inter.toDouble() / minOf(areaA, areaB).toDouble() >= 0.4
    }

    private fun collectInRect(node: AccessibilityNodeInfo?, rect: Rect, out: MutableList<String>, depth: Int) {
        if (node == null || out.size >= 300 || depth > 60 || !node.isVisibleToUser) return
        val b = Rect()
        node.getBoundsInScreen(b)
        if (!Rect.intersects(b, rect)) return
        val before = out.size
        for (i in 0 until node.childCount) {
            collectInRect(node.getChild(i), rect, out, depth + 1)
        }
        if (out.size == before) {
            val t = nodeText(node)
            if (t.isNotBlank() && !out.contains(t) && overlapOk(b, rect)) out.add(t)
        }
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

    /** A simple tap (tiny rectangle) reads the text under the finger. */
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

    /** The round "X" that appears at the bottom-centre while dragging the pen. */
    private class XView(context: Context) : View(context) {
        private var hot = false
        private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = 8f
            strokeCap = Paint.Cap.ROUND
        }

        fun setHotState(value: Boolean) {
            if (hot == value) return
            hot = value
            animate().scaleX(if (value) 1.25f else 1f).scaleY(if (value) 1.25f else 1f).setDuration(100).start()
            invalidate()
        }

        override fun onDraw(c: Canvas) {
            val w = width.toFloat()
            bg.color = if (hot) Color.rgb(211, 47, 47) else Color.argb(220, 55, 55, 55)
            c.drawCircle(w / 2f, height / 2f, w / 2f - 2f, bg)
            val a = w * 0.32f
            val b = w * 0.68f
            c.drawLine(a, a, b, b, line)
            c.drawLine(b, a, a, b, line)
        }
    }

    /** Transparent full-screen layer where the user draws a free rectangle around text. */
    private class CaptureView(context: Context) : View(context) {
        var onTap: ((Int, Int) -> Unit)? = null
        var onRect: ((Rect) -> Unit)? = null
        private var highlight: Rect? = null // screen coordinates
        private var drag: Rect? = null      // screen coordinates
        private var sx = 0f
        private var sy = 0f
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
            highlight = Rect(r)
            invalidate()
            removeCallbacks(clearRun)
            postDelayed(clearRun, 2500)
        }

        private fun shift(r: Rect) =
            Rect(r.left - loc[0], r.top - loc[1], r.right - loc[0], r.bottom - loc[1])

        private fun makeRect(x: Float, y: Float) = Rect(
            minOf(sx, x).toInt(), minOf(sy, y).toInt(),
            maxOf(sx, x).toInt(), maxOf(sy, y).toInt()
        )

        override fun onDraw(c: Canvas) {
            c.drawPaint(tint)
            getLocationOnScreen(loc)
            drag?.let {
                val s = shift(it)
                c.drawRect(s, fill)
                c.drawRect(s, stroke)
            }
            highlight?.let {
                val s = shift(it)
                c.drawRect(s, fill)
                c.drawRect(s, stroke)
            }
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    sx = e.rawX
                    sy = e.rawY
                    highlight = null
                    drag = makeRect(sx, sy)
                    invalidate()
                }
                MotionEvent.ACTION_MOVE -> {
                    drag = makeRect(e.rawX, e.rawY)
                    invalidate()
                }
                MotionEvent.ACTION_UP -> {
                    val r = makeRect(e.rawX, e.rawY)
                    drag = null
                    invalidate()
                    val minSize = 24 * resources.displayMetrics.density
                    if (r.width() < minSize && r.height() < minSize) {
                        onTap?.invoke(e.rawX.toInt(), e.rawY.toInt())
                    } else {
                        onRect?.invoke(r)
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    drag = null
                    invalidate()
                }
            }
            return true
        }
    }
}
