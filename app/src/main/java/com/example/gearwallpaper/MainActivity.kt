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
                "Если их нет в системном списке обоев — включи кнопкой ниже."
            textSize = 14f
            setTextColor(Color.rgb(154, 163, 173))
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(24))
        }
        val applyBtn = Button(this).apply {
            text = "Включить обои"
            setOnClickListener { applyWallpaper() }
        }
        val previewBtn = Button(this).apply {
            text = "Системный предпросмотр"
            setOnClickListener { openPreview() }
        }

        val wrap = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        root.addView(title, wrap)
        root.addView(info, wrap)
        root.addView(applyBtn, wrap)
        root.addView(previewBtn, LinearLayout.LayoutParams(wrap).apply { topMargin = dp(8) })
        setContentView(root)
    }

    /** Ставит обои напрямую, без списка обоев прошивки. */
    private fun applyWallpaper() {
        try {
            WallpaperManager.getInstance(this)
                .setWallpaperComponent(ComponentName(this, GearWallpaperService::class.java))
            Toast.makeText(this, "Обои установлены ✔", Toast.LENGTH_LONG).show()
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "Напрямую не вышло: ${e.message}", Toast.LENGTH_LONG).show()
            openPreview()
        }
    }

    /** Запасной путь: системный экран предпросмотра живых обоев. */
    private fun openPreview() {
        try {
            startActivity(
                Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(this, GearWallpaperService::class.java)
                )
            )
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Предпросмотр недоступен на этой прошивке", Toast.LENGTH_LONG).show()
        }
    }
}
