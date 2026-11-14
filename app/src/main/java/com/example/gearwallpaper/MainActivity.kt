package com.example.gearwallpaper

import android.app.Activity
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(OrreryPrefs.NAME, MODE_PRIVATE)
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(32))
            setBackgroundColor(Color.rgb(6, 9, 16))
        }
        setContentView(ScrollView(this).apply { addView(col) })

        col.addView(TextView(this).apply {
            text = "Коллекция живых обоев"; textSize = 22f
            setTextColor(Color.rgb(225, 238, 250)); gravity = Gravity.CENTER
        })
        col.addView(space(dp(16)))
        col.addView(button("⚙ Часовой механизм — включить") { openPreview(GearWallpaperService::class.java) })
        col.addView(space(dp(8)))
        col.addView(button("🪐 Планетарные часы — включить") { openPreview(OrreryWallpaperService::class.java) })
        col.addView(space(dp(8)))
        col.addView(button("Список живых обоев (запасной)") { openChooser() })

        col.addView(space(dp(24)))
        col.addView(header("НАСТРОЙКА ПЛАНЕТАРНЫХ ЧАСОВ"))
        
        // НОВЫЕ ПОЛЗУНКИ
        slider(col, "Общий масштаб системы", OrreryPrefs.SYSTEM_SCALE, 0.5f, 2.5f)
        slider(col, "Размер планет", OrreryPrefs.PLANET_SIZE, 0.5f, 3.0f)
        
        switch(col, "Растянуть на все экраны", OrreryPrefs.MULTI_SCREEN, true)
        slider(col, "Скорость, суток/с", OrreryPrefs.SPEED, 0.1f, 5f)
        slider(col, "Наклон орбит", OrreryPrefs.TILT, 0f, 1f)
        slider(col, "Свечение", OrreryPrefs.GLOW, 0f, 1f)
        slider(col, "Длина шлейфов", OrreryPrefs.TRAIL, 0f, 1f)
        slider(col, "Плотность звёзд", OrreryPrefs.STARS, 0.3f, 2f)
        slider(col, "Разброс орбит", OrreryPrefs.SPREAD, 0.6f, 1.6f)
        switch(col, "Подписи планет", OrreryPrefs.LABELS, false)
        switch(col, "Строка даты и времени", OrreryPrefs.DATE, true)

        col.addView(space(dp(20)))
        col.addView(header("РЕДКИЕ КОСМИЧЕСКИЕ СОБЫТИЯ"))
        switch(col, "События включены", OrreryPrefs.EVENTS, true)
        slider(col, "Частота событий", OrreryPrefs.RATE, 0.2f, 3f)
        switch(col, "Разрешить катастрофы", OrreryPrefs.CATASTROPHES, true)
        col.addView(space(dp(8)))
        col.addView(button("✦ Вызвать случайное событие") {
            prefs.edit().putLong(OrreryPrefs.FORCE_RANDOM, System.nanoTime()).apply()
            Toast.makeText(this, "Событие начнётся на обоях в течение секунды", Toast.LENGTH_SHORT).show()
        })
        col.addView(space(dp(8)))
        col.addView(button("☄ Вызвать катастрофу (перезапуск вселенной)") {
            prefs.edit().putLong(OrreryPrefs.FORCE_CAT, System.nanoTime()).apply()
            Toast.makeText(this, "Катастрофа запущена", Toast.LENGTH_SHORT).show()
        })

        col.addView(space(dp(16)))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(button("Сброс") { OrreryPrefs.write(prefs, OrreryPrefs.Settings()); recreate() },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(space(dp(8)))
        row.addView(button("Случайно") { OrreryPrefs.write(prefs, OrreryPrefs.random()); recreate() },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        col.addView(row)
    }

    private fun space(h: Int) = TextView(this).apply { height = h }
    private fun header(t: String) = TextView(this).apply {
        text = t; textSize = 12f; setTextColor(Color.rgb(120, 170, 215)); letterSpacing = 0.15f
    }
    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text; isAllCaps = false; setOnClickListener { onClick() }
    }

    private fun slider(parent: LinearLayout, label: String, key: String, min: Float, max: Float) {
        val def = OrreryPrefs.read(prefs)
        val cur = prefs.getFloat(key, when (key) {
            OrreryPrefs.SPEED -> def.speed; OrreryPrefs.TILT -> def.tilt
            OrreryPrefs.GLOW -> def.glow; OrreryPrefs.TRAIL -> def.trail
            OrreryPrefs.STARS -> def.stars; OrreryPrefs.SPREAD -> def.spread
            OrreryPrefs.RATE -> def.rate
            OrreryPrefs.SYSTEM_SCALE -> def.systemScale
            OrreryPrefs.PLANET_SIZE -> def.planetSize
            else -> 1f
        })
        val tv = TextView(this).apply {
            textSize = 13f; setTextColor(Color.rgb(190, 210, 230))
            text = "$label: ${String.format(Locale.US, "%.2f", cur)}"
        }
        parent.addView(tv)
        parent.addView(SeekBar(this).apply {
            this.max = 100
            progress = ((cur - min) / (max - min) * 100).roundToInt().coerceIn(0, 100)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(b: SeekBar, p: Int, fromUser: Boolean) {
                    val v = min + (max - min) * p / 100f
                    prefs.edit().putFloat(key, v).apply()
                    tv.text = "$label: ${String.format(Locale.US, "%.2f", v)}"
                }
                override fun onStartTrackingTouch(b: SeekBar) {}
                override fun onStopTrackingTouch(b: SeekBar) {}
            })
        })
    }

    private fun switch(parent: LinearLayout, label: String, key: String, def: Boolean) {
        parent.addView(Switch(this).apply {
            text = label; textSize = 13f; setTextColor(Color.rgb(190, 210, 230))
            isChecked = prefs.getBoolean(key, def)
            setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
                prefs.edit().putBoolean(key, checked).apply()
            }
        })
    }

    private fun openPreview(cls: Class<*>) {
        try {
            startActivity(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(this, cls)))
        } catch (e: ActivityNotFoundException) { openChooser() }
    }

    private fun openChooser() {
        try { startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)) }
        catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Системный выборщик обоев недоступен", Toast.LENGTH_LONG).show()
        }
    }
}
