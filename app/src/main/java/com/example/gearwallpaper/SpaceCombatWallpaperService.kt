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

// Все поля val по умолчанию. Это безопасно и не вызывает ошибок при копировании.
private data class CombatStar(val x: Float, val y: Float, val size: Float, val brightness: Float)
private data class CombatPlanet(val x: Float, val y: Float, val radius: Float, val color1: Int, val color2: Int, val hasRing: Boolean, val hasAtmosphere: Boolean, val craters: List<Pair<Float, Float>>, val destroyed: Boolean = false, val destructionType: Int = 0, val destructionTime: Float = 0f)
private data class CombatParticle(val x: Float, val y: Float, val vx: Float, val vy: Float, val life: Float, val maxLife: Float, val color: Int, val size: Float)

// Обычный класс только для корабля, так как он один и его удобно менять
private class CombatShip {
    var x = 0f; var y = 0f; var angle = 0f; var targetPlanet: Int = 0
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

        private var density = 1f; private var worldW = 0f; private var worldH = 0f
        private var cameraX = 0f; private var cameraY = 0f
        private var warpProgress = 0f; private var warpActive = false; private var systemIndex = 0

        private val ship = CombatShip()
        private val planets = mutableListOf<CombatPlanet>()
        private val particles = mutableListOf<CombatParticle>()
        private val stars = mutableListOf<CombatStar>()
        private val rng = Random(42L)
        private var lastPlanetX = 0f

        private val bgPaint = Paint()
        private val nebulaPaint = Paint()
        private val shipPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val enginePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(200, 200, 230, 255); textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        override fun onVisibilityChanged(v: Boolean) { visible = v; if (v) startRendering() else stopRendering() }
        override fun onSurfaceCreated(h: SurfaceHolder) { super.onSurfaceCreated(h); surfaceValid = true }
        override fun onSurfaceChanged(h: SurfaceHolder, f: Int, w: Int, hh: Int) {
            super.onSurfaceChanged(h, f, w, hh); surfaceW = w; surfaceH = hh; layoutDirty = true
            if (visible) startRendering()
        }
        override fun onSurfaceDestroyed(h: SurfaceHolder) { super.onSurfaceDestroyed(h); surfaceValid = false; stopRendering() }
        override fun onDestroy() { super.onDestroy(); visible = false; stopRendering() }

        private fun startRendering() {
            synchronized(threadLock) {
                val ex = renderHandler
                if (ex != null) ex.post { scheduleFrame() }
                else {
                    val th = HandlerThread("CombatRender", Process.THREAD_PRIORITY_DISPLAY)
                    th.start()
                    val h = Handler(th.looper)
                    renderThread = th; renderHandler = h
                    h.post { choreographer = Choreographer.getInstance(); scheduleFrame() }
                }
            }
        }
        private fun stopRendering() {
            synchronized(threadLock) {
                val h = renderHandler ?: return
                val th = renderThread
                renderHandler = null; renderThread = null
                h.post { choreographer?.removeFrameCallback(this); choreographer = null; frameScheduled = false; th?.quitSafely() }
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
                if (layoutDirty) rebuildScene()
                drawFrame((System.nanoTime() - startTime) / 1e9)
            }
            scheduleFrame()
        }
        private fun dp(v: Float) = v * density

        private fun rebuildScene() {
            val w = surfaceW; val h = surfaceH
            if (w <= 0 || h <= 0) return
            layoutDirty = false
            density = resources.displayMetrics.density
            worldW = w.toFloat() * 3f; worldH = h.toFloat()
            cameraX = ship.x - w / 2f; cameraY = ship.y - h / 2f

            stars.clear()
            val rnd = Random(42L)
            for (i in 0 until 300) {
                stars.add(CombatStar(rnd.nextFloat() * worldW * 2f, rnd.nextFloat() * worldH * 2f, rnd.nextFloat() * 2f + 0.5f, rnd.nextFloat()))
            }
            bgPaint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(Color.rgb(2, 4, 8), Color.rgb(8, 12, 24)), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
            nebulaPaint.shader = RadialGradient(w * 0.3f, h * 0.4f, maxOf(w, h), intArrayOf(Color.argb(30, 60, 40, 120), Color.argb(0, 0, 0, 0)), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        }

        private fun generateSystem() {
            planets.clear(); particles.clear(); warpActive = false; warpProgress = 0f; systemIndex++
            lastPlanetX = ship.x + worldW * 0.5f
            val count = 4 + rng.nextInt(4)
            for (i in 0 until count) {
                lastPlanetX += 400f + rng.nextFloat() * 300f
                val y = worldH * 0.3f + rng.nextFloat() * worldH * 0.4f
                val radius = 60f + rng.nextFloat() * 120f
                val c1 = Color.rgb(rng.nextInt(200) + 55, rng.nextInt(200) + 55, rng.nextInt(200) + 55)
                val c2 = Color.rgb(rng.nextInt(150) + 30, rng.nextInt(150) + 30, rng.nextInt(150) + 30)
                val craters = (0 until rng.nextInt(5)).map { Pair(rng.nextFloat() * 2f - 1f, rng.nextFloat() * 2f - 1f) }
                planets.add(CombatPlanet(lastPlanetX, y, radius, c1, c2, rng.nextBoolean(), rng.nextBoolean(), craters))
            }
            ship.targetPlanet = 0
        }

        private fun drawFrame(t: Double) {
            val holder = surfaceHolder
            val canvas = try { holder.lockHardwareCanvas() } catch (e: Exception) { try { holder.lockCanvas() } catch (e2: Exception) { null } } ?: return
            try {
                val w = surfaceW.toFloat(); val h = surfaceH.toFloat()
                val dt = if (lastFrameNanos == 0L) 0.016f else ((t * 1e9 - lastFrameNanos) / 1e9f).toFloat()

                if (warpActive) {
                    warpProgress += dt * 0.5f
                    if (warpProgress >= 1f) generateSystem()
                    drawWarp(canvas, w, h)
                } else {
                    updateShip(dt); updateParticles(dt); updatePlanets(dt); checkCollisions()
                    if (planets.all { it.destroyed }) startWarp()
                    updateCamera(dt); drawScene(canvas, w, h)
                }
            } finally { try { holder.unlockCanvasAndPost(canvas) } catch (_: IllegalArgumentException) {} }
        }

        private fun updateShip(dt: Float) {
            if (ship.targetPlanet < planets.size && !planets[ship.targetPlanet].destroyed) {
                val target = planets[ship.targetPlanet]
                val dx = target.x - ship.x; val dy = target.y - ship.y
                val dist = sqrt(dx * dx + dy * dy)
                if (dist > 1f) {
                    ship.x += dx / dist * 200f * dt; ship.y += dy / dist * 200f * dt
                    ship.angle = atan2(dy.toDouble(), dx.toDouble()).toFloat()
                }
            } else {
                var nearest = -1; var minDist = Float.MAX_VALUE
                for (i in planets.indices) {
                    if (!planets[i].destroyed) {
                        val dx = planets[i].x - ship.x; val dy = planets[i].y - ship.y
                        val d = sqrt(dx * dx + dy * dy)
                        if (d < minDist) { minDist = d; nearest = i }
                    }
                }
                if (nearest >= 0) ship.targetPlanet = nearest
            }
        }

        private fun updateCamera(dt: Float) {
            cameraX += (ship.x - surfaceW / 2f - cameraX) * 3f * dt
            cameraY += (ship.y - surfaceH / 2f - cameraY) * 3f * dt
        }

        private fun checkCollisions() {
            for (i in planets.indices) {
                if (!planets[i].destroyed) {
                    val p = planets[i]
                    val dist = sqrt((ship.x - p.x).let { it * it } + (ship.y - p.y).let { it * it })
                    if (dist < p.radius + 20f) { destroyPlanet(i); break }
                }
            }
        }

        private fun destroyPlanet(index: Int) {
            val p = planets[index]
            val type = rng.nextInt(6)
            planets[index] = CombatPlanet(p.x, p.y, p.radius, p.color1, p.color2, p.hasRing, p.hasAtmosphere, p.craters, true, type, 0f)
            when (type) {
                0 -> createExplosion(p.x, p.y, p.radius, p.color1)
                1 -> createBlackHole(p.x, p.y, p.radius)
                2 -> createShatter(p.x, p.y, p.radius, p.color1)
                3 -> createDust(p.x, p.y, p.radius, p.color2)
                4 -> createFreeze(p.x, p.y, p.radius, p.color1)
                5 -> createAbsorb(p.x, p.y, p.radius, p.color1)
            }
        }

        private fun createExplosion(x: Float, y: Float, radius: Float, color: Int) {
            for (i in 0 until 80) {
                val angle = rng.nextFloat() * 6.28f; val speed = 100f + rng.nextFloat() * 300f
                particles.add(CombatParticle(x, y, cos(angle).toFloat() * speed, sin(angle).toFloat() * speed, 1f + rng.nextFloat(), 2f, color, rng.nextFloat() * 8f + 4f))
            }
        }
        private fun createBlackHole(x: Float, y: Float, radius: Float) {
            for (i in 0 until 60) {
                val angle = rng.nextFloat() * 6.28f; val dist = radius * (1f + rng.nextFloat() * 2f)
                particles.add(CombatParticle(x + cos(angle).toFloat() * dist, y + sin(angle).toFloat() * dist, -cos(angle).toFloat() * 150f, -sin(angle).toFloat() * 150f, 2f, 3f, Color.BLACK, rng.nextFloat() * 6f + 3f))
            }
        }
        private fun createShatter(x: Float, y: Float, radius: Float, color: Int) {
            for (i in 0 until 12) {
                val angle = rng.nextFloat() * 6.28f; val speed = 50f + rng.nextFloat() * 200f
                particles.add(CombatParticle(x, y, cos(angle).toFloat() * speed, sin(angle).toFloat() * speed, 3f, 4f, color, radius * 0.3f + rng.nextFloat() * radius * 0.4f))
            }
        }
        private fun createDust(x: Float, y: Float, radius: Float, color: Int) {
            for (i in 0 until 100) {
                val angle = rng.nextFloat() * 6.28f; val speed = 20f + rng.nextFloat() * 100f
                particles.add(CombatParticle(x + cos(angle).toFloat() * radius * rng.nextFloat(), y + sin(angle).toFloat() * radius * rng.nextFloat(), cos(angle).toFloat() * speed, sin(angle).toFloat() * speed, 2f + rng.nextFloat() * 2f, 4f, color, rng.nextFloat() * 3f + 1f))
            }
        }
        private fun createFreeze(x: Float, y: Float, radius: Float, color: Int) {
            for (i in 0 until 50) {
                val angle = rng.nextFloat() * 6.28f; val dist = rng.nextFloat() * radius
                particles.add(CombatParticle(x + cos(angle).toFloat() * dist, y + sin(angle).toFloat() * dist, 0f, 0f, 3f, 4f, Color.argb(200, 180, 220, 255), rng.nextFloat() * 10f + 5f))
            }
        }
        private fun createAbsorb(x: Float, y: Float, radius: Float, color: Int) {
            for (i in 0 until 70) {
                val angle = rng.nextFloat() * 6.28f; val dist = radius * (1f + rng.nextFloat() * 3f)
                particles.add(CombatParticle(x + cos(angle).toFloat() * dist, y + sin(angle).toFloat() * dist, -cos(angle).toFloat() * 200f, -sin(angle).toFloat() * 200f, 1.5f, 2f, color, rng.nextFloat() * 5f + 3f))
            }
        }

        // 100% ЗАЩИТА ОТ "VAL CANNOT BE REASSIGNED": мы создаём новый объект вместо изменения старого
        private fun updateParticles(dt: Float) {
            var i = particles.size - 1
            while (i >= 0) {
                val p = particles[i]
                val newLife = p.life - dt
                if (newLife <= 0f) {
                    particles.removeAt(i)
                } else {
                    particles[i] = CombatParticle(
                        x = p.x + p.vx * dt,
                        y = p.y + p.vy * dt,
                        vx = p.vx,
                        vy = p.vy,
                        life = newLife,
                        maxLife = p.maxLife,
                        color = p.color,
                        size = p.size
                    )
                }
                i--
            }
        }
        
        // То же самое для планет
        private fun updatePlanets(dt: Float) {
            for (i in planets.indices) {
                val p = planets[i]
                if (p.destroyed) {
                    planets[i] = CombatPlanet(
                        x = p.x, y = p.y, radius = p.radius,
                        color1 = p.color1, color2 = p.color2,
                        hasRing = p.hasRing, hasAtmosphere = p.hasAtmosphere,
                        craters = p.craters,
                        destroyed = true,
                        destructionType = p.destructionType,
                        destructionTime = p.destructionTime + dt
                    )
                }
            }
        }
        
        private fun startWarp() { warpActive = true; warpProgress = 0f }

        private fun drawScene(canvas: Canvas, w: Float, h: Float) {
            canvas.drawRect(0f, 0f, w, h, bgPaint); canvas.drawRect(0f, 0f, w, h, nebulaPaint)
            canvas.save(); canvas.translate(-cameraX, -cameraY)

            for (s in stars) {
                val sx = s.x - cameraX * 0.3f; val sy = s.y - cameraY * 0.3f
                if (sx > cameraX - 10f && sx < cameraX + w + 10f && sy > cameraY - 10f && sy < cameraY + h + 10f) {
                    particlePaint.color = Color.argb((s.brightness * 200).toInt(), 200, 220, 255)
                    canvas.drawCircle(sx + cameraX, sy + cameraY, s.size, particlePaint)
                }
            }
            for (p in planets) { if (!p.destroyed || p.destructionTime < 4f) drawPlanet(canvas, p) }
            for (p in particles) {
                val alpha = (p.life / p.maxLife * 255).toInt().coerceIn(0, 255)
                particlePaint.color = Color.argb(alpha, Color.red(p.color), Color.green(p.color), Color.blue(p.color))
                canvas.drawCircle(p.x, p.y, p.size, particlePaint)
            }
            drawShip(canvas); canvas.restore()

            textPaint.textSize = dp(20f)
            canvas.drawText("Система $systemIndex", w / 2f, dp(40f), textPaint)
            canvas.drawText("Планет: ${planets.count { !it.destroyed }}", w / 2f, dp(70f), textPaint)
        }

        private fun drawPlanet(canvas: Canvas, p: CombatPlanet) {
            if (p.destroyed) {
                val progress = (p.destructionTime / 2f).coerceIn(0f, 1f)
                if (p.destructionType == 1) {
                    val radius = p.radius * (1f - progress * 0.5f)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                    paint.shader = RadialGradient(p.x, p.y, radius, intArrayOf(Color.BLACK, Color.argb(0, 0, 0, 0)), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
                    canvas.drawCircle(p.x, p.y, radius * 2f, paint)
                } else if (p.destructionType == 4) {
                    val alpha = (200 * (1f - progress)).toInt().coerceIn(0, 255)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                    paint.color = Color.argb(alpha, 180, 220, 255)
                    canvas.drawCircle(p.x, p.y, p.radius * (1f + progress * 0.3f), paint)
                }
                return
            }
            if (p.hasAtmosphere) {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                paint.shader = RadialGradient(p.x, p.y, p.radius * 1.3f, intArrayOf(Color.argb(80, Color.red(p.color1), Color.green(p.color1), Color.blue(p.color1)), Color.argb(0, 0, 0, 0)), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
                canvas.drawCircle(p.x, p.y, p.radius * 1.3f, paint)
            }
            if (p.hasRing) {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG); paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(8f); paint.color = Color.argb(150, 200, 200, 200)
                canvas.save(); canvas.translate(p.x, p.y); canvas.rotate(20f)
                canvas.drawOval(-p.radius * 1.8f, -p.radius * 0.4f, p.radius * 1.8f, p.radius * 0.4f, paint)
                canvas.restore()
            }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.shader = RadialGradient(p.x - p.radius * 0.3f, p.y - p.radius * 0.3f, p.radius, intArrayOf(p.color1, p.color2), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
            canvas.drawCircle(p.x, p.y, p.radius, paint)
            
            val craterPaint = Paint(Paint.ANTI_ALIAS_FLAG); craterPaint.color = Color.argb(100, 0, 0, 0)
            for (c in p.craters) canvas.drawCircle(p.x + c.first * p.radius * 0.6f, p.y + c.second * p.radius * 0.6f, p.radius * 0.15f, craterPaint)
        }

        private fun drawShip(canvas: Canvas) {
            canvas.save(); canvas.translate(ship.x, ship.y); canvas.rotate(ship.angle * 57.3f)
            enginePaint.shader = RadialGradient(-30f, 0f, 20f, intArrayOf(Color.argb(200, 255, 150, 50), Color.argb(0, 255, 100, 0)), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
            canvas.drawCircle(-30f, 0f, 20f, enginePaint)
            val path = Path(); path.moveTo(30f, 0f); path.lineTo(-20f, -15f); path.lineTo(-15f, 0f); path.lineTo(-20f, 15f); path.close()
            shipPaint.color = Color.rgb(180, 200, 220); canvas.drawPath(path, shipPaint)
            shipPaint.color = Color.rgb(100, 180, 255); canvas.drawCircle(10f, 0f, 8f, shipPaint)
            canvas.restore()
        }

        private fun drawWarp(canvas: Canvas, w: Float, h: Float) {
            canvas.drawRect(0f, 0f, w, h, bgPaint)
            val progress = warpProgress.coerceIn(0f, 1f)
            for (s in stars) {
                val sx = (s.x - cameraX * 0.5f) % w; val sy = (s.y - cameraY * 0.5f) % h
                particlePaint.color = Color.argb((s.brightness * 255).toInt(), 200, 220, 255)
                canvas.drawRect(sx, sy, sx + progress * 200f, sy + 2f, particlePaint)
            }
            if (progress > 0.8f) canvas.drawColor(Color.argb(((progress - 0.8f) * 5f * 255).toInt().coerceIn(0, 255), 255, 255, 255))
            textPaint.textSize = dp(30f); canvas.drawText("ВАРП-ПРЫЖОК", w / 2f, h / 2f, textPaint)
        }
    }
}
