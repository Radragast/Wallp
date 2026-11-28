package com.example.gearwallpaper

import android.content.SharedPreferences
import kotlin.random.Random

object OrreryPrefs {
    const val NAME = "orrery_prefs"
    const val SPEED = "speed"
    const val MOON_SPEED = "moon_speed"
    const val TILT = "tilt"
    const val GLOW = "glow"
    const val TRAIL = "trail"
    const val STARS = "stars"
    const val SPREAD = "spread"
    const val LABELS = "labels"
    const val DATE = "date"
    const val EVENTS = "events"
    const val RATE = "rate"
    const val CATASTROPHES = "catastrophes"
    const val MULTI_SCREEN = "multi_screen"
    const val SYSTEM_SCALE = "system_scale"
    const val PLANET_SIZE = "planet_size"
    const val CIV_SPEED = "civ_speed"
    const val CAMERA = "camera_drift"
    const val FACTS = "facts"
    const val SENSOR_TILT = "sensor_tilt"
    const val TILT_STRENGTH = "tilt_strength"
    const val CHARGE_REACTION = "charge_reaction"
    const val OFFLINE_PROGRESS = "offline_progress"
    const val OFFLINE_CAP = "offline_cap"
    const val SIM_STATE = "sim_state_v1"
    const val FORCE_RANDOM = "force_random"
    const val FORCE_CAT = "force_cat"
    const val FORCE_RESTORE = "force_restore"
    const val FORCE_RESET = "force_reset"

    data class Settings(
        val speed: Float = 1f,
        val moonSpeed: Float = 0.1f,
        val tilt: Float = 0.55f,
        val glow: Float = 0.6f,
        val trail: Float = 0.45f,
        val stars: Float = 1f,
        val spread: Float = 1f,
        val labels: Boolean = false,
        val date: Boolean = true,
        val events: Boolean = true,
        val rate: Float = 1f,
        val catastrophes: Boolean = true,
        val multiScreen: Boolean = true,
        val systemScale: Float = 1f,
        val planetSize: Float = 1.4f,
        val civSpeed: Float = 1f,
        val camera: Boolean = true,
        val facts: Boolean = true,
        val sensorTilt: Boolean = true,
        val tiltStrength: Float = 1f,
        val chargeReaction: Boolean = true,
        val offlineProgress: Boolean = true,
        val offlineCap: Float = 8f
    )

    fun read(p: SharedPreferences) = Settings(
        p.getFloat(SPEED, 1f), p.getFloat(MOON_SPEED, 0.1f), p.getFloat(TILT, 0.55f),
        p.getFloat(GLOW, 0.6f), p.getFloat(TRAIL, 0.45f), p.getFloat(STARS, 1f),
        p.getFloat(SPREAD, 1f), p.getBoolean(LABELS, false), p.getBoolean(DATE, true),
        p.getBoolean(EVENTS, true), p.getFloat(RATE, 1f), p.getBoolean(CATASTROPHES, true),
        p.getBoolean(MULTI_SCREEN, true), p.getFloat(SYSTEM_SCALE, 1f),
        p.getFloat(PLANET_SIZE, 1.4f), p.getFloat(CIV_SPEED, 1f),
        p.getBoolean(CAMERA, true), p.getBoolean(FACTS, true),
        p.getBoolean(SENSOR_TILT, true), p.getFloat(TILT_STRENGTH, 1f),
        p.getBoolean(CHARGE_REACTION, true),
        p.getBoolean(OFFLINE_PROGRESS, true), p.getFloat(OFFLINE_CAP, 8f)
    )

    fun write(p: SharedPreferences, s: Settings) {
        p.edit()
            .putFloat(SPEED, s.speed).putFloat(MOON_SPEED, s.moonSpeed)
            .putFloat(TILT, s.tilt).putFloat(GLOW, s.glow)
            .putFloat(TRAIL, s.trail).putFloat(STARS, s.stars).putFloat(SPREAD, s.spread)
            .putBoolean(LABELS, s.labels).putBoolean(DATE, s.date)
            .putBoolean(EVENTS, s.events).putFloat(RATE, s.rate)
            .putBoolean(CATASTROPHES, s.catastrophes).putBoolean(MULTI_SCREEN, s.multiScreen)
            .putFloat(SYSTEM_SCALE, s.systemScale).putFloat(PLANET_SIZE, s.planetSize)
            .putFloat(CIV_SPEED, s.civSpeed).putBoolean(CAMERA, s.camera)
            .putBoolean(FACTS, s.facts)
            .putBoolean(SENSOR_TILT, s.sensorTilt).putFloat(TILT_STRENGTH, s.tiltStrength)
            .putBoolean(CHARGE_REACTION, s.chargeReaction)
            .putBoolean(OFFLINE_PROGRESS, s.offlineProgress).putFloat(OFFLINE_CAP, s.offlineCap)
            .apply()
    }

    fun random(): Settings {
        val r = Random(System.nanoTime())
        return Settings(
            speed = 0.4f + r.nextFloat() * 2f,
            moonSpeed = 0.05f + r.nextFloat() * 0.15f,
            tilt = 0.2f + r.nextFloat() * 0.6f,
            glow = 0.35f + r.nextFloat() * 0.5f,
            trail = r.nextFloat(),
            stars = 0.5f + r.nextFloat() * 1.5f,
            spread = 0.85f + r.nextFloat() * 0.3f,
            labels = r.nextBoolean(),
            civSpeed = 0.5f + r.nextFloat() * 2f,
            systemScale = 0.9f + r.nextFloat() * 0.4f,
            planetSize = 1.1f + r.nextFloat() * 0.9f,
            sensorTilt = true,
            tiltStrength = 0.6f + r.nextFloat() * 0.8f,
            chargeReaction = true,
            offlineProgress = true,
            offlineCap = 8f
        )
    }
}
