package com.example.gearwallpaper

import android.content.SharedPreferences
import kotlin.random.Random

object OrreryPrefs {
    const val NAME = "orrery_prefs"
    const val SPEED = "speed"; const val TILT = "tilt"; const val GLOW = "glow"
    const val TRAIL = "trail"; const val STARS = "stars"; const val SPREAD = "spread"
    const val LABELS = "labels"; const val DATE = "date"
    const val EVENTS = "events"; const val RATE = "rate"; const val CATASTROPHES = "catastrophes"
    const val MULTI_SCREEN = "multi_screen"
    const val SYSTEM_SCALE = "system_scale"
    const val PLANET_SIZE = "planet_size"
    const val FORCE_RANDOM = "force_random"; const val FORCE_CAT = "force_cat"

    data class Settings(
        val speed: Float = 1f, val tilt: Float = 0.6f, val glow: Float = 0.7f,
        val trail: Float = 0.5f, val stars: Float = 1f, val spread: Float = 1f,
        val labels: Boolean = false, val date: Boolean = true,
        val events: Boolean = true, val rate: Float = 1f, val catastrophes: Boolean = true,
        val multiScreen: Boolean = true,
        val systemScale: Float = 1.2f, val planetSize: Float = 1.5f,
    )

    fun read(p: SharedPreferences) = Settings(
        p.getFloat(SPEED, 1f), p.getFloat(TILT, 0.6f), p.getFloat(GLOW, 0.7f),
        p.getFloat(TRAIL, 0.5f), p.getFloat(STARS, 1f), p.getFloat(SPREAD, 1f),
        p.getBoolean(LABELS, false), p.getBoolean(DATE, true),
        p.getBoolean(EVENTS, true), p.getFloat(RATE, 1f), p.getBoolean(CATASTROPHES, true),
        p.getBoolean(MULTI_SCREEN, true),
        p.getFloat(SYSTEM_SCALE, 1.2f), p.getFloat(PLANET_SIZE, 1.5f),
    )

    fun write(p: SharedPreferences, s: Settings) {
        p.edit()
            .putFloat(SPEED, s.speed).putFloat(TILT, s.tilt).putFloat(GLOW, s.glow)
            .putFloat(TRAIL, s.trail).putFloat(STARS, s.stars).putFloat(SPREAD, s.spread)
            .putBoolean(LABELS, s.labels).putBoolean(DATE, s.date)
            .putBoolean(EVENTS, s.events).putFloat(RATE, s.rate).putBoolean(CATASTROPHES, s.catastrophes)
            .putBoolean(MULTI_SCREEN, s.multiScreen)
            .putFloat(SYSTEM_SCALE, s.systemScale).putFloat(PLANET_SIZE, s.planetSize)
            .apply()
    }

    fun random(): Settings {
        val r = Random(System.nanoTime())
        return Settings(
            speed = 0.3f + r.nextFloat() * 3f, tilt = r.nextFloat(),
            glow = 0.3f + r.nextFloat() * 0.7f, trail = r.nextFloat(),
            stars = 0.4f + r.nextFloat() * 1.6f, spread = 0.6f + r.nextFloat(),
            labels = r.nextBoolean(), date = true,
            events = true, rate = 0.5f + r.nextFloat() * 2f, catastrophes = r.nextBoolean(),
            multiScreen = true, systemScale = 1.0f + r.nextFloat(), planetSize = 1.0f + r.nextFloat()
        )
    }
}
