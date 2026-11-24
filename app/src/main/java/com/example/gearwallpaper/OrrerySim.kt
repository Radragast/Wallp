package com.example.gearwallpaper

import android.graphics.Color
import java.util.Random
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class SimBody(
    val name: String,
    val parent: Int,
    val orbit: Float,
    val period: Double,
    val l0: Double,
    val radius: Float,
    val c1: Int,
    val c2: Int,
    val ring: Boolean,
    val icy: Boolean
) {
    var x = 0f
    var y = 0f
    var stage = 0
    var pop = 0.0
    var prog = 0f
    var scars = 0f
    var ruined = false
    var destroyed = false
    var rebuild = 0f
    var colonyOf = -1
    var ringBuilt = false
    var elevator = false
    var aggression = 0.5f
    var flash = 0f
    var flashColor = 0
    var colonyTimer = 40f
}

class SimEvent {
    var type = 0
    var t = 0f
    var dur = 12f
    var a = 0
    var b = 0
    var seed = 0L
    var ecc = 0.97f
    var peri = 95f
    var rot = 0f
    var nu = -2.4f
    var behavior = 0
    var applied = false
    var x = 0f
    var y = 0f
}

class OrrerySim {
    val bodies = ArrayList<SimBody>()
    val events = ArrayList<SimEvent>()
    var simDays = 0.0
    var swarm = 0f
    var kIndex = 0f
    var totalPop = 0.0
    var colonies = 0
    var fact = ""
    var factIsSim = false
    var callout = -1
    var nextEvent = 30f
    var pendingRandom = false
    var pendingCat = false
    var pendingRestore = false
    var civSpeed = 1f
    var eventRate = 1f
    var eventsOn = true
    var catOn = true
    private var factTimer = 6f
    private var calloutTimer = 12f
    private val rng = Random(20261124L)
    private val caps = doubleArrayOf(0.0, 1e3, 1e5, 1e7, 1e10, 1e11, 1e12, 1e13)

    private val realFacts = arrayOf(
        "Свет Солнца идёт до Земли 8 мин 20 с",
        "Земля движется по орбите со скоростью 29.8 км/с",
        "Юпитер тяжелее всех остальных планет вместе взятых",
        "Подлёдный океан Энцелада питает гейзеры на южном полюсе",
        "Ганимед — единственная луна с собственным магнитным полем",
        "Титан — единственная луна с плотной атмосферой",
        "Ионный хвост кометы всегда направлен от Солнца",
        "У перигелия комета ускоряется по второму закону Кеплера",
        "Оборот Ио вокруг Юпитера занимает 1.77 суток",
        "Средняя плотность Сатурна меньше плотности воды",
        "На Ио действуют приливы Юпитера — вулканы выбрасывают серу",
        "Сутки на Венере длиннее её года"
    )

    init {
        add("Меркурий", -1, 130f, 87.969, 252.25, 5f, Color.rgb(168, 164, 158), Color.rgb(70, 68, 64), false, false)
        add("Венера", -1, 200f, 224.701, 181.98, 8f, Color.rgb(236, 208, 152), Color.rgb(110, 84, 44), false, false)
        add("Земля", -1, 275f, 365.256, 100.46, 9f, Color.rgb(86, 156, 226), Color.rgb(18, 52, 96), false, false)
        add("Луна", 2, 30f, 27.321, 40.0, 3f, Color.rgb(198, 198, 196), Color.rgb(74, 74, 72), false, false)
        add("Марс", -1, 360f, 686.980, 355.45, 6.5f, Color.rgb(214, 112, 70), Color.rgb(88, 36, 20), false, false)
        add("Юпитер", -1, 520f, 4332.59, 34.40, 24f, Color.rgb(226, 190, 142), Color.rgb(96, 66, 40), false, false)
        add("Ио", 5, 42f, 1.769, 10.0, 3.2f, Color.rgb(238, 214, 120), Color.rgb(110, 82, 26), false, false)
        add("Европа", 5, 56f, 3.551, 120.0, 3.0f, Color.rgb(216, 228, 236), Color.rgb(80, 100, 120), false, true)
        add("Ганимед", 5, 72f, 7.155, 230.0, 4.2f, Color.rgb(186, 176, 164), Color.rgb(70, 64, 58), false, true)
        add("Каллисто", 5, 90f, 16.689, 300.0, 4.0f, Color.rgb(150, 140, 132), Color.rgb(54, 48, 44), false, true)
        add("Сатурн", -1, 700f, 10759.22, 49.94, 20f, Color.rgb(232, 210, 158), Color.rgb(104, 84, 46), true, false)
        add("Титан", 10, 62f, 15.945, 80.0, 4.0f, Color.rgb(226, 168, 84), Color.rgb(96, 60, 20), false, true)
        add("Энцелад", 10, 36f, 1.370, 200.0, 2.4f, Color.rgb(238, 246, 252), Color.rgb(110, 130, 148), false, true)
        bodies[2].stage = 4
        bodies[2].pop = 8.1e9
        bodies[2].aggression = 0.7f
        fact = realFacts[0]
    }

    private fun add(n: String, p: Int, o: Float, per: Double, l: Double, r: Float, a: Int, b: Int, ring: Boolean, icy: Boolean) {
        bodies.add(SimBody(n, p, o, per, l, r, a, b, ring, icy))
    }

    fun bodyIndex(name: String): Int {
        for (i in bodies.indices) if (bodies[i].name == name) return i
        return -1
    }

    fun update(dt: Float, astroSpeed: Float) {
        simDays += (dt * astroSpeed).toDouble()
        for (b in bodies) {
            val ang = Math.toRadians((b.l0 + 360.0 * simDays / b.period) % 360.0)
            val ox = if (b.parent >= 0) bodies[b.parent].x else 0f
            val oy = if (b.parent >= 0) bodies[b.parent].y else 0f
            b.x = ox + (b.orbit * cos(ang)).toFloat()
            b.y = oy + (b.orbit * sin(ang)).toFloat()
        }

        totalPop = 0.0
        colonies = 0
        var mega = false
        for (i in bodies.indices) {
            val b = bodies[i]
            b.scars = max(0f, b.scars - dt * 0.008f)
            b.flash = max(0f, b.flash - dt * 1.5f)
            if (b.destroyed) continue
            if (b.colonyOf >= 0) colonies++
            if (b.pop > 0.0) {
                val cap = caps[min(b.stage + 1, 7)]
                b.pop += (cap - b.pop) * dt * 0.02f * civSpeed
                b.prog += dt * civSpeed / 900f
                if (b.prog >= 1f && b.stage < 7) {
                    b.prog = 0f
                    b.stage++
                    if (b.stage == 6) b.ringBuilt = true
                    if (b.stage == 7) b.elevator = true
                    b.flash = 1f
                    b.flashColor = Color.rgb(120, 240, 180)
                    fact = "SIM · " + b.name + ": " + stageName(b.stage)
                    factIsSim = true
                    factTimer = 14f
                }
                if (b.stage in 4..5 && rng.nextFloat() < dt * 0.0009f * b.aggression * civSpeed) war(i)
                if (b.stage >= 5) {
                    b.colonyTimer -= dt * civSpeed
                    if (b.colonyTimer <= 0f) {
                        b.colonyTimer = 70f + rng.nextFloat() * 90f
                        tryColonize(i)
                    }
                }
                if (b.stage >= 7) mega = true
            }
            totalPop += b.pop
        }
        if (mega) swarm = min(1f, swarm + dt * 0.0035f * civSpeed)
        kIndex = ((log10(max(1.0, totalPop * 2e4)) - 6.0) / 10.0).toFloat()

        var i = events.size - 1
        while (i >= 0) {
            val e = events[i]
            e.t += dt
            step(e, dt)
            if (e.t >= e.dur) {
                finish(e)
                events.removeAt(i)
            }
            i--
        }

        if (eventsOn) {
            nextEvent -= dt * eventRate
            if (nextEvent <= 0f) {
                nextEvent = 80f + rng.nextFloat() * 140f
                spawn(false)
            }
        }
        if (pendingRandom) { pendingRandom = false; spawn(false) }
        if (pendingCat) { pendingCat = false; spawn(true) }
        if (pendingRestore) { pendingRestore = false; restoreAll() }

        factTimer -= dt
        if (factTimer <= 0f) {
            factTimer = 15f
            factIsSim = !factIsSim
            fact = if (factIsSim) simStat() else realFacts[rng.nextInt(realFacts.size)]
        }
        calloutTimer -= dt
        if (calloutTimer <= 0f) {
            if (callout >= 0) {
                callout = -1
                calloutTimer = 45f + rng.nextFloat() * 50f
            } else {
                val c = inhabited()
                if (c >= 0) { callout = c; calloutTimer = 8f } else calloutTimer = 25f
            }
        }
    }

    fun stageName(s: Int) = when (s) {
        0 -> "стерильный мир"; 1 -> "микробная жизнь"; 2 -> "сложная жизнь"
        3 -> "разумная жизнь"; 4 -> "индустриальная эпоха"; 5 -> "выход в космос"
        6 -> "межпланетная цивилизация"; else -> "мегаструктуры · K II"
    }

    private fun simStat(): String {
        val p = totalPop
        val ps = when {
            p >= 1e12 -> String.format("%.1f трлн", p / 1e12)
            p >= 1e9 -> String.format("%.1f млрд", p / 1e9)
            p >= 1e6 -> String.format("%.1f млн", p / 1e6)
            p >= 1e3 -> String.format("%.0f тыс", p / 1e3)
            else -> String.format("%.0f", p)
        }
        return when (rng.nextInt(4)) {
            0 -> "SIM · население системы " + ps
            1 -> "SIM · индекс Кардашёва K " + String.format("%.2f", kIndex)
            2 -> "SIM · колоний " + colonies + " · рой Дайсона " + (swarm * 100).toInt() + "%"
            else -> "SIM · живых миров " + bodies.count { it.pop > 0.0 }
        }
    }

    private fun inhabited(): Int {
        val list = ArrayList<Int>()
        for (i in bodies.indices) if (bodies[i].pop > 1e3 && !bodies[i].destroyed) list.add(i)
        if (list.isEmpty()) return -1
        return list[rng.nextInt(list.size)]
    }

    private fun war(i: Int) {
        val b = bodies[i]
        b.pop *= 0.03 + rng.nextDouble() * 0.14
        b.stage = max(0, b.stage - 2)
        b.ruined = true
        b.scars = 1f
        b.flash = 1f
        b.flashColor = Color.rgb(255, 90, 60)
        if (rng.nextFloat() < 0.25f) { b.pop = 0.0; b.stage = 0 }
        fact = "SIM · " + b.name + ": коллапс цивилизации"
        factIsSim = true
        factTimer = 14f
    }

    private fun tryColonize(from: Int) {
        val list = ArrayList<Int>()
        for (i in bodies.indices) {
            val b = bodies[i]
            if (i == from || b.destroyed || b.colonyOf >= 0 || b.stage >= 5) continue
            if (b.name == "Марс" || b.name == "Луна" || b.icy) list.add(i)
        }
        if (list.isEmpty()) return
        val to = list[rng.nextInt(list.size)]
        val e = SimEvent()
        e.type = 2
        e.a = from
        e.b = to
        e.dur = 16f
        e.seed = rng.nextLong()
        events.add(e)
    }

    fun restoreAll() {
        for (i in bodies.indices) {
            val b = bodies[i]
            if (b.destroyed) genesis(i)
            else if (b.ruined || b.scars > 0.2f) {
                b.scars = 0f
                b.ruined = false
                b.flash = 1f
                b.flashColor = Color.rgb(120, 240, 200)
                if (b.pop <= 0.0) { b.stage = 1; b.pop = 1e3 }
            }
        }
    }

    private fun genesis(i: Int) {
        val e = SimEvent()
        e.type = 15
        e.a = i
        e.dur = 70f
        e.seed = rng.nextLong()
        events.add(e)
        fact = "SIM · протокол «Генезис»: сборка " + bodies[i].name
        factIsSim = true
        factTimer = 14f
    }

    fun spawn(catastrophe: Boolean) {
        val e = SimEvent()
        e.seed = rng.nextLong()
        e.rot = rng.nextFloat() * 6.2832f
        e.a = rng.nextInt(bodies.size)
        if (catastrophe && catOn) {
            e.type = when (rng.nextInt(4)) { 0 -> 4; 1 -> 5; 2 -> 8; else -> 9 }
            e.dur = 16f
        } else {
            e.type = when (rng.nextInt(11)) {
                0 -> 0; 1 -> 1; 2 -> 3; 3 -> 6; 4 -> 7; 5 -> 10
                6 -> 11; 7 -> 12; 8 -> 13; 9 -> 14; else -> 16
            }
            e.dur = when (e.type) { 0 -> 30f; 1 -> 26f; 3 -> 20f; else -> 14f }
            if (e.type == 0) { e.ecc = 0.97f; e.peri = 95f; e.nu = -2.5f }
            if (e.type == 1) { e.ecc = 1f; e.peri = 80f; e.nu = -2.4f }
            if (e.type == 3) e.behavior = rng.nextInt(3)
        }
        events.add(e)
    }

    private fun step(e: SimEvent, dt: Float) {
        if (e.type == 0 || e.type == 1) {
            val r = e.peri * (1f + e.ecc) / max(0.05f, 1f + e.ecc * cos(e.nu))
            e.x = r * cos(e.nu + e.rot)
            e.y = r * sin(e.nu + e.rot)
            e.nu += (4200f / max(400f, r * r)) * dt
        }
        if (e.type == 15) bodies[e.a].rebuild = (e.t / e.dur).coerceIn(0f, 1f)
        if (!e.applied && e.t / e.dur > 0.55f) {
            e.applied = true
            val b = bodies[e.a]
            when (e.type) {
                5 -> {
                    b.scars = 1f; b.pop *= 0.05; b.stage = max(0, b.stage - 2)
                    b.ruined = true; b.flash = 1f; b.flashColor = Color.rgb(255, 150, 70)
                }
                9 -> {
                    b.scars = 1f; b.pop *= 0.001; b.stage = max(0, b.stage - 3)
                    b.ruined = true; b.flash = 1f; b.flashColor = Color.rgb(220, 245, 255)
                }
                4 -> {
                    b.scars = 1f; b.pop *= 0.02; b.stage = max(0, b.stage - 2)
                    b.ruined = true; b.flash = 1f; b.flashColor = Color.rgb(160, 220, 255)
                }
                8 -> {
                    if (catOn && rng.nextFloat() < 0.5f && !b.destroyed) {
                        b.destroyed = true; b.pop = 0.0; b.stage = 0
                        b.colonyOf = -1; b.ringBuilt = false; b.elevator = false
                        genesis(e.a)
                    } else {
                        b.scars = 1f; b.pop *= 0.1; b.flash = 1f
                        b.flashColor = Color.rgb(255, 190, 110)
                    }
                }
            }
        }
    }

    private fun finish(e: SimEvent) {
        when (e.type) {
            2 -> {
                val t = bodies[e.b]
                if (!t.destroyed) {
                    t.colonyOf = e.a
                    t.stage = 5
                    t.pop = 1e5
                    t.ruined = false
                    t.flash = 1f
                    t.flashColor = Color.rgb(140, 230, 255)
                    fact = "SIM · колония основана: " + t.name
                    factIsSim = true
                    factTimer = 14f
                }
            }
            3 -> {
                val b = bodies[e.a]
                when (e.behavior) {
                    1 -> { b.pop *= 0.85; b.flash = 1f; b.flashColor = Color.rgb(180, 120, 255) }
                    2 -> if (b.stage == 0 && !b.destroyed) {
                        b.stage = 1; b.pop = 1e3; b.flash = 1f
                        b.flashColor = Color.rgb(120, 255, 190)
                        fact = "SIM · засеян мир: " + b.name
                        factIsSim = true; factTimer = 14f
                    }
                }
            }
            15 -> {
                val b = bodies[e.a]
                b.destroyed = false
                b.rebuild = 0f
                b.scars = 0.6f
                b.ruined = false
                b.stage = 1
                b.pop = 1e3
                b.flash = 1f
                b.flashColor = Color.rgb(150, 255, 210)
                fact = "SIM · мир восстановлен: " + b.name
                factIsSim = true
                factTimer = 14f
            }
        }
    }
}
