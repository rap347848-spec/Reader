package com.example.arabicvoiceaccess

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
// تم استيراد مكتبات AdMob المطلوبة هنا:
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds

class MainActivity : Activity() {
    
    private lateinit var adView: AdView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // تهيئة خدمة الإعلانات
        MobileAds.initialize(this) {}

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24, 30, 24, 24)
            setBackgroundColor(Color.rgb(247, 249, 252))
        }

        root.addView(TextView(this).apply {
            text = "قارئ الشاشة"
            textSize = 26f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(24, 68, 120))
        }, matchWrap())

        root.addView(TextView(this).apply {
            text = "\nيقرأ لك أي نص ظاهر على الشاشة في فيسبوك وواتساب والمتصفح وغيرها.\n\n" +
                "1. اضغط «تفعيل الخدمة» وفعّل «قارئ الشاشة» من إعدادات إمكانية الوصول.\n" +
                "2. سيظهر قلم عائم فوق كل التطبيقات، ويمكنك سحبه إلى أي مكان.\n" +
                "3. اضغط على القلم مرة واحدة فيظهر خياران:\n" +
                "   • قراءة كل الشاشة.\n" +
                "   • تحديد مربع حر: ارسم مربعاً بإصبعك حول النص فيُقرأ ما بداخله.\n" +
                "4. اضغط على القلم مرة أخرى للخروج من وضع التحديد.\n" +
                "5. لإغلاق القلم: اسحبه إلى أسفل منتصف الشاشة وأفلته داخل دائرة ×.\n" +
                "    ولإعادته اضغط «إظهار القلم» هنا.\n\n" +
                "ملاحظة: في أندرويد 13 وما بعده، إذا كان خيار الخدمة رمادياً، اضغط «معلومات التطبيق» " +
                "ثم القوائم الثلاث ⋮ ثم «السماح بالإعدادات المقيّدة»."
            textSize = 16f
            setTextColor(Color.DKGRAY)
            setPadding(0, 10, 0, 20)
        }, matchWrap())

        root.addView(Button(this).apply {
            text = "تفعيل الخدمة"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }, matchWrap())

        root.addView(Button(this).apply {
            text = "إظهار القلم"
            setOnClickListener {
                val svc = ReaderAccessibilityService.instance
                if (svc == null) {
                    Toast.makeText(this@MainActivity, "فعّل الخدمة أولاً من إعدادات إمكانية الوصول.", Toast.LENGTH_LONG).show()
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } else {
                    svc.showPen()
                }
            }
        }, matchWrap())

        root.addView(Button(this).apply {
            text = "إخفاء القلم"
            setOnClickListener { ReaderAccessibilityService.instance?.hidePen() }
        }, matchWrap())

        root.addView(Button(this).apply {
            text = "معلومات التطبيق"
            setOnClickListener {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                )
            }
        }, matchWrap())

        root.addView(TextView(this).apply {
            text = "\nالصوت العربي يعتمد على محرك تحويل النص إلى كلام المثبّت في الهاتف."
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.GRAY)
        }, matchWrap())

        // Credit
        root.addView(TextView(this).apply {
            text = "\nتصميم طلال قاسم"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(24, 68, 120))
        }, matchWrap())

        root.addView(TextView(this).apply {
            text = "0599347848"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(24, 68, 120))
            setPadding(0, 4, 0, 24)
        }, matchWrap())

        // --- إضافة إعلان البانر برمجياً في أسفل الشاشة ---
        adView = AdView(this).apply {
            setAdSize(AdSize.BANNER)
            // استخدام معرف وحدة البانر الخاصة بك:
            adUnitId = "ca-app-pub-3407676171751539/6423981030"
            loadAd(AdRequest.Builder().build())
        }
        root.addView(adView, matchWrap())
        // ----------------------------------------------

        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )
}
