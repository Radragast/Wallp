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
            setPadding(dp(20), dp(24), dp(20), dp(36))
            setBackgroundColor(Color.rgb(6, 9, 16))
        }
        setContentView(ScrollView(this).apply { addView(col) })

        col.addView(TextView(this).apply {
            text = "Коллекция живых обоев"
            textSize = 22f
            setTextColor(Color.rgb(228, 199, 124))
            gravity = Gravity.CENTER
        })
        col.addView(space(dp(16)))
        col.addView(button("⚙ Часовой механизм") { openPreview(GearWallpaperService::class.java) })
        col.addView(space(dp(8)))
        col.addView(button("🪐 Планетарные часы · живая система") { openPreview(OrreryWallpaperService::class.java) })
        col.addView(space(dp(8)))
        col.addView(button("🚀 Космический полёт") { openPreview(SpaceCombatWallpaperService::class.java) })
        col.addView(space(dp(8)))
        col.addView(button("Список живых обоев (запасной)") { openChooser() })

        col.addView(space(dp(24)))
        col.addView(header("ДВИЖЕНИЕ"))
        slider(col, "Скорость вращения спутников", OrreryPrefs.MOON_SPEED, 0.02f, 1f)
        slider(col, "Скорость суток (планеты)", OrreryPrefs.SPEED, 0.1f, 5f)
        slider(col, "Скорость развития цивилизаций", OrreryPrefs.CIV_SPEED, 0.2f, 3f)

        col.addView(space(dp(20)))
        col.addView(header("КАМЕРА И МАСШТАБ"))
        switch(col, "Наезд камеры с плавным возвратом", OrreryPrefs.CAMERA, true)
        switch(col, "Растянуть на все экраны", OrreryPrefs.MULTI_SCREEN, true)
        slider(col, "Масштаб системы", OrreryPrefs.SYSTEM_SCALE, 0.5f, 2.5f)
        slider(col, "Размер планет", OrreryPrefs.PLANET_SIZE, 0.6f, 3f)
        slider(col, "Наклон орбит", OrreryPrefs.TILT, 0f, 1f)

        col.addView(space(dp(20)))
        col.addView(header("ИНФОРМАЦИЯ"))
        switch(col, "Карточки в золотой рамке у планет", OrreryPrefs.FACTS, true)
        switch(col, "Дата и время сверху", OrreryPrefs.DATE, true)
        switch(col, "Подписи миров", OrreryPrefs.LABELS, false)

        col.addView(space(dp(20)))
        col.addView(header("ВИД"))
        slider(col, "Свечение", OrreryPrefs.GLOW, 0f, 1f)
        slider(col, "Длина шлейфов", OrreryPrefs.TRAIL, 0f, 1f)
        slider(col, "Плотность звёзд", OrreryPrefs.STARS, 0.3f, 2f)
        slider(col, "Разброс орбит", OrreryPrefs.SPREAD, 0.7f, 1.5f)

        col.addView(space(dp(20)))
        col.addView(header("СОБЫТИЯ"))
        switch(col, "События включены", OrreryPrefs.EVENTS, true)
        slider(col, "Частота событий", OrreryPrefs.RATE, 0.2f, 3f)
        switch(col, "Разрешить катастрофы", OrreryPrefs.CATASTROPHES, true)
        col.addView(space(dp(8)))
        col.addView(button("✦ Случайное событие (комета, ракета, НЛО…)") {
            prefs.edit().putLong(OrreryPrefs.FORCE_RANDOM, System.nanoTime()).apply()
            toast("Событие начнётся в течение секунды")
        })
        col.addView(space(dp(8)))
        col.addView(button("☄ Катастрофа (с восстановлением мира)") {
            prefs.edit().putLong(OrreryPrefs.FORCE_CAT, System.nanoTime()).apply()
            toast("Катастрофа запущена")
        })
        col.addView(space(dp(8)))
        col.addView(button("✚ Восстановить повреждённые миры") {
            prefs.edit().putLong(OrreryPrefs.FORCE_RESTORE, System.nanoTime()).apply()
            toast("Протокол «Генезис» запущен")
        })

        col.addView(space(dp(18)))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(button("Сброс") {
            OrreryPrefs.write(prefs, OrreryPrefs.Settings()); recreate()
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(space(dp(8)))
        row.addView(button("Случайно") {
            OrreryPrefs.write(prefs, OrreryPrefs.random()); recreate()
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        col.addView(row)
    }

    private fun toast(m: String) {
        Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
    }

    private fun space(h: Int) = TextView(this).apply { height = h }

    private fun header(t: String) = TextView(this).apply {
        text = t
        textSize = 12f
        setTextColor(Color.rgb(201, 162, 39))
        letterSpacing = 0.15f
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    private fun slider(parent: LinearLayout, label: String, key: String, min: Float, max: Float) {
        val def = OrreryPrefs.read(prefs)
        val fallback = when (key) {
            OrreryPrefs.SPEED -> def.speed
            OrreryPrefs.MOON_SPEED -> def.moonSpeed
            OrreryPrefs.TILT -> def.tilt
            OrreryPrefs.GLOW -> def.glow
            OrreryPrefs.TRAIL -> def.trail
            OrreryPrefs.STARS -> def.stars
            OrreryPrefs.SPREAD -> def.spread
            OrreryPrefs.RATE -> def.rate
            OrreryPrefs.SYSTEM_SCALE -> def.systemScale
            OrreryPrefs.PLANET_SIZE -> def.planetSize
            OrreryPrefs.CIV_SPEED -> def.civSpeed
            else -> 1f
        }
        val cur = prefs.getFloat(key, fallback)
        val tv = TextView(this)
        tv.textSize = 13f
        tv.setTextColor(Color.rgb(190, 210, 230))
        tv.text = label + ": " + String.format(Locale.US, "%.2f", cur)
        parent.addView(tv)

        val sb = SeekBar(this)
        sb.max = 100
        sb.progress = ((cur - min) / (max - min) * 100f).roundToInt().coerceIn(0, 100)
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(b: SeekBar, p: Int, fromUser: Boolean) {
                val v = min + (max - min) * p / 100f
                prefs.edit().putFloat(key, v).apply()
                tv.text = label + ": " + String.format(Locale.US, "%.2f", v)
            }
            override fun onStartTrackingTouch(b: SeekBar) {}
            override fun onStopTrackingTouch(b: SeekBar) {}
        })
        parent.addView(sb)
    }

    private fun switch(parent: LinearLayout, label: String, key: String, def: Boolean) {
        val sw = Switch(this)
        sw.text = label
        sw.textSize = 13f
        sw.setTextColor(Color.rgb(190, 210, 230))
        sw.isChecked = prefs.getBoolean(key, def)
        sw.setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
            prefs.edit().putBoolean(key, checked).apply()
        }
        parent.addView(sw)
    }

    private fun openPreview(cls: Class<*>) {
        try {
            startActivity(
                Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(this, cls)
                )
            )
        } catch (e: ActivityNotFoundException) {
            openChooser()
        }
    }

    private fun openChooser() {
        try {
            startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
        } catch (e: ActivityNotFoundException) {
            toast("Системный выборщик обоев недоступен")
        }
    }
}
