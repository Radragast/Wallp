package com.example.gearwallpaper

import android.graphics.Color
import java.util.Locale
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
    var moonSpeed = 0.1f
    var swarm = 0f
    var kIndex = 0f
    var totalPop = 0.0
    var colonies = 0
    var nextEvent = 30f
    var pendingRandom = false
    var pendingCat = false
    var pendingRestore = false
    var civSpeed = 1f
    var eventRate = 1f
    var eventsOn = true
    var catOn = true

    var cardBody = -1
    var cardTitle = ""
    var cardText = ""
    var cardTag = ""
    var cardAge = 0f
    val cardDur = 11f
    private var cardGap = 35f
    private var pendBody = -1
    private var pendText = ""
    private var pendTag = ""

    private val rng = Random(20261124L)
    private val caps = doubleArrayOf(0.0, 1e3, 1e5, 1e7, 1e10, 1e11, 1e12, 1e13)

    init {
        add("Меркурий", -1, 130f, 87.969, 252.25, 5f, Color.rgb(168, 164, 158), Color.rgb(70, 68, 64), false, false)
        add("Венера", -1, 200f, 224.701, 181.98, 8f, Color.rgb(236, 208, 152), Color.rgb(110, 84, 44), false, false)
        add("Земля", -1, 275f, 365.256, 100.46, 9f, Color.rgb(86, 156, 226), Color.rgb(18, 52, 96), false, false)
        add("Луна", 2, 30f, 27.321, 40.0, 3f, Color.rgb(198, 198, 196), Color.rgb(74, 74, 72), false, false)
        add("Марс", -1, 360f, 686.980, 355.45, 6.5f, Color.rgb(214, 112, 70), Color.rgb(88, 36, 20), false, false)
        add("Юпитер", -1, 520f, 4332.59, 34.40, 24f, Color.rgb(226, 190, 142), Color.rgb(96, 66, 40), false, false)
        add("Ио", 5, 44f, 1.769, 10.0, 3.2f, Color.rgb(238, 214, 120), Color.rgb(110, 82, 26), false, false)
        add("Европа", 5, 58f, 3.551, 120.0, 3.0f, Color.rgb(216, 228, 236), Color.rgb(80, 100, 120), false, true)
        add("Ганимед", 5, 74f, 7.155, 230.0, 4.2f, Color.rgb(186, 176, 164), Color.rgb(70, 64, 58), false, true)
        add("Каллисто", 5, 92f, 16.689, 300.0, 4.0f, Color.rgb(150, 140, 132), Color.rgb(54, 48, 44), false, true)
        add("Сатурн", -1, 700f, 10759.22, 49.94, 20f, Color.rgb(232, 210, 158), Color.rgb(104, 84, 46), true, false)
        add("Титан", 10, 66f, 15.945, 80.0, 4.0f, Color.rgb(226, 168, 84), Color.rgb(96, 60, 20), false, true)
        add("Энцелад", 10, 38f, 1.370, 200.0, 2.4f, Color.rgb(238, 246, 252), Color.rgb(110, 130, 148), false, true)
        bodies[2].stage = 4
        bodies[2].pop = 8.1e9
        bodies[2].aggression = 0.7f
    }

    private fun add(n: String, p: Int, o: Float, per: Double, l: Double, r: Float, a: Int, b: Int, ring: Boolean, icy: Boolean) {
        bodies.add(SimBody(n, p, o, per, l, r, a, b, ring, icy))
    }

    fun stageName(s: Int) = when (s) {
        0 -> "стерильный мир"; 1 -> "микробная жизнь"; 2 -> "сложная жизнь"
        3 -> "разумная жизнь"; 4 -> "индустриальная эпоха"; 5 -> "выход в космос"
        6 -> "межпланетная цивилизация"; else -> "мегаструктуры · K II"
    }

    fun popText(p: Double): String = when {
        p >= 1e12 -> String.format(Locale.US, "%.1f трлн", p / 1e12)
        p >= 1e9 -> String.format(Locale.US, "%.1f млрд", p / 1e9)
        p >= 1e6 -> String.format(Locale.US, "%.1f млн", p / 1e6)
        p >= 1e3 -> String.format(Locale.US, "%.0f тыс", p / 1e3)
        else -> String.format(Locale.US, "%.0f", p)
    }

    private fun bodyFact(i: Int): String = when (bodies[i].name) {
        "Меркурий" -> "Год длится 88 суток, а солнечные сутки — 176 земных"
        "Венера" -> "Сутки длиннее года: 243 против 225 земных суток"
        "Земля" -> "Свет Солнца идёт до нас 8 минут 20 секунд"
        "Луна" -> "Удаляется от Земли примерно на 3.8 см каждый год"
        "Марс" -> "Гора Олимп выше Эвереста примерно в 2.5 раза"
        "Юпитер" -> "Тяжелее всех остальных планет вместе взятых"
        "Ио" -> "Самое вулканическое тело Солнечной системы"
        "Европа" -> "Подлёдный океан содержит больше воды, чем все океаны Земли"
        "Ганимед" -> "Крупнее Меркурия и имеет собственное магнитное поле"
        "Каллисто" -> "Древнейшая поверхность: кратеры не стирались 4 млрд лет"
        "Сатурн" -> "Средняя плотность меньше плотности воды"
        "Титан" -> "Единственная луна с плотной атмосферой и метановыми реками"
        else -> "Гейзеры выбрасывают водяной лёд на сотни километров"
    }

    private fun announce(i: Int, text: String, tag: String) {
        pendBody = i
        pendText = text
        pendTag = tag
    }

    private fun openCard() {
        val live = ArrayList<Int>()
        for (i in bodies.indices) if (!bodies[i].destroyed) live.add(i)
        if (live.isEmpty()) { cardGap = 30f; return }
        val weighted = ArrayList<Int>()
        for (i in live) {
            weighted.add(i)
            if (bodies[i].pop > 1e3) { weighted.add(i); weighted.add(i) }
        }
        val i = weighted[rng.nextInt(weighted.size)]
        val b = bodies[i]
        cardBody = i
        cardTitle = b.name.uppercase()
        cardAge = 0f
        if (b.pop > 1e3 && rng.nextFloat() < 0.55f) {
            cardText = "Население " + popText(b.pop) + " · " + stageName(b.stage)
            cardTag = "СИМУЛЯЦИЯ"
        } else {
            cardText = bodyFact(i)
            cardTag = "НАУКА"
        }
    }

    fun update(dt: Float, astroSpeed: Float) {
        simDays += (dt * astroSpeed).toDouble()
        val ms = max(0.02f, moonSpeed).toDouble()
        for (b in bodies) {
            val per = if (b.parent >= 0) b.period / ms else b.period
            val ang = Math.toRadians((b.l0 + 360.0 * simDays / per) % 360.0)
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
                    announce(i, "Новая эпоха: " + stageName(b.stage), "СИМУЛЯЦИЯ")
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
            if (e.t >= e.dur) { finish(e); events.removeAt(i) }
            i--
        }

        if (eventsOn) {
            nextEvent -= dt * eventRate
            if (nextEvent <= 0f) { nextEvent = 80f + rng.nextFloat() * 140f; spawn(false) }
        }
        if (pendingRandom) { pendingRandom = false; spawn(false) }
        if (pendingCat) { pendingCat = false; spawn(true) }
        if (pendingRestore) { pendingRestore = false; restoreAll() }

        if (cardBody >= 0) {
            cardAge += dt
            if (cardAge >= cardDur) { cardBody = -1; cardGap = 80f + rng.nextFloat() * 75f }
        } else if (pendBody >= 0) {
            cardBody = pendBody
            cardTitle = bodies[pendBody].name.uppercase()
            cardText = pendText
            cardTag = pendTag
            cardAge = 0f
            pendBody = -1
        } else {
            cardGap -= dt
            if (cardGap <= 0f) openCard()
        }
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
        announce(i, "Коллапс цивилизации · уцелело " + popText(b.pop), "СИМУЛЯЦИЯ")
    }

    private fun tryColonize(from: Int) {
        val list = ArrayList<Int>()
        for (i in bodies.indices) {
            val b = bodies[i]
            if (i == from || b.destroyed || b.colonyOf >= 0 || b.stage >= 5) continue
            if (b.name == "Марс" || b.name == "Луна" || b.icy) list.add(i)
        }
        if (list.isEmpty()) return
        val e = SimEvent()
        e.type = 2
        e.a = from
        e.b = list[rng.nextInt(list.size)]
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
        announce(i, "Протокол «Генезис»: сборка мира из остатков", "СИМУЛЯЦИЯ")
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
                    announce(e.b, "Колония основана · выход в космос", "СИМУЛЯЦИЯ")
                }
            }
            3 -> {
                val b = bodies[e.a]
                when (e.behavior) {
                    1 -> { b.pop *= 0.85; b.flash = 1f; b.flashColor = Color.rgb(180, 120, 255) }
                    2 -> if (b.stage == 0 && !b.destroyed) {
                        b.stage = 1; b.pop = 1e3; b.flash = 1f
                        b.flashColor = Color.rgb(120, 255, 190)
                        announce(e.a, "Жизнь засеяна извне · микробная эпоха", "СИМУЛЯЦИЯ")
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
                announce(e.a, "Мир восстановлен и снова заселён", "СИМУЛЯЦИЯ")
            }
        }
    }
}
