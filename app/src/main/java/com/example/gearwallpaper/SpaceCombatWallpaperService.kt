package com.example.gearwallpaper

import android.graphics.*
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.service.wallpaper.WallpaperService
import android.view.Choreographer
import android.view.SurfaceHolder
import java.util.Random
import kotlin.math.*

private data class CStar(
    val x: Float, val y: Float, val size: Float, val br: Float
)

private data class CPlanet(
    val x: Float, val y: Float, val radius: Float,
    val c1: Int, val c2: Int, val ring: Boolean,
    val atmo: Boolean, val craters: List<Pair<Float, Float>>,
    val dead: Boolean = false, val dtype: Int = 0, val dtime: Float = 0f
)

private data class CPart(
    val x: Float, val y: Float, val vx: Float, val vy: Float,
    val life: Float, val maxLife: Float, val color: Int, val size: Float
)

private class CShip {
    var x = 0f
    var y = 0f
    var angle = 0f
    var target = 0
}

class SpaceCombatWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = CombatEngine()

    inner class CombatEngine : Engine(), Choreographer.FrameCallback {

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

        private var density = 1f
        private var worldW = 0f
        private var worldH = 0f
        private var cameraX = 0f
        private var cameraY = 0f
        private var warpProgress = 0f
        private var warpActive = false
        private var systemIndex = 0
        private var lastPlanetX = 0f

        private val ship = CShip()
        private val planets = mutableListOf<CPlanet>()
        private val particles = mutableListOf<CPart>()
        private val stars = mutableListOf<CStar>()
        private val rng = Random(42L)

        private val bgPaint = Paint()
        private val nebulaPaint = Paint()
        private val shipPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val enginePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val partPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val icePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.argb(150, 200, 200, 200)
        }
        private val craterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(100, 0, 0, 0)
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(200, 200, 230, 255)
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        // ==================== lifecycle ====================
        override fun onVisibilityChanged(v: Boolean) {
            visible = v
            if (v) startRendering() else stopRendering()
        }
        override fun onSurfaceCreated(h: SurfaceHolder) {
            super.onSurfaceCreated(h)
            surfaceValid = true
        }
        override fun onSurfaceChanged(h: SurfaceHolder, f: Int, w: Int, hh: Int) {
            super.onSurfaceChanged(h, f, w, hh)
            surfaceW = w
            surfaceH = hh
            layoutDirty = true
            if (visible) startRendering()
        }
        override fun onSurfaceDestroyed(h: SurfaceHolder) {
            super.onSurfaceDestroyed(h)
            surfaceValid = false
            stopRendering()
        }
        override fun onDestroy() {
            super.onDestroy()
            visible = false
            stopRendering()
        }

        // ==================== поток ====================
        private fun startRendering() {
            synchronized(threadLock) {
                val ex = renderHandler
                if (ex != null) {
                    ex.post { scheduleFrame() }
                } else {
                    val th = HandlerThread(
                        "CombatRender", Process.THREAD_PRIORITY_DISPLAY
                    )
                    th.start()
                    val h = Handler(th.looper)
                    renderThread = th
                    renderHandler = h
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
                renderHandler = null
                renderThread = null
                h.post {
                    choreographer?.removeFrameCallback(this)
                    choreographer = null
                    frameScheduled = false
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
            if (surfaceW > 0 && surfaceH > 0 &&
                fn - lastFrameNanos >= minFrameNanos
            ) {
                lastFrameNanos = fn
                if (layoutDirty) rebuildScene()
                drawFrame((System.nanoTime() - startTime) / 1e9)
            }
            scheduleFrame()
        }
        private fun dp(v: Float) = v * density

        // ==================== сцена ====================
        private fun rebuildScene() {
            val w = surfaceW
            val h = surfaceH
            if (w <= 0 || h <= 0) return
            layoutDirty = false
            density = resources.displayMetrics.density
            worldW = w.toFloat() * 3f
            worldH = h.toFloat()
            cameraX = ship.x - w / 2f
            cameraY = ship.y - h / 2f

            stars.clear()
            val rnd = Random(42L)
            for (i in 0 until 300) {
                stars.add(
                    CStar(
                        rnd.nextFloat() * worldW * 2f,
                        rnd.nextFloat() * worldH * 2f,
                        rnd.nextFloat() * 2f + 0.5f,
                        rnd.nextFloat()
                    )
                )
            }
            if (planets.isEmpty()) generateSystem()

            val diag = maxOf(w, h).toFloat()
            bgPaint.shader = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                Color.rgb(2, 4, 8), Color.rgb(8, 12, 24),
                Shader.TileMode.CLAMP
            )
            nebulaPaint.shader = RadialGradient(
                w * 0.3f, h * 0.4f, diag,
                Color.argb(30, 60, 40, 120), Color.argb(0, 0, 0, 0),
                Shader.TileMode.CLAMP
            )
        }

        private fun generateSystem() {
            planets.clear()
            particles.clear()
            warpActive = false
            warpProgress = 0f
            systemIndex++
            lastPlanetX = ship.x + worldW * 0.5f
            val count = 4 + rng.nextInt(4)
            for (i in 0 until count) {
                lastPlanetX += 400f + rng.nextFloat() * 300f
                val y = worldH * 0.3f + rng.nextFloat() * worldH * 0.4f
                val radius = 60f + rng.nextFloat() * 120f
                val c1 = Color.rgb(
                    rng.nextInt(200) + 55,
                    rng.nextInt(200) + 55,
                    rng.nextInt(200) + 55
                )
                val c2 = Color.rgb(
                    rng.nextInt(150) + 30,
                    rng.nextInt(150) + 30,
                    rng.nextInt(150) + 30
                )
                val craters = (0 until rng.nextInt(5)).map {
                    Pair(rng.nextFloat() * 2f - 1f, rng.nextFloat() * 2f - 1f)
                }
                planets.add(
                    CPlanet(
                        lastPlanetX, y, radius, c1, c2,
                        rng.nextBoolean(), rng.nextBoolean(), craters
                    )
                )
            }
            ship.target = 0
        }

        // ==================== кадр ====================
        private fun drawFrame(t: Double) {
            val holder = surfaceHolder
            val canvas = try {
                holder.lockHardwareCanvas()
            } catch (e: Exception) {
                try { holder.lockCanvas() } catch (e2: Exception) { null }
            } ?: return
            try {
                val w = surfaceW.toFloat()
                val h = surfaceH.toFloat()
                val dt = if (lastFrameNanos == 0L) 0.016f
                else ((t * 1e9 - lastFrameNanos) / 1e9f).toFloat()

                if (warpActive) {
                    warpProgress += dt * 0.5f
                    if (warpProgress >= 1f) generateSystem()
                    drawWarp(canvas, w, h)
                } else {
                    updateShip(dt)
                    updateParticles(dt)
                    updatePlanets(dt)
                    checkCollisions()
                    if (planets.all { it.dead }) startWarp()
                    updateCamera(dt)
                    drawScene(canvas, w, h)
                }
            } finally {
                try { holder.unlockCanvasAndPost(canvas) }
                catch (_: IllegalArgumentException) {}
            }
        }

        private fun updateShip(dt: Float) {
            if (ship.target < planets.size && !planets[ship.target].dead) {
                val target = planets[ship.target]
                val dx = target.x - ship.x
                val dy = target.y - ship.y
                val dist = sqrt(dx * dx + dy * dy)
                if (dist > 1f) {
                    ship.x += dx / dist * 200f * dt
                    ship.y += dy / dist * 200f * dt
                    ship.angle = atan2(dy.toDouble(), dx.toDouble()).toFloat()
                }
            } else {
                var nearest = -1
                var minDist = Float.MAX_VALUE
                for (i in planets.indices) {
                    if (!planets[i].dead) {
                        val dx = planets[i].x - ship.x
                        val dy = planets[i].y - ship.y
                        val d = sqrt(dx * dx + dy * dy)
                        if (d < minDist) {
                            minDist = d
                            nearest = i
                        }
                    }
                }
                if (nearest >= 0) ship.target = nearest
            }
        }

        private fun updateCamera(dt: Float) {
            val tx = ship.x - surfaceW / 2f
            val ty = ship.y - surfaceH / 2f
            cameraX += (tx - cameraX) * 3f * dt
            cameraY += (ty - cameraY) * 3f * dt
        }

        private fun checkCollisions() {
            for (i in planets.indices) {
                val p = planets[i]
                if (!p.dead) {
                    val dx = ship.x - p.x
                    val dy = ship.y - p.y
                    val dist = sqrt(dx * dx + dy * dy)
                    if (dist < p.radius + 20f) {
                        destroyPlanet(i)
                        break
                    }
                }
            }
        }

        private fun destroyPlanet(index: Int) {
            val p = planets[index]
            val type = rng.nextInt(6)
            planets[index] = p.copy(dead = true, dtype = type, dtime = 0f)
            when (type) {
                0 -> spawnBurst(p.x, p.y, p.radius, p.c1, 80f, 300f)
                1 -> spawnSuck(p.x, p.y, p.radius, Color.BLACK, 60, 150f)
                2 -> spawnBurst(p.x, p.y, p.radius, p.c1, 12f, 250f)
                3 -> spawnBurst(p.x, p.y, p.radius, p.c2, 100f, 100f)
                4 -> spawnBurst(p.x, p.y, p.radius, p.c1, 50f, 5f)
                5 -> spawnSuck(p.x, p.y, p.radius, p.c1, 70, 200f)
            }
        }

        private fun spawnBurst(
            x: Float, y: Float, r: Float,
            color: Int, count: Float, speed: Float
        ) {
            for (i in 0 until count.toInt()) {
                val a = rng.nextFloat() * 6.28f
                val sp = speed * (0.3f + rng.nextFloat())
                val sz = if (count < 20f) r * (0.3f + rng.nextFloat() * 0.4f)
                else rng.nextFloat() * 8f + 2f
                particles.add(
                    CPart(
                        x, y,
                        cos(a).toFloat() * sp, sin(a).toFloat() * sp,
                        2f + rng.nextFloat() * 2f, 4f,
                        color, sz
                    )
                )
            }
        }

        private fun spawnSuck(
            x: Float, y: Float, r: Float,
            color: Int, count: Int, speed: Float
        ) {
            for (i in 0 until count) {
                val a = rng.nextFloat() * 6.28f
                val d = r * (1f + rng.nextFloat() * 3f)
                particles.add(
                    CPart(
                        x + cos(a).toFloat() * d,
                        y + sin(a).toFloat() * d,
                        -cos(a).toFloat() * speed,
                        -sin(a).toFloat() * speed,
                        2f + rng.nextFloat(), 3f,
                        color, rng.nextFloat() * 6f + 3f
                    )
                )
            }
        }

        private fun updateParticles(dt: Float) {
            val it = particles.iterator()
            while (it.hasNext()) {
                val p = it.next()
                p.x += p.vx * dt
                p.y += p.vy * dt
                p.life -= dt
                if (p.life <= 0f) it.remove()
            }
        }

        private fun updatePlanets(dt: Float) {
            for (i in planets.indices) {
                val p = planets[i]
                if (p.dead) planets[i] = p.copy(dtime = p.dtime + dt)
            }
        }

        private fun startWarp() {
            warpActive = true
            warpProgress = 0f
        }

        // ==================== отрисовка ====================
        private fun drawScene(canvas: Canvas, w: Float, h: Float) {
            canvas.drawRect(0f, 0f, w, h, bgPaint)
            canvas.drawRect(0f, 0f, w, h, nebulaPaint)

            canvas.save()
            canvas.translate(-cameraX, -cameraY)

            for (s in stars) {
                partPaint.color = Color.argb(
                    (s.br * 200).toInt().coerceIn(0, 255), 200, 220, 255
                )
                canvas.drawCircle(s.x, s.y, s.size, partPaint)
            }
            for (p in planets) {
                if (!p.dead || p.dtime < 4f) drawPlanet(canvas, p)
            }
            for (p in particles) {
                val alpha = (p.life / p.maxLife * 255).toInt().coerceIn(0, 255)
                partPaint.color = Color.argb(
                    alpha, Color.red(p.color), Color.green(p.color), Color.blue(p.color)
                )
                canvas.drawCircle(p.x, p.y, p.size, partPaint)
            }
            drawShip(canvas)
            canvas.restore()

            textPaint.textSize = dp(20f)
            canvas.drawText("Система $systemIndex", w / 2f, dp(40f), textPaint)
            val rem = planets.count { !it.dead }
            canvas.drawText("Планет: $rem", w / 2f, dp(70f), textPaint)
        }

        private fun drawPlanet(canvas: Canvas, p: CPlanet) {
            if (p.dead) {
                val prog = (p.dtime / 2f).coerceIn(0f, 1f)
                if (p.dtype == 1) {
                    val r = p.radius * (1f - prog * 0.5f)
                    glowPaint.shader = RadialGradient(
                        p.x, p.y, r,
                        Color.BLACK, Color.argb(0, 0, 0, 0),
                        Shader.TileMode.CLAMP
                    )
                    canvas.drawCircle(p.x, p.y, r * 2f, glowPaint)
                    glowPaint.shader = null
                } else if (p.dtype == 4) {
                    icePaint.color = Color.argb(
                        (200 * (1f - prog)).toInt().coerceIn(0, 255),
                        180, 220, 255
                    )
                    canvas.drawCircle(
                        p.x, p.y, p.radius * (1f + prog * 0.3f), icePaint
                    )
                }
                return
            }

            if (p.atmo) {
                glowPaint.shader = RadialGradient(
                    p.x, p.y, p.radius * 1.3f,
                    Color.argb(
                        80, Color.red(p.c1), Color.green(p.c1), Color.blue(p.c1)
                    ),
                    Color.argb(0, 0, 0, 0),
                    Shader.TileMode.CLAMP
                )
                canvas.drawCircle(p.x, p.y, p.radius * 1.3f, glowPaint)
                glowPaint.shader = null
            }

            if (p.ring) {
                ringPaint.strokeWidth = dp(8f)
                canvas.save()
                canvas.translate(p.x, p.y)
                canvas.rotate(20f)
                canvas.drawOval(
                    -p.radius * 1.8f, -p.radius * 0.4f,
                    p.radius * 1.8f, p.radius * 0.4f, ringPaint
                )
                canvas.restore()
            }

            glowPaint.shader = RadialGradient(
                p.x - p.radius * 0.3f, p.y - p.radius * 0.3f, p.radius,
                p.c1, p.c2, Shader.TileMode.CLAMP
            )
            canvas.drawCircle(p.x, p.y, p.radius, glowPaint)
            glowPaint.shader = null

            for (c in p.craters) {
                canvas.drawCircle(
                    p.x + c.first * p.radius * 0.6f,
                    p.y + c.second * p.radius * 0.6f,
                    p.radius * 0.15f, craterPaint
                )
            }
        }

        private fun drawShip(canvas: Canvas) {
            canvas.save()
            canvas.translate(ship.x, ship.y)
            canvas.rotate(ship.angle * 57.3f)

            enginePaint.shader = RadialGradient(
                -30f, 0f, 20f,
                Color.argb(200, 255, 150, 50),
                Color.argb(0, 255, 100, 0),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(-30f, 0f, 20f, enginePaint)
            enginePaint.shader = null

            val path = Path()
            path.moveTo(30f, 0f)
            path.lineTo(-20f, -15f)
            path.lineTo(-15f, 0f)
            path.lineTo(-20f, 15f)
            path.close()
            shipPaint.color = Color.rgb(180, 200, 220)
            canvas.drawPath(path, shipPaint)

            shipPaint.color = Color.rgb(100, 180, 255)
            canvas.drawCircle(10f, 0f, 8f, shipPaint)
            canvas.restore()
        }

        private fun drawWarp(canvas: Canvas, w: Float, h: Float) {
            canvas.drawRect(0f, 0f, w, h, bgPaint)
            val prog = warpProgress.coerceIn(0f, 1f)
            for (s in stars) {
                val sx = ((s.x - cameraX * 0.5f) % w + w) % w
                val sy = ((s.y - cameraY * 0.5f) % h + h) % h
                partPaint.color = Color.argb(
                    (s.br * 255).toInt().coerceIn(0, 255), 200, 220, 255
                )
                canvas.drawRect(sx, sy, sx + prog * 200f, sy + 2f, partPaint)
            }
            if (prog > 0.8f) {
                val a = ((prog - 0.8f) * 5f * 255).toInt().coerceIn(0, 255)
                canvas.drawColor(Color.argb(a, 255, 255, 255))
            }
            textPaint.textSize = dp(30f)
            canvas.drawText("ВАРП-ПРЫЖОК", w / 2f, h / 2f, textPaint)
        }
    }
}
