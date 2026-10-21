package com.example.arabicvoiceaccess

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
                "3. اضغط على القلم ثم اضغط على النص الذي تريد قراءته.\n" +
                "4. اضغط على القلم مرة أخرى للخروج من وضع القلم.\n" +
                "5. الضغط المطوّل على القلم يقرأ كل ما هو ظاهر على الشاشة.\n\n" +
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

        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )
}
