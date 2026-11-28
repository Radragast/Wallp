package com.example.gearwallpaper

import android.content.Context
import android.graphics.Color
import java.util.Random
import kotlin.math.max
import kotlin.math.min

private class Faction(
    val name: String,
    val home: Int,
    val color: Int,
    var expansion: Float,
    var science: Float,
    var aggression: Float,
    var caution: Float,
    var bio: Float
) {
    var intent = 7
    var decisionIn = 45f
    var shockLock = 0f
    var lastPop = 0.0
    var loss = 0f
    var dominance = 0f
    var alive = true
    var target = -1
    var conflict = 0f
    var reviveIn = 0f
    var reason = ""
}

class OrreryFactions {
    private val list = ArrayList<Faction>()
    private val rel = Array(3) { FloatArray(3) }
    private var owners = IntArray(0)
    private val rng = Random(31337L)
    private var cardLock = 0f
    private var pendTitle = ""
    private var pendText = ""
    private var pendBody = -1
    private var emptyTimer = 0f
    private val caps = doubleArrayOf(0.0, 1e3, 1e5, 1e7, 1e10, 1e11, 1e12, 1e13)
    private val names = arrayOf(
        "КОЛОНИЗАЦИЯ", "НАУКА", "РОЙ ДАЙСОНА", "ОБОРОНА",
        "ВООРУЖЕНИЕ", "ТОРГОВЛЯ", "АТАКА", "ВОССТАНОВЛЕНИЕ"
    )
    private val palette = intArrayOf(
        Color.rgb(224, 166, 75),
        Color.rgb(181, 98, 58),
        Color.rgb(111, 168, 201)
    )

    fun count(): Int = list.size
    fun colorOf(i: Int): Int {
        if (i < 0 || i >= list.size) return Color.TRANSPARENT
        val c = list[i].color
        return Color.argb(150, Color.red(c), Color.green(c), Color.blue(c))
    }
    fun homeOf(i: Int): Int = list[i].home
    fun relation(i: Int, j: Int): Float = rel[i][j]
    fun aliveOf(i: Int): Boolean = i in list.indices && list[i].alive
    fun intentOf(i: Int): Int = if (i in list.indices) list[i].intent else -1
    fun scienceOf(i: Int): Float = if (i in list.indices) list[i].science else 0f
    fun expansionOf(i: Int): Float = if (i in list.indices) list[i].expansion else 0f
    fun nameOf(i: Int): String = if (i in list.indices) list[i].name else ""
    fun ownerOf(body: Int): Int = if (body in owners.indices) owners[body] else -1
    fun leaderName(): String {
        var best = -1
        var bp = -1.0
        for (i in list.indices) {
            val p = popOf(i)
            if (p > bp) { bp = p; best = i }
        }
        return if (best >= 0) "ЛИДЕР " + list[best].name else ""
    }

    private var cachedPop = DoubleArray(3)

    private fun popOf(i: Int): Double = if (i in cachedPop.indices) cachedPop[i] else 0.0

    private fun spawn(sim: OrrerySim, body: Int) {
        if (list.size >= 3) return
        val b = sim.bodies[body]
        val idx = list.size
        val f = when {
            b.icy -> Faction(b.name.uppercase(), body, palette[idx], 0.2f, 0.9f, 0.1f, 0.75f, 0.6f)
            b.name == "Марс" -> Faction(b.name.uppercase(), body, palette[idx], 0.5f, 0.35f, 0.7f, 0.6f, 0.2f)
            idx == 0 -> Faction(b.name.uppercase(), body, palette[idx], 0.8f, 0.45f, 0.5f, 0.3f, 0.35f)
            else -> Faction(b.name.uppercase(), body, palette[idx], 0.6f, 0.5f, 0.4f, 0.5f, 0.4f)
        }
        f.decisionIn = 40f + idx * 130f + rng.nextFloat() * 60f
        f.lastPop = b.pop
        b.colonyOf = -1
        list.add(f)
        pendBody = body
        pendTitle = f.name
        pendText = "Новая фракция · независимость провозглашена"
    }

    private fun recompute(sim: OrrerySim) {
        if (owners.size != sim.bodies.size) owners = IntArray(sim.bodies.size)
        for (i in owners.indices) owners[i] = -1
        for (k in list.indices) if (list[k].alive) owners[list[k].home] = k
        repeat(4) {
            for (i in sim.bodies.indices) {
                val b = sim.bodies[i]
                if (owners[i] < 0 && b.colonyOf >= 0 && b.colonyOf in owners.indices && owners[b.colonyOf] >= 0) {
                    owners[i] = owners[b.colonyOf]
                }
            }
        }
        for (k in 0 until 3) cachedPop[k] = 0.0
        for (i in sim.bodies.indices) {
            val o = owners[i]
            if (o >= 0 && o < 3) cachedPop[o] += sim.bodies[i].pop
        }
    }

    private fun anyConflict(): Boolean {
        for (f in list) if (f.conflict > 0f) return true
        return false
    }

    fun update(dt: Float, sim: OrrerySim, st: OrreryPrefs.Settings) {
        if (list.isEmpty() && sim.bodies[2].pop > 0.0) spawn(sim, 2)
        recompute(sim)

        if (list.size < 3) {
            for (i in sim.bodies.indices) {
                val b = sim.bodies[i]
                if (b.destroyed || b.colonyOf < 0 || owners[i] < 0) continue
                val need = if (list.size == 1) 5 else 6
                if (b.stage >= need) { spawn(sim, i); recompute(sim); break }
            }
        }

        cardLock = max(0f, cardLock - dt)
        var inhabited = 0
        for (i in sim.bodies.indices) if (sim.bodies[i].pop > 1e3) inhabited++

        for (k in list.indices) {
            val f = list[k]
            f.shockLock = max(0f, f.shockLock - dt)
            val now = cachedPop[k]
            if (f.lastPop > 1e4 && now < f.lastPop * 0.7f) {
                f.loss = 1f
                if (f.shockLock <= 0f) { f.decisionIn = 0f; f.shockLock = 180f }
            }
            f.loss = max(0f, f.loss - dt * 0.01f)
            f.lastPop = now

            if (now <= 0.0) {
                if (f.alive) { f.alive = false; f.reviveIn = 1200f + rng.nextFloat() * 1200f }
                f.reviveIn -= dt
                if (f.reviveIn <= 0f) {
                    val h = sim.bodies[f.home]
                    if (!h.destroyed) {
                        h.stage = max(h.stage, 1)
                        h.pop = 1e3
                        h.ruined = false
                        f.alive = true
                        pendBody = f.home
                        pendTitle = f.name
                        pendText = "Наследники вернулись на руины"
                    } else f.reviveIn = 600f
                }
                continue
            }
            f.alive = true

            if (inhabited > 0) {
                var mine = 0
                for (i in sim.bodies.indices) if (owners[i] == k && sim.bodies[i].pop > 1e3) mine++
                if (mine.toFloat() / inhabited > 0.6f) f.dominance += dt else f.dominance = max(0f, f.dominance - dt)
            }

            f.decisionIn -= dt * st.civSpeed
            if (f.decisionIn <= 0f) {
                decide(k, sim, inhabited)
                f.decisionIn = 720f + rng.nextFloat() * 600f
            }
            apply(k, dt, sim, st)

            if (f.conflict > 0f) {
                f.conflict -= dt
                val t = f.target
                if (t in list.indices) {
                    for (i in sim.bodies.indices) {
                        if (owners[i] == t && sim.bodies[i].pop > 0.0) {
                            sim.bodies[i].pop *= (1f - dt * 0.0008f)
                            sim.bodies[i].scars = min(1f, sim.bodies[i].scars + dt * 0.004f)
                        }
                    }
                    rel[k][t] = max(-1f, rel[k][t] - dt * 0.002f)
                    rel[t][k] = rel[k][t]
                }
                if (f.conflict <= 0f) {
                    f.target = -1
                    f.caution = min(0.95f, f.caution + 0.05f)
                    f.aggression = max(0.05f, f.aggression - 0.03f)
                }
            }
        }

        for (i in 0 until 3) {
            for (j in 0 until 3) {
                if (i == j) continue
                rel[i][j] += (0f - rel[i][j]) * dt * 0.0006f
            }
        }
        for (k in list.indices) {
            if (list[k].dominance < 1200f) continue
            for (j in list.indices) {
                if (j == k || !list[j].alive) continue
                rel[j][k] = max(-1f, rel[j][k] - dt * 0.004f)
                list[j].aggression = min(0.95f, list[j].aggression + dt * 0.002f)
            }
        }

        var anyPop = false
        for (b in sim.bodies) if (b.pop > 0.0) { anyPop = true; break }
        if (!anyPop) {
            emptyTimer += dt
            if (emptyTimer > 600f) {
                emptyTimer = 0f
                val e = sim.bodies[2]
                if (!e.destroyed) { e.stage = 1; e.pop = 1e3; e.ruined = false }
            }
        } else emptyTimer = 0f

        if (pendBody >= 0 && cardLock <= 0f && sim.cardBody < 0) {
            sim.cardBody = pendBody
            sim.cardTitle = pendTitle
            sim.cardText = pendText
            sim.cardTag = "ФРАКЦИИ"
            sim.cardAge = 0f
            cardLock = 95f + rng.nextFloat() * 55f
            pendBody = -1
        }
    }

    private fun decide(k: Int, sim: OrrerySim, inhabited: Int) {
        val f = list[k]
        var mine = 0
        var ruined = 0
        var mega = false
        var colonizable = false
        for (i in sim.bodies.indices) {
            val b = sim.bodies[i]
            if (owners[i] == k) {
                if (b.pop > 1e3) mine++
                if (b.ruined || b.scars > 0.3f) ruined++
                if (b.stage >= 6) mega = true
            }
            if (b.colonyOf < 0 && owners[i] < 0 && !b.destroyed &&
                (b.name == "Марс" || b.name == "Луна" || b.icy)) colonizable = true
        }
        var rival = -1
        var worst = 0f
        for (j in list.indices) {
            if (j == k || !list[j].alive) continue
            if (rel[k][j] < worst) { worst = rel[k][j]; rival = j }
        }
        if (rival < 0) for (j in list.indices) if (j != k && list[j].alive) rival = j
        val threat = if (rival >= 0 && popOf(rival) > popOf(k) * 1.4) 0.6f else 0f
        val behind = if (rival >= 0 && popOf(rival) > popOf(k) * 1.2) 0.4f else 0f
        val crowd = if (mine >= 3) 0.3f else 0f

        val s = FloatArray(8)
        s[0] = f.expansion * (if (colonizable) 1.2f + crowd else 0.05f)
        s[1] = f.science * (1.1f + behind)
        s[2] = if (mega) f.science * 0.5f + f.expansion * 0.4f else 0.02f
        s[3] = f.caution * (1.0f + f.loss * 2f + threat)
        s[4] = f.aggression * (0.8f + threat)
        s[5] = if (rival >= 0) (1f - f.aggression) * 0.5f + f.bio * 0.4f + f.expansion * 0.2f else 0.05f
        s[6] = if (rival >= 0 && !anyConflict() && rel[k][rival] < 0.1f)
            f.aggression * (1.2f + list[rival].dominance / 2400f) else 0.02f
        s[7] = (f.caution * 0.5f + f.bio * 0.7f) * (if (ruined > 0) 1.7f else 0.35f)

        s[f.intent] *= 1.15f
        for (i in 0 until 8) s[i] *= 0.92f + rng.nextFloat() * 0.16f

        var best = 0
        var second = 0
        for (i in 0 until 8) {
            if (s[i] > s[best]) { second = best; best = i }
            else if (s[i] > s[second] && i != best) second = i
        }
        val chosen = if (rng.nextFloat() < 0.05f) second else best
        val old = f.intent
        f.intent = chosen
        if (chosen == 6 && rival >= 0) {
            f.target = rival
            f.conflict = 480f + rng.nextFloat() * 420f
        }
        if (f.loss > 0.3f) {
            f.caution = min(0.95f, f.caution + 0.05f)
            f.aggression = max(0.05f, f.aggression - 0.03f)
        }
        if (chosen != old) {
            f.reason = when {
                f.loss > 0.3f -> "потери " + (f.loss * 40f).toInt() + "%"
                threat > 0f -> "угроза соседа"
                behind > 0f -> "отставание"
                ruined > 0 && chosen == 7 -> "руины"
                crowd > 0f -> "перенаселение"
                chosen == 6 -> "вражда"
                else -> "смена курса"
            }
            pendBody = f.home
            pendTitle = f.name
            pendText = names[old] + " → " + names[chosen] + " · " + f.reason
        }
    }

    private fun apply(k: Int, dt: Float, sim: OrrerySim, st: OrreryPrefs.Settings) {
        val f = list[k]
        val rate = dt * st.civSpeed
        for (i in sim.bodies.indices) {
            if (owners[i] != k) continue
            val b = sim.bodies[i]
            if (b.destroyed || b.pop <= 0.0) continue
            when (f.intent) {
                0 -> b.colonyTimer -= rate * 0.9f
                1 -> b.prog += rate / 900f * 0.55f
                2 -> if (b.stage >= 6) sim.swarm = min(1f, sim.swarm + rate * 0.0022f)
                3 -> b.aggression = max(0.15f, b.aggression - rate * 0.004f)
                4 -> b.aggression = min(0.95f, b.aggression + rate * 0.004f)
                5 -> {
                    val cap = caps[min(b.stage + 1, 7)]
                    b.pop += (cap - b.pop) * rate * 0.012f
                    for (j in list.indices) if (j != k && list[j].alive) {
                        rel[k][j] = min(1f, rel[k][j] + rate * 0.0016f)
                        rel[j][k] = rel[k][j]
                    }
                }
                7 -> {
                    b.scars = max(0f, b.scars - rate * 0.02f)
                    if (b.ruined && b.scars <= 0.05f) b.ruined = false
                    val cap = caps[min(b.stage + 1, 7)]
                    b.pop += (cap - b.pop) * rate * 0.008f
                }
            }
        }
    }

    fun store(c: Context) {
        val p = c.getSharedPreferences("orrery_factions", Context.MODE_PRIVATE)
        val e = p.edit()
        e.putInt("n", list.size)
        for (i in list.indices) {
            val f = list[i]
            val s = StringBuilder()
            s.append(f.name).append('|').append(f.home).append('|').append(f.color).append('|')
            s.append(f.expansion).append('|').append(f.science).append('|')
            s.append(f.aggression).append('|').append(f.caution).append('|').append(f.bio).append('|')
            s.append(f.intent).append('|').append(f.decisionIn).append('|')
            s.append(if (f.alive) 1 else 0).append('|').append(f.target).append('|')
            s.append(f.conflict).append('|').append(f.dominance)
            e.putString("f$i", s.toString())
        }
        for (i in 0 until 3) for (j in 0 until 3) e.putFloat("r$i$j", rel[i][j])
        e.apply()
    }

    fun restore(c: Context, sim: OrrerySim) {
        val p = c.getSharedPreferences("orrery_factions", Context.MODE_PRIVATE)
        val n = p.getInt("n", 0)
        if (n <= 0 || n > 3) return
        list.clear()
        for (i in 0 until n) {
            val line = p.getString("f$i", null) ?: continue
            val a = line.split('|')
            if (a.size < 14) continue
            val home = a[1].toIntOrNull() ?: continue
            if (home !in sim.bodies.indices) continue
            val f = Faction(
                a[0], home, a[2].toIntOrNull() ?: palette[i],
                a[3].toFloatOrNull() ?: 0.5f, a[4].toFloatOrNull() ?: 0.5f,
                a[5].toFloatOrNull() ?: 0.5f, a[6].toFloatOrNull() ?: 0.5f,
                a[7].toFloatOrNull() ?: 0.5f
            )
            f.intent = (a[8].toIntOrNull() ?: 7).coerceIn(0, 7)
            f.decisionIn = a[9].toFloatOrNull() ?: 300f
            f.alive = a[10] == "1"
            f.target = a[11].toIntOrNull() ?: -1
            f.conflict = a[12].toFloatOrNull() ?: 0f
            f.dominance = a[13].toFloatOrNull() ?: 0f
            f.lastPop = sim.bodies[home].pop
            list.add(f)
        }
        for (i in 0 until 3) for (j in 0 until 3) rel[i][j] = p.getFloat("r$i$j", 0f)
        recompute(sim)
    }
}
