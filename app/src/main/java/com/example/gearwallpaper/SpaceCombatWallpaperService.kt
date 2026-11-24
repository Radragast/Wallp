package com.example.gearwallpaper

import android.graphics.*
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.service.wallpaper.WallpaperService
import android.view.Choreographer
import android.view.SurfaceHolder
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private class FlightStar {
    var x = 0f
    var y = 0f
    var z = 0f
    var b = 0f
}

private class FlightParticle {
    var x = 0f
    var y = 0f
    var z = 0f
    var vx = 0f
    var vy = 0f
    var vz = 0f
    var life = 0f
    var max = 1f
    var size = 1f
    var color = 0
    var chunk = false
    var rot = 0f
    var vr = 0f
}

private data class FlightPlanet(
    val x: Float, val y: Float, val z: Float, val r: Float,
    val c1: Int, val c2: Int,
    val ring: Boolean, val atmo: Boolean, val bands: Boolean,
    val seed: Long,
    val dead: Boolean = false, val kind: Int = 0, val age: Float = 0f
)

class SpaceCombatWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = FlightEngine()

    inner class FlightEngine : Engine(), Choreographer.FrameCallback {
        private val minFrameNanos = 16_000_000L
        @Volatile private var visible = false
        @Volatile private var surfaceValid = false
        @Volatile private var surfaceW = 0
        @Volatile private var surfaceH = 0
        @Volatile private var layoutDirty = true

        private val startTime = System.nanoTime()
        private val threadLock = Any()
        private var renderThread: HandlerThread? = null
        private var renderHandler: Handler? = null
        private var choreographer: Choreographer? = null
        private var frameScheduled = false
        private var lastFrameNanos = 0L
        private var lastT = 0.0

        private var density = 1f
        private var focal = 1000f
        private var camX = 0f
        private var camY = 0f
        private var camZ = 0f
        private var roll = 0f
        private var shake = 0f
        private val baseSpeed = 1500f
        private var warpSpeed = 0f
        private var warpT = 0f
        private var warpActive = false
        private var swapped = false
        private var started = false
        private var systemIndex = 0

        private val stars = mutableListOf<FlightStar>()
        private val planets = mutableListOf<FlightPlanet>()
        private val parts = mutableListOf<FlightParticle>()
        private val rng = Random(9L)

        private val bgPaint = Paint()
        private val nebA = Paint()
        private val nebB = Paint()
        private val vig = Paint()
        private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
        private val line = Paint(Paint.ANTI_ALIAS_FLAG)
        private val body = Paint(Paint.ANTI_ALIAS_FLAG)
        private val ringP = Paint(Paint.ANTI_ALIAS_FLAG)
        private val hud = Paint(Paint.ANTI_ALIAS_FLAG)
        private val bar = Paint()
        private val clip = Path()
        private val chunkPath = Path()

        override fun onVisibilityChanged(v: Boolean) {
            visible = v
            if (v) startRendering() else stopRendering()
        }
        override fun onSurfaceCreated(h: SurfaceHolder) {
            super.onSurfaceCreated(h); surfaceValid = true
        }
        override fun onSurfaceChanged(h: SurfaceHolder, f: Int, w: Int, hh: Int) {
            super.onSurfaceChanged(h, f, w, hh)
            surfaceW = w; surfaceH = hh; layoutDirty = true
            if (visible) startRendering()
        }
        override fun onSurfaceDestroyed(h: SurfaceHolder) {
            super.onSurfaceDestroyed(h); surfaceValid = false; stopRendering()
        }
        override fun onDestroy() {
            super.onDestroy(); visible = false; stopRendering()
        }

        private fun startRendering() {
            synchronized(threadLock) {
                val ex = renderHandler
                if (ex != null) ex.post { scheduleFrame() }
                else {
                    val th = HandlerThread("FlightRender", Process.THREAD_PRIORITY_DISPLAY)
                    th.start()
                    val h = Handler(th.looper)
                    renderThread = th; renderHandler = h
                    h.post {
                        choreographer = Choreographer.getInstance()
                        scheduleFrame()
                    }
                }
            }
        }
        private fun stopRendering() {
            synchronized(threadLock) {
                val h = renderHandler ?: return
                val th = renderThread
                renderHandler = null; renderThread = null
                h.post {
                    choreographer?.removeFrameCallback(this)
                    choreographer = null; frameScheduled = false
                    th?.quitSafely()
                }
            }
        }
        private fun scheduleFrame() {
            if (frameScheduled || !visible || !surfaceValid) return
            frameScheduled = true
            choreographer?.postFrameCallback(this)
        }
        override fun doFrame(fn: Long) {
            frameScheduled = false
            if (!visible || !surfaceValid) return
            if (surfaceW > 0 && surfaceH > 0 && fn - lastFrameNanos >= minFrameNanos) {
                lastFrameNanos = fn
                if (layoutDirty) rebuild()
                drawFrame((System.nanoTime() - startTime) / 1e9)
            }
            scheduleFrame()
        }

        private fun dp(v: Float) = v * density
        private fun smooth(p: Float) = p * p * (3f - 2f * p)

        private fun rebuild() {
            val w = surfaceW.toFloat()
            val h = surfaceH.toFloat()
            if (w <= 0f || h <= 0f) return
            layoutDirty = false
            density = resources.displayMetrics.density
            focal = h * 0.95f
            hud.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            hud.color = Color.argb(190, 130, 210, 245)
            line.strokeCap = Paint.Cap.ROUND
            ringP.style = Paint.Style.STROKE
            bar.color = Color.BLACK
            bgPaint.shader = LinearGradient(
                0f, 0f, 0f, h,
                intArrayOf(Color.rgb(2, 3, 8), Color.rgb(7, 10, 22)),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
            )
            nebA.shader = RadialGradient(
                w * 0.3f, h * 0.35f, max(w, h) * 0.8f,
                intArrayOf(Color.argb(26, 60, 90, 190), Color.argb(0, 0, 0, 0)),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
            )
            nebB.shader = RadialGradient(
                w * 0.75f, h * 0.7f, max(w, h) * 0.7f,
                intArrayOf(Color.argb(20, 120, 60, 170), Color.argb(0, 0, 0, 0)),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
            )
            vig.shader = RadialGradient(
                w / 2f, h / 2f, max(w, h) * 0.72f,
                intArrayOf(Color.argb(0, 0, 0, 0), Color.argb(0, 0, 0, 0), Color.argb(200, 0, 0, 0)),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
            )
            if (!started) {
                started = true
                makeStars()
                generateSystem()
            }
        }

        private fun makeStars() {
            stars.clear()
            for (i in 0 until 420) {
                val s = FlightStar()
                s.x = (rng.nextFloat() - 0.5f) * 10000f
                s.y = (rng.nextFloat() - 0.5f) * 10000f
                s.z = camZ + 80f + rng.nextFloat() * 9000f
                s.b = 0.25f + rng.nextFloat() * 0.75f
                stars.add(s)
            }
        }

        private fun generateSystem() {
            planets.clear()
            parts.clear()
            systemIndex++
            var z = camZ + 2600f
            val count = 4 + rng.nextInt(4)
            for (i in 0 until count) {
                val r = 260f + rng.nextFloat() * 360f
                val c1 = Color.rgb(60 + rng.nextInt(190), 60 + rng.nextInt(190), 60 + rng.nextInt(190))
                val c2 = Color.rgb(15 + rng.nextInt(70), 15 + rng.nextInt(70), 20 + rng.nextInt(80))
                planets.add(
                    FlightPlanet(
                        (rng.nextFloat() - 0.5f) * 2200f,
                        (rng.nextFloat() - 0.5f) * 1600f,
                        z, r, c1, c2,
                        rng.nextFloat() < 0.3f,
                        rng.nextFloat() < 0.5f,
                        rng.nextFloat() < 0.4f,
                        rng.nextLong()
                    )
                )
                z += 2600f + rng.nextFloat() * 1600f
            }
        }

        private fun drawFrame(t: Double) {
            val holder = surfaceHolder
            val canvas = try { holder.lockHardwareCanvas() } catch (e: Exception) {
                try { holder.lockCanvas() } catch (e2: Exception) { null }
            } ?: return
            try {
                val dt = if (lastT == 0.0) 0.016f else (t - lastT).toFloat().coerceIn(0f, 0.05f)
                lastT = t
                update(dt)
                render(canvas, t, dt)
            } finally {
                try { holder.unlockCanvasAndPost(canvas) } catch (_: IllegalArgumentException) {}
            }
        }

        private fun update(dt: Float) {
            if (warpActive) {
                warpT += dt / 2.4f
                val wv = warpT.coerceIn(0f, 1f)
                val boost = if (wv < 0.5f) smooth(wv / 0.5f) else 1f - smooth((wv - 0.5f) / 0.5f)
                warpSpeed = baseSpeed * 45f * boost
                if (wv >= 0.75f && !swapped) {
                    generateSystem()
                    makeStars()
                    swapped = true
                }
                if (wv >= 1f) {
                    warpActive = false; warpT = 0f; swapped = false; warpSpeed = 0f
                }
            } else {
                camZ += baseSpeed * dt
                val target = planets.firstOrNull { !it.dead }
                if (target != null) {
                    val k = min(1f, dt * 1.9f)
                    camX += (target.x - camX) * k
                    camY += (target.y - camY) * k
                    val want = ((target.x - camX) * -0.006f).coerceIn(-7f, 7f)
                    roll += (want - roll) * min(1f, dt * 3f)
                }
                for (i in planets.indices) {
                    val p = planets[i]
                    if (!p.dead && camZ > p.z - p.r * 0.85f) destroy(i)
                }
                if (planets.none { !it.dead }) {
                    warpActive = true; warpT = 0f; swapped = false
                }
            }

            val adv = warpSpeed * dt
            for (s in stars) {
                if (adv > 0f) s.z -= adv
                if (s.z - camZ < 70f) {
                    s.z += 9000f
                    s.x = (rng.nextFloat() - 0.5f) * 10000f
                    s.y = (rng.nextFloat() - 0.5f) * 10000f
                }
            }

            var i = parts.size - 1
            while (i >= 0) {
                val p = parts[i]
                p.x += p.vx * dt
                p.y += p.vy * dt
                p.z += p.vz * dt
                p.rot += p.vr * dt
                p.life -= dt
                if (p.life <= 0f || p.z - camZ < 50f) parts.removeAt(i)
                i--
            }

            i = planets.size - 1
            while (i >= 0) {
                val p = planets[i]
                if (p.dead) {
                    val na = p.age + dt
                    if (na > 3.5f) planets.removeAt(i)
                    else planets[i] = p.copy(age = na)
                }
                i--
            }
            shake = max(0f, shake - dt * 2.2f)
        }

        private fun destroy(index: Int) {
            val p = planets[index]
            val kind = rng.nextInt(6)
            planets[index] = p.copy(dead = true, kind = kind, age = 0f)
            shake = 1f
            val n = when (kind) { 2 -> 16; 3 -> 150; else -> 90 }
            for (i in 0 until n) {
                val a = rng.nextFloat() * 6.2832f
                val b = (rng.nextFloat() - 0.5f) * 3.1416f
                val dx = cos(b) * cos(a)
                val dy = cos(b) * sin(a)
                val dz = sin(b)
                val q = FlightParticle()
                q.color = if (rng.nextBoolean()) p.c1 else p.c2
                q.chunk = kind == 2
                q.size = if (q.chunk) p.r * (0.18f + rng.nextFloat() * 0.22f)
                else 6f + rng.nextFloat() * 22f
                q.max = 1.4f + rng.nextFloat() * 1.6f
                q.life = q.max
                q.rot = rng.nextFloat() * 360f
                q.vr = (rng.nextFloat() - 0.5f) * 180f
                when (kind) {
                    1, 5 -> {
                        val d = p.r * (1.4f + rng.nextFloat() * 2f)
                        q.x = p.x + dx * d; q.y = p.y + dy * d; q.z = p.z + dz * d
                        val sp = 700f + rng.nextFloat() * 900f
                        q.vx = -dx * sp; q.vy = -dy * sp; q.vz = -dz * sp
                        q.color = if (kind == 1) Color.rgb(20, 20, 26) else p.c1
                    }
                    4 -> {
                        val d = rng.nextFloat() * p.r
                        q.x = p.x + dx * d; q.y = p.y + dy * d; q.z = p.z + dz * d
                        q.vx = dx * 90f; q.vy = dy * 90f; q.vz = dz * 90f
                        q.color = Color.rgb(190, 225, 255)
                        q.chunk = true
                        q.size = p.r * (0.08f + rng.nextFloat() * 0.14f)
                    }
                    else -> {
                        val d = rng.nextFloat() * p.r * 0.6f
                        q.x = p.x + dx * d; q.y = p.y + dy * d; q.z = p.z + dz * d
                        val sp = if (kind == 3) 150f + rng.nextFloat() * 450f
                        else 500f + rng.nextFloat() * 1700f
                        q.vx = dx * sp; q.vy = dy * sp; q.vz = dz * sp
                    }
                }
                parts.add(q)
            }
        }

        private fun render(canvas: Canvas, t: Double, dt: Float) {
            val w = surfaceW.toFloat()
            val h = surfaceH.toFloat()
            val cx = w / 2f
            val cy = h / 2f
            canvas.drawRect(0f, 0f, w, h, bgPaint)
            canvas.drawRect(0f, 0f, w, h, nebA)
            canvas.drawRect(0f, 0f, w, h, nebB)

            canvas.save()
            val sh = shake * dp(14f)
            canvas.translate(sin(t * 47.0).toFloat() * sh, cos(t * 41.0).toFloat() * sh)
            canvas.rotate(roll, cx, cy)

            val trail = (if (warpActive) warpSpeed else baseSpeed) * dt * 2.4f
            for (s in stars) {
                val dz = s.z - camZ
                if (dz < 70f) continue
                val k = focal / dz
                val sx = cx + (s.x - camX) * k
                val sy = cy + (s.y - camY) * k
                if (sx < -200f || sx > w + 200f || sy < -200f || sy > h + 200f) continue
                val k2 = focal / (dz + trail)
                val px = cx + (s.x - camX) * k2
                val py = cy + (s.y - camY) * k2
                val a = (s.b * 230f * min(1f, 2500f / dz)).toInt().coerceIn(0, 255)
                line.color = Color.argb(a, 205, 228, 255)
                line.strokeWidth = max(0.8f, s.b * 2.4f * min(2f, 1200f / dz))
                canvas.drawLine(px, py, sx, sy, line)
            }

            val sorted = planets.sortedByDescending { it.z }
            for (p in sorted) drawPlanet(canvas, p, cx, cy, w, h)

            for (p in parts) {
                val dz = p.z - camZ
                if (dz < 60f) continue
                val k = focal / dz
                val sx = cx + (p.x - camX) * k
                val sy = cy + (p.y - camY) * k
                val sr = max(0.6f, p.size * k)
                if (sx + sr < 0f || sx - sr > w || sy + sr < 0f || sy - sr > h) continue
                val a = (p.life / p.max * 255f).toInt().coerceIn(0, 255)
                dot.color = Color.argb(a, Color.red(p.color), Color.green(p.color), Color.blue(p.color))
                if (p.chunk) {
                    canvas.save()
                    canvas.translate(sx, sy)
                    canvas.rotate(p.rot)
                    chunkPath.rewind()
                    chunkPath.moveTo(-sr, -sr * 0.6f)
                    chunkPath.lineTo(sr * 0.7f, -sr)
                    chunkPath.lineTo(sr, sr * 0.5f)
                    chunkPath.lineTo(-sr * 0.4f, sr)
                    chunkPath.close()
                    canvas.drawPath(chunkPath, dot)
                    canvas.restore()
                } else {
                    canvas.drawCircle(sx, sy, sr, dot)
                }
            }
            canvas.restore()

            drawHud(canvas, w, h, cx, cy)

            if (warpActive) {
                val wv = warpT.coerceIn(0f, 1f)
                val fw = ((wv - 0.5f) / 0.5f).coerceIn(0f, 1f)
                val fl = sin(fw * PI).toFloat()
                dot.color = Color.argb((235 * fl).toInt().coerceIn(0, 255), 225, 240, 255)
                canvas.drawRect(0f, 0f, w, h, dot)
            }
            canvas.drawRect(0f, 0f, w, h, vig)
            val bh = h * 0.045f
            canvas.drawRect(0f, 0f, w, bh, bar)
            canvas.drawRect(0f, h - bh, w, h, bar)
        }

        private fun drawPlanet(canvas: Canvas, p: FlightPlanet, cx: Float, cy: Float, w: Float, h: Float) {
            val dz = p.z - camZ
            if (dz < 60f) return
            val k = focal / dz
            val sx = cx + (p.x - camX) * k
            val sy = cy + (p.y - camY) * k
            val sr = p.r * k
            if (sx + sr * 2f < 0f || sx - sr * 2f > w) return
            if (sy + sr * 2f < 0f || sy - sr * 2f > h) return

            if (p.dead) {
                val q = (p.age / 3.5f).coerceIn(0f, 1f)
                val fade = (1f - q)
                when (p.kind) {
                    0 -> {
                        val rr = sr * (0.6f + q * 2.2f)
                        body.shader = RadialGradient(
                            sx, sy, max(1f, rr),
                            intArrayOf(
                                Color.argb((200 * fade).toInt(), 255, 240, 200),
                                Color.argb(0, 255, 120, 40)
                            ),
                            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
                        )
                        canvas.drawCircle(sx, sy, rr, body)
                        body.shader = null
                        ringP.strokeWidth = dp(2f) * fade + 0.5f
                        ringP.color = Color.argb((170 * fade).toInt(), 255, 200, 140)
                        canvas.drawCircle(sx, sy, sr * (1f + q * 3f), ringP)
                    }
                    1 -> {
                        val rr = sr * (1f - q * 0.7f)
                        body.color = Color.argb((255 * fade).toInt(), 0, 0, 0)
                        canvas.drawCircle(sx, sy, max(1f, rr * 0.55f), body)
                        ringP.strokeWidth = dp(3f)
                        ringP.color = Color.argb((220 * fade).toInt(), 255, 170, 70)
                        canvas.drawCircle(sx, sy, max(1f, rr * 0.8f), ringP)
                    }
                    4 -> {
                        body.color = Color.argb((150 * fade).toInt(), 200, 235, 255)
                        canvas.drawCircle(sx, sy, sr * (1f + q * 0.25f), body)
                    }
                    5 -> {
                        body.color = Color.argb((180 * fade).toInt(), Color.red(p.c1), Color.green(p.c1), Color.blue(p.c1))
                        canvas.drawCircle(sx, sy, max(1f, sr * (1f - q)), body)
                    }
                    else -> {
                        body.color = Color.argb((90 * fade).toInt(), 255, 220, 180)
                        canvas.drawCircle(sx, sy, sr * (0.8f + q), body)
                    }
                }
                return
            }

            if (p.atmo) {
                body.shader = RadialGradient(
                    sx, sy, max(1f, sr * 1.25f),
                    intArrayOf(
                        Color.argb(70, Color.red(p.c1), Color.green(p.c1), Color.blue(p.c1)),
                        Color.argb(0, 0, 0, 0)
                    ),
                    floatArrayOf(0.72f, 1f), Shader.TileMode.CLAMP
                )
                canvas.drawCircle(sx, sy, sr * 1.25f, body)
                body.shader = null
            }

            body.shader = RadialGradient(
                sx - sr * 0.35f, sy - sr * 0.4f, max(1f, sr * 1.5f),
                intArrayOf(p.c1, p.c2, Color.rgb(2, 3, 6)),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
            )
            canvas.drawCircle(sx, sy, sr, body)
            body.shader = null

            if (sr > 40f) {
                canvas.save()
                clip.rewind()
                clip.addCircle(sx, sy, sr, Path.Direction.CW)
                canvas.clipPath(clip)
                if (p.bands) {
                    for (i in 0 until 6) {
                        val yy = sy - sr + sr * 2f * (i + 0.5f) / 6f
                        body.color = Color.argb(if (i % 2 == 0) 45 else 25, 0, 0, 0)
                        canvas.drawRect(sx - sr, yy - sr * 0.09f, sx + sr, yy + sr * 0.09f, body)
                    }
                } else {
                    val cr = Random(p.seed)
                    val n = 3 + cr.nextInt(5)
                    for (i in 0 until n) {
                        val ax = (cr.nextFloat() - 0.5f) * 1.4f
                        val ay = (cr.nextFloat() - 0.5f) * 1.4f
                        body.color = Color.argb(60, 0, 0, 0)
                        canvas.drawCircle(sx + ax * sr, sy + ay * sr, sr * (0.06f + cr.nextFloat() * 0.12f), body)
                    }
                }
                canvas.restore()
            }

            if (p.ring) {
                canvas.save()
                canvas.translate(sx, sy)
                canvas.rotate(-18f)
                ringP.strokeWidth = max(1f, sr * 0.10f)
                ringP.color = Color.argb(120, 220, 215, 195)
                canvas.drawOval(-sr * 1.9f, -sr * 0.42f, sr * 1.9f, sr * 0.42f, ringP)
                ringP.strokeWidth = max(1f, sr * 0.04f)
                ringP.color = Color.argb(80, 255, 250, 230)
                canvas.drawOval(-sr * 2.25f, -sr * 0.5f, sr * 2.25f, sr * 0.5f, ringP)
                canvas.restore()
            }
        }

        private fun drawHud(canvas: Canvas, w: Float, h: Float, cx: Float, cy: Float) {
            hud.textSize = dp(11f)
            hud.textAlign = Paint.Align.LEFT
            val left = planets.count { !it.dead }
            canvas.drawText("СИСТЕМА %02d".format(systemIndex), dp(16f), h * 0.045f + dp(22f), hud)
            canvas.drawText("ЦЕЛЕЙ: $left", dp(16f), h * 0.045f + dp(38f), hud)
            if (warpActive) {
                hud.textAlign = Paint.Align.CENTER
                hud.textSize = dp(13f)
                canvas.drawText("СВЕРХСВЕТОВОЙ ПРЫЖОК", cx, cy, hud)
                hud.textAlign = Paint.Align.LEFT
                return
            }
            val t = planets.firstOrNull { !it.dead } ?: return
            val dz = t.z - camZ
            if (dz < 200f) return
            val k = focal / dz
            val sx = cx + (t.x - camX) * k
            val sy = cy + (t.y - camY) * k
            val rr = max(dp(18f), t.r * k * 1.15f)
            if (sx < -rr || sx > w + rr || sy < -rr || sy > h + rr) return
            line.strokeWidth = dp(1.4f)
            line.color = Color.argb(150, 120, 220, 250)
            val c = rr * 0.35f
            canvas.drawLine(sx - rr, sy - rr, sx - rr + c, sy - rr, line)
            canvas.drawLine(sx - rr, sy - rr, sx - rr, sy - rr + c, line)
            canvas.drawLine(sx + rr, sy - rr, sx + rr - c, sy - rr, line)
            canvas.drawLine(sx + rr, sy - rr, sx + rr, sy - rr + c, line)
            canvas.drawLine(sx - rr, sy + rr, sx - rr + c, sy + rr, line)
            canvas.drawLine(sx - rr, sy + rr, sx - rr, sy + rr - c, line)
            canvas.drawLine(sx + rr, sy + rr, sx + rr - c, sy + rr, line)
            canvas.drawLine(sx + rr, sy + rr, sx + rr, sy + rr - c, line)
            hud.textSize = dp(10f)
            canvas.drawText("${(dz / 1000f).toInt()}k", sx + rr + dp(6f), sy, hud)
        }
    }
}
