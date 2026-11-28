package com.example.gearwallpaper

import android.content.Context
import android.content.SharedPreferences
import kotlin.math.min

object OrreryState {
    private const val FILE = "orrery_state"
    private const val KEY_TIME = "t"
    private const val KEY_DAYS = "days"
    private const val KEY_SWARM = "swarm"
    private const val KEY_N = "n"
    private const val MAX_CATCHUP = 28800f

    private fun prefs(c: Context): SharedPreferences =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun store(c: Context, sim: OrrerySim) {
        val e = prefs(c).edit()
        e.putLong(KEY_TIME, System.currentTimeMillis())
        e.putString(KEY_DAYS, sim.simDays.toString())
        e.putFloat(KEY_SWARM, sim.swarm)
        e.putInt(KEY_N, sim.bodies.size)
        for (i in sim.bodies.indices) {
            val b = sim.bodies[i]
            val s = StringBuilder()
            s.append(b.stage).append('|')
            s.append(b.pop).append('|')
            s.append(b.prog).append('|')
            s.append(b.scars).append('|')
            s.append(if (b.ruined) 1 else 0).append('|')
            s.append(if (b.destroyed) 1 else 0).append('|')
            s.append(b.rebuild).append('|')
            s.append(b.colonyOf).append('|')
            s.append(if (b.ringBuilt) 1 else 0).append('|')
            s.append(if (b.elevator) 1 else 0).append('|')
            s.append(b.aggression).append('|')
            s.append(b.colonyTimer)
            e.putString("b$i", s.toString())
        }
        e.apply()
    }

    fun restore(c: Context, sim: OrrerySim, st: OrreryPrefs.Settings) {
        val p = prefs(c)
        val n = p.getInt(KEY_N, 0)
        if (n != sim.bodies.size) return
        val t = p.getLong(KEY_TIME, 0L)
        if (t == 0L) return

        sim.simDays = (p.getString(KEY_DAYS, "0") ?: "0").toDoubleOrNull() ?: 0.0
        sim.swarm = p.getFloat(KEY_SWARM, 0f)

        val oldStage = IntArray(n)
        val oldColony = IntArray(n)
        val oldDead = BooleanArray(n)

        for (i in 0 until n) {
            val line = p.getString("b$i", null) ?: continue
            val f = line.split('|')
            if (f.size < 12) continue
            val b = sim.bodies[i]
            b.stage = (f[0].toIntOrNull() ?: 0).coerceIn(0, 7)
            b.pop = f[1].toDoubleOrNull() ?: 0.0
            b.prog = f[2].toFloatOrNull() ?: 0f
            b.scars = f[3].toFloatOrNull() ?: 0f
            b.ruined = f[4] == "1"
            b.destroyed = f[5] == "1"
            b.rebuild = f[6].toFloatOrNull() ?: 0f
            b.colonyOf = f[7].toIntOrNull() ?: -1
            b.ringBuilt = f[8] == "1"
            b.elevator = f[9] == "1"
            b.aggression = f[10].toFloatOrNull() ?: 0.5f
            b.colonyTimer = f[11].toFloatOrNull() ?: 40f
            oldStage[i] = b.stage
            oldColony[i] = b.colonyOf
            oldDead[i] = b.destroyed
        }

        val elapsed = ((System.currentTimeMillis() - t) / 1000f).coerceIn(0f, MAX_CATCHUP)
        if (elapsed < 60f) return

        sim.civSpeed = st.civSpeed
        sim.moonSpeed = st.moonSpeed
        sim.eventRate = st.rate
        sim.catOn = st.catastrophes
        sim.eventsOn = false

        var left = elapsed
        while (left > 0f) {
            val d = min(5f, left)
            sim.update(d, st.speed)
            left -= d
        }
        sim.eventsOn = st.events
        sim.pendingRandom = false
        sim.pendingCat = false
        sim.pendingRestore = false

        for (i in 0 until n) {
            val b = sim.bodies[i]
            if (b.stage > oldStage[i] + 2) {
                b.stage = oldStage[i] + 2
                b.prog = 0f
            }
        }

        var colonies = 0
        var epochs = 0
        var lost = 0
        var revived = 0
        for (i in 0 until n) {
            val b = sim.bodies[i]
            if (b.colonyOf >= 0 && oldColony[i] < 0) colonies++
            val d = b.stage - oldStage[i]
            if (d > 0) epochs += d
            if (b.destroyed && !oldDead[i]) lost++
            if (!b.destroyed && oldDead[i]) revived++
        }

        val h = (elapsed / 3600f).toInt()
        val m = ((elapsed - h * 3600f) / 60f).toInt()
        val span = if (h > 0) "$h ч $m мин" else "$m мин"

        val parts = ArrayList<String>()
        if (colonies > 0) parts.add("колоний +$colonies")
        if (epochs > 0) parts.add("эпох +$epochs")
        if (lost > 0) parts.add("погибло миров: $lost")
        if (revived > 0) parts.add("восстановлено: $revived")
        val tail = if (parts.isEmpty()) "без изменений" else parts.joinToString(" · ")

        sim.cardBody = if (n > 2) 2 else 0
        sim.cardTitle = "СВОДКА"
        sim.cardText = "Тебя не было $span · $tail"
        sim.cardTag = "СИМУЛЯЦИЯ"
        sim.cardAge = 0f
    }
}
