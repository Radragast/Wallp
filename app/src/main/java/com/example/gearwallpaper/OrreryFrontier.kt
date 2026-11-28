package com.example.gearwallpaper

import android.content.Context
import java.util.Random
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

class FrontierProbe {
    var owner = 0
    var sx = 0f; var sy = 0f
    var tx = 0f; var ty = 0f
    var x = 0f; var y = 0f
    var t = 0f
    var dur = 240f
}

class FrontierArk {
    var owner = 0
    var sx = 0f; var sy = 0f
    var tx = 0f; var ty = 0f
    var x = 0f; var y = 0f
    var t = 0f
    var dur = 700f
    var target = 0
    var phase = 0
    var transit = 0f
}

class FrontierStar(
    val star: String,
    val ly: Float,
    val angle: Float,
    var settled: Boolean = false,
    var assigned: Boolean = false
)

class OrreryFrontier {
    private val rng = Random(90210L)
    private val catalog = arrayOf(
        "Проксима Центавра" to 4.24f,
        "Звезда Барнарда" to 5.96f,
        "Вольф 359" to 7.86f,
        "Лаланд 21185" to 8.31f,
        "Сириус" to 8.60f,
        "Росс 154" to 9.70f,
        "Эпсилон Эридана" to 10.50f,
        "Тау Кита" to 11.90f,
        "Глизе 581" to 20.40f,
        "TRAPPIST-1" to 40.70f
    )

    val discoveries = ArrayList<FrontierStar>()
    var probe: FrontierProbe? = null
    var ark: FrontierArk? = null
    var arksSent = 0
    var settled = 0
    var probesLost = 0

    private var probeWait = 150f
    private var arkWait = 420f
    private var cardLock = 0f
    private var pendTitle = ""
    private var pendText = ""
    private var pendBody = -1

    private val vol = Array(3) { FloatArray(3) }
    private val mark = Array(3) { BooleanArray(3) }
    val linkA = IntArray(4)
    val linkB = IntArray(4)
    val linkVol = FloatArray(4)
    var linkN = 0

    private fun say(body: Int, title: String, text: String) {
        pendBody = body
        pendTitle = title
        pendText = text
    }

    fun update(dt: Float, sim: OrrerySim, f: OrreryFactions, st: OrreryPrefs.Settings) {
        val rate = dt * st.civSpeed
        cardLock = max(0f, cardLock - dt)

        linkN = 0
        for (i in 0 until f.count()) {
            for (j in i + 1 until f.count()) {
                val rel = f.relation(i, j)
                if (f.aliveOf(i) && f.aliveOf(j) && rel > 0.35f) {
                    vol[i][j] = max(0f, min(1f, vol[i][j] + rate * 0.006f * rel))
                    if (linkN < 4) {
                        linkA[linkN] = i; linkB[linkN] = j; linkVol[linkN] = vol[i][j]; linkN++
                    }
                    if (vol[i][j] > 0.6f && !mark[i][j]) {
                        mark[i][j] = true
                        say(f.homeOf(i), "ТОРГОВЛЯ",
                            f.nameOf(i) + " ↔ " + f.nameOf(j) + " · оборот вырос")
                    }
                } else {
                    vol[i][j] = max(0f, vol[i][j] - rate * 0.004f)
                    if (vol[i][j] < 0.2f) mark[i][j] = false
                }
            }
        }

        probeWait -= rate
        val p = probe
        if (p != null) {
            p.t += dt
            val u = (p.t / p.dur).coerceIn(0f, 1f)
            val e = u * u * (3f - 2f * u)
            val dx = p.tx - p.sx; val dy = p.ty - p.sy
            val nl = max(1f, sqrt(dx * dx + dy * dy))
            val bulge = sin(u * Math.PI).toFloat() * 130f
            p.x = p.sx + dx * e - dy / nl * bulge
            p.y = p.sy + dy * e + dx / nl * bulge
            if (u >= 1f) {
                probe = null
                probeWait = 260f + rng.nextFloat() * 200f
                val chance = 0.45f + f.scienceOf(p.owner) * 0.4f
                if (rng.nextFloat() < chance && discoveries.size < catalog.size) {
                    val c = catalog[discoveries.size]
                    discoveries.add(FrontierStar(c.first, c.second, rng.nextFloat() * 6.283f))
                    say(f.homeOf(p.owner), "РАЗВЕДКА",
                        "Цель найдена: " + c.first + " · " + c.second + " св. лет")
                } else {
                    probesLost++
                    say(f.homeOf(p.owner), "РАЗВЕДКА", "Зонд потерян · сигнал прерван")
                }
            }
        } else if (probeWait <= 0f) {
            for (i in 0 until f.count()) {
                if (!f.aliveOf(i)) continue
                val h = sim.bodies[f.homeOf(i)]
                if (h.destroyed || h.stage < 5) continue
                val it = f.intentOf(i)
                if (it != 1 && it != 0) continue
                val a = rng.nextFloat() * 6.283f
                val r = 1150f + rng.nextFloat() * 450f
                val q = FrontierProbe()
                q.owner = i
                q.sx = h.x; q.sy = h.y
                q.tx = kotlin.math.cos(a) * r; q.ty = kotlin.math.sin(a) * r
                q.x = h.x; q.y = h.y
                q.dur = 200f + rng.nextFloat() * 120f
                probe = q
                say(f.homeOf(i), "РАЗВЕДКА", f.nameOf(i) + " отправляет зонд за границу системы")
                break
            }
            if (probe == null) probeWait = 200f
        }

        arkWait -= rate
        val a = ark
        if (a != null) {
            if (a.phase == 0) {
                a.t += dt
                val u = (a.t / a.dur).coerceIn(0f, 1f)
                val e = u * u * (3f - 2f * u)
                val dx = a.tx - a.sx; val dy = a.ty - a.sy
                val nl = max(1f, sqrt(dx * dx + dy * dy))
                val bulge = sin(u * Math.PI).toFloat() * 220f
                a.x = a.sx + dx * e - dy / nl * bulge
                a.y = a.sy + dy * e + dx / nl * bulge
                if (u >= 1f) {
                    a.phase = 1
                    a.transit = 900f + rng.nextFloat() * 600f
                }
            } else {
                a.transit -= rate
                if (a.transit <= 0f) {
                    val d = discoveries.getOrNull(a.target)
                    if (d != null) {
                        d.settled = true
                        settled++
                        say(f.homeOf(a.owner), "КОВЧЕГ",
                            "Достигнута " + d.star + " · колония основана")
                    }
                    ark = null
                    arkWait = 700f + rng.nextFloat() * 500f
                }
            }
        } else if (arkWait <= 0f) {
            var idx = -1
            for (k in discoveries.indices) {
                if (!discoveries[k].settled && !discoveries[k].assigned) { idx = k; break }
            }
            if (idx >= 0) {
                for (i in 0 until f.count()) {
                    if (!f.aliveOf(i)) continue
                    val h = sim.bodies[f.homeOf(i)]
                    if (h.destroyed || h.stage < 6) continue
                    if (f.expansionOf(i) < 0.4f) continue
                    val d = discoveries[idx]
                    val r = 3200f + rng.nextFloat() * 900f
                    val q = FrontierArk()
                    q.owner = i
                    q.target = idx
                    q.sx = h.x; q.sy = h.y
                    q.tx = kotlin.math.cos(d.angle) * r
                    q.ty = kotlin.math.sin(d.angle) * r
                    q.x = h.x; q.y = h.y
                    q.dur = 620f + rng.nextFloat() * 220f
                    ark = q
                    d.assigned = true
                    arksSent++
                    say(f.homeOf(i), "КОВЧЕГ",
                        f.nameOf(i) + " отправляет ковчег к " + d.star)
                    break
                }
            }
            if (ark == null) arkWait = 400f
        }

        if (pendBody >= 0 && cardLock <= 0f && sim.cardBody < 0) {
            sim.cardBody = pendBody
            sim.cardTitle = pendTitle
            sim.cardText = pendText
            sim.cardTag = "ФРОНТИР"
            sim.cardAge = 0f
            cardLock = 120f + rng.nextFloat() * 60f
            pendBody = -1
        }
    }

    fun store(c: Context) {
        val e = c.getSharedPreferences("orrery_frontier", Context.MODE_PRIVATE).edit()
        e.putInt("sent", arksSent).putInt("settled", settled).putInt("lost", probesLost)
        e.putFloat("pw", probeWait).putFloat("aw", arkWait)
        e.putInt("dn", discoveries.size)
        for (i in discoveries.indices) {
            val d = discoveries[i]
            e.putString("d$i", d.star + "|" + d.ly + "|" + d.angle + "|" +
                (if (d.settled) 1 else 0) + "|" + (if (d.assigned) 1 else 0))
        }
        val p = probe
        if (p == null) e.putString("probe", "")
        else e.putString("probe", "${p.owner}|${p.sx}|${p.sy}|${p.tx}|${p.ty}|${p.t}|${p.dur}")
        val a = ark
        if (a == null) e.putString("ark", "")
        else e.putString("ark", "${a.owner}|${a.sx}|${a.sy}|${a.tx}|${a.ty}|${a.t}|${a.dur}|${a.target}|${a.phase}|${a.transit}")
        for (i in 0 until 3) for (j in 0 until 3) {
            e.putFloat("v$i$j", vol[i][j])
            e.putBoolean("m$i$j", mark[i][j])
        }
        e.apply()
    }

    fun restore(c: Context, sim: OrrerySim, f: OrreryFactions) {
        val p = c.getSharedPreferences("orrery_frontier", Context.MODE_PRIVATE)
        arksSent = p.getInt("sent", 0)
        settled = p.getInt("settled", 0)
        probesLost = p.getInt("lost", 0)
        probeWait = p.getFloat("pw", 150f)
        arkWait = p.getFloat("aw", 420f)
        discoveries.clear()
        val dn = p.getInt("dn", 0)
        for (i in 0 until dn) {
            val s = p.getString("d$i", null) ?: continue
            val a = s.split('|')
            if (a.size < 5) continue
            discoveries.add(FrontierStar(a[0], a[1].toFloatOrNull() ?: 5f,
                a[2].toFloatOrNull() ?: 0f, a[3] == "1", a[4] == "1"))
        }
        val ps = p.getString("probe", "") ?: ""
        if (ps.isNotEmpty()) {
            val a = ps.split('|')
            if (a.size >= 7) {
                val q = FrontierProbe()
                q.owner = a[0].toIntOrNull() ?: 0
                q.sx = a[1].toFloatOrNull() ?: 0f; q.sy = a[2].toFloatOrNull() ?: 0f
                q.tx = a[3].toFloatOrNull() ?: 0f; q.ty = a[4].toFloatOrNull() ?: 0f
                q.t = a[5].toFloatOrNull() ?: 0f; q.dur = a[6].toFloatOrNull() ?: 240f
                q.x = q.sx; q.y = q.sy
                if (q.owner < f.count()) probe = q
            }
        }
        val asr = p.getString("ark", "") ?: ""
        if (asr.isNotEmpty()) {
            val a = asr.split('|')
            if (a.size >= 10) {
                val q = FrontierArk()
                q.owner = a[0].toIntOrNull() ?: 0
                q.sx = a[1].toFloatOrNull() ?: 0f; q.sy = a[2].toFloatOrNull() ?: 0f
                q.tx = a[3].toFloatOrNull() ?: 0f; q.ty = a[4].toFloatOrNull() ?: 0f
                q.t = a[5].toFloatOrNull() ?: 0f; q.dur = a[6].toFloatOrNull() ?: 700f
                q.target = a[7].toIntOrNull() ?: 0
                q.phase = a[8].toIntOrNull() ?: 0
                q.transit = a[9].toFloatOrNull() ?: 900f
                q.x = q.sx; q.y = q.sy
                if (q.owner < f.count() && q.target < discoveries.size) ark = q
            }
        }
        for (i in 0 until 3) for (j in 0 until 3) {
            vol[i][j] = p.getFloat("v$i$j", 0f)
            mark[i][j] = p.getBoolean("m$i$j", false)
        }
    }
}
