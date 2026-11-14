package com.example.gearwallpaper

import android.app.Activity
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(Color.rgb(26, 26, 26))
        }
        val title = TextView(this).apply {
            text = "Часовой механизм"
            textSize = 24f
            setTextColor(Color.rgb(232, 228, 216))
            gravity = Gravity.CENTER
        }
        val info = TextView(this).apply {
            text = "Живые обои: шестерёнки, маятник и стрелки.\n" +
                "Нажми «Включить обои» — откроется системный экран,\n" +
                "там подтверди «Установить обои»."
            textSize = 14f
            setTextColor(Color.rgb(154, 163, 173))
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(24))
        }
        val applyBtn = Button(this).apply {
            text = "Включить обои"
            setOnClickListener { openOurPreview() }
        }
        val chooserBtn = Button(this).apply {
            text = "Список живых обоев (запасной)"
            setOnClickListener { openChooser() }
        }

        val wrap = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        root.addView(title, wrap)
        root.addView(info, wrap)
        root.addView(applyBtn, wrap)
        root.addView(chooserBtn, LinearLayout.LayoutParams(wrap).apply { topMargin = dp(8) })
        setContentView(root)
    }

    /** Системный предпросмотр сразу наших обоев. */
    private fun openOurPreview() {
        try {
            startActivity(
                Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(this, GearWallpaperService::class.java)
                )
            )
        } catch (e: ActivityNotFoundException) {
            openChooser()
        }
    }

    /** Запасной путь: AOSP-список всех живых обоев — он, в отличие от
     *  списка прошивки, показывает сторонние сервисы. */
    private fun openChooser() {
        try {
            startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                this,
                "Системный выборщик обоев недоступен на этой прошивке",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
