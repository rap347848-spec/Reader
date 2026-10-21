package com.example.arabicvoiceaccess

import android.app.Activity
import android.content.Intent
import android.Manifest
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import android.graphics.Color

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24, 30, 24, 24)
            setBackgroundColor(Color.rgb(247, 249, 252))
        }
        root.addView(TextView(this).apply {
            text = "قارئ الشاشة الصوتي"
            textSize = 26f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(24, 68, 120))
        }, matchWrap())

        root.addView(TextView(this).apply {
            text = "\nاقرأ النصوص من فيسبوك وواتساب والمتصفح والتطبيقات الأخرى.\n\n" +
                "1. فعّل خدمة قارئ الشاشة من إعدادات إمكانية الوصول.\n" +
                "2. ارجع إلى أي تطبيق وحدد نصًا، ثم اختر «مشاركة» أو «نسخ» إذا كان ذلك متاحًا.\n" +
                "3. لقراءة محتوى الشاشة، افتح لوحة الإشعارات واضغط «قراءة الشاشة» في الإشعار.\n\n" +
                "ملاحظة: بعض التطبيقات لا تتيح تحديد النص أو كشف كل محتوى الشاشة."
            textSize = 17f
            setTextColor(Color.DKGRAY)
            setPadding(0, 20, 0, 20)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val enable = Button(this).apply {
            text = "تفعيل خدمة إمكانية الوصول"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        root.addView(enable, matchWrap())

        val start = Button(this).apply {
            text = "بدء قراءة الشاشة"
            setOnClickListener {
                val svc = ReaderAccessibilityService.instance
                if (svc == null) {
                    Toast.makeText(this@MainActivity, "فعّل الخدمة أولاً من إعدادات إمكانية الوصول.", Toast.LENGTH_LONG).show()
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } else {
                    Toast.makeText(this@MainActivity, "افتح التطبيق المطلوب ثم اضغط «قراءة الشاشة» في الإشعار.", Toast.LENGTH_LONG).show()
                }
            }
        }
        root.addView(start, matchWrap())

        root.addView(TextView(this).apply {
            text = "\nالصوت العربي يعتمد على محرك تحويل النص إلى كلام المثبّت في الهاتف."
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.GRAY)
        }, matchWrap())

        setContentView(root)
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )
}
