package com.example.gearwallpaper

import android.content.SharedPreferences
import android.graphics.*
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.service.wallpaper.WallpaperService
import android.view.Choreographer
import android.view.SurfaceHolder
import java.util.Calendar
import java.util.Locale
import java.util.Random
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

class OrreryWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = OrreryEngine()

    inner class OrreryEngine : Engine(), Choreographer.FrameCallback {

        private val minFrameIntervalNanos = 33_000_000L

        @Volatile private var visible = false
        @Volatile private var surfaceValid = false
        @Volatile private var surfaceW = 0
        @Volatile private var surfaceH = 0
        @Volatile private var layoutDirty = true
        @Volatile private var xOffset = 0.5f
        @Volatile private var pages = 1
        @Volatile private var settings = OrreryPrefs.Settings()
        @Volatile private var pendingForce = 0

        private val startTimeNanos = System.nanoTime()
        private val epochDays = (System.currentTimeMillis() - 946728000000L) / 86400000.0
        private var simDays = epochDays
        private var lastT = 0.0

        private val threadLock = Any()
        private var renderThread: HandlerThread? = null
        private var renderHandler: Handler? = null
        private var choreographer: Choreographer? = null
        private var frameScheduled = false
        private var lastFrameNanos = 0L

        private val prefs = this@OrreryWallpaperService.getSharedPreferences(OrreryPrefs.NAME, MODE_PRIVATE)
        private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                OrreryPrefs.FORCE_RANDOM -> pendingForce = 1
                OrreryPrefs.FORCE_CAT -> pendingForce = 2
            }
            settings = OrreryPrefs.read(prefs)
            layoutDirty = true
            if (!settings.events) activeEvent = null
        }

        // ---------- сцена ----------
        private var worldW = 0f
        private var cx = 0f
        private var cy = 0f
        private var rOut = 0f
        private var yScale = 0.6f
        private var density = 1f
        private var stars = emptyList<Star>()
        private var orbitRects = emptyList<RectF>()
        private var glowPaints = emptyList<Paint>()
        private var bgPaint = Paint()
        private var nebulaA = Paint()
        private var nebulaB = Paint()
        private var vignettePaint = Paint()
        private val orbitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.argb(58, 120, 180, 220) }
        private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.argb(95, 150, 200, 235) }
        private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.argb(150, 190, 220, 245) }
        private val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val sunCore = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(235, 248, 255) }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(185, 210, 230, 245); textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(140, 170, 205, 235); typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        private val eventPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val eventStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        private val eventPath = Path()
        private val calendar = Calendar.getInstance()
        private val rng = Random(7L)

        private data class Star(val x: Float, val y: Float, val r: Float, val ph: Float, val fr: Float, val a: Int)
        private data class Planet(
            val name: String, val color: Int, val sizeDp: Float,
            val periodDays: Double, val l0: Double, val orbitFrac: Float,
            val ring: Boolean = false, val moon: Boolean = false,
        )
        private val planets = listOf(
            Planet("Меркурий", Color.rgb(170, 178, 188), 3.2f, 87.969, 252.25, 0.20f),
            Planet("Венера", Color.rgb(235, 214, 168), 5.0f, 224.701, 181.98, 0.32f),
            Planet("Земля", Color.rgb(92, 158, 225), 5.4f, 365.256, 100.46, 0.45f, moon = true),
            Planet("Марс", Color.rgb(214, 116, 78), 4.2f, 686.980, 355.45, 0.58f),
            Planet("Юпитер", Color.rgb(222, 186, 138), 11f, 4332.59, 34.40, 0.76f),
            Planet("Сатурн", Color.rgb(228, 208, 158), 9f, 10759.22, 49.94, 1.0f, ring = true),
        )

        // ---------- события ----------
        private enum class EventType(val dur: Double, val cat: Boolean, val title: String) {
            SUPERNOVA(14.0, true, "Сверхновая"),
            ASTEROID_IMPACT(9.0, false, "Столкновение астероида"),
            COMET(12.0, false, "Пролёт кометы"),
            SOLAR_FLARE(8.0, false, "Солнечная вспышка"),
            METEOR_SHOWER(10.0, false, "Метеорный поток"),
            ROGUE_BLACKHOLE(16.0, true, "Блуждающая чёрная дыра"),
            GAMMA_BURST(7.0, true, "Гамма-всплеск"),
            SYZYGY(10.0, false, "Парад планет"),
            RING_SHIMMER(9.0, false, "Сияние колец Сатурна"),
            EARTH_AURORA(9.0, false, "Полярное сияние Земли"),
            WORMHOLE(12.0, false, "Червоточина"),
            NEBULA_BLOOM(14.0, false, "Цветение туманности"),
            BINARY_COMPANION(12.0, false, "Звезда-компаньон"),
            VACUUM_DECAY(15.0, true, "Распад вакуума"),
        }
        private data class CosmicEvent(
            val type: EventType, val start: Double, val seed: Long,
            val target: Int, val x: Float, val y: Float, val angle: Float,
        )
        private var activeEvent: CosmicEvent? = null
        private var nextEventAt = 0.0

        init {
            settings = OrreryPrefs.read(prefs)
            prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        }

        // ==================== lifecycle ====================
        override fun onVisibilityChanged(v: Boolean) { visible = v; if (v) startRendering() else stopRendering() }
        override fun onSurfaceCreated(h: SurfaceHolder) { super.onSurfaceCreated(h); surfaceValid = true }
        override fun onSurfaceChanged(h: SurfaceHolder, f: Int, w: Int, hh: Int) {
            super.onSurfaceChanged(h, f, w, hh); surfaceW = w; surfaceH = hh; layoutDirty = true
            if (visible) startRendering()
        }
        override fun onSurfaceDestroyed(h: SurfaceHolder) { super.onSurfaceDestroyed(h); surfaceValid = false; stopRendering() }
        override fun onDestroy() {
            super.onDestroy()
            prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
            visible = false; stopRendering()
        }
        override fun onOffsetsChanged(xo: Float, yo: Float, xStep: Float, yStep: Float, xPix: Int, yPix: Int) {
            xOffset = xo.coerceIn(0f, 1f)
            val p = if (xStep > 0.0001f) (1f / xStep).roundToInt() + 1 else 1
            val c = p.coerceIn(1, 9)
            if (c != pages) { pages = c; layoutDirty = true }
        }

        // ==================== поток ====================
        private fun startRendering() {
            synchronized(threadLock) {
                val ex = renderHandler
                if (ex != null) ex.post { scheduleFrame() }
                else {
                    val th = HandlerThread("OrreryRender", Process.THREAD_PRIORITY_DISPLAY)
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
            if (surfaceW > 0 && surfaceH > 0 && fn - lastFrameNanos >= minFrameIntervalNanos) {
                lastFrameNanos = fn
                if (layoutDirty) rebuildScene()
                drawFrame((System.nanoTime() - startTimeNanos) / 1e9)
            }
            scheduleFrame()
        }
        private fun dp(v: Float) = v * density

        // ==================== сцена ====================
        private fun rebuildScene() {
            val w = surfaceW; val h = surfaceH
            if (w <= 0 || h <= 0) return
            layoutDirty = false
            val s = settings
            density = this@OrreryWallpaperService.resources.displayMetrics.density
            worldW = w.toFloat() * pages
            cx = worldW / 2f
            cy = h * 0.46f
            rOut = min(worldW * 0.46f, h * 0.55f)
            yScale = 1f - 0.65f * s.tilt
            val rnd = Random(42L)
            stars = List((110 * pages * s.stars).toInt().coerceAtLeast(20)) {
                Star(rnd.nextFloat() * worldW, rnd.nextFloat() * h,
                    dp(0.6f + rnd.nextFloat() * 1.4f), rnd.nextFloat() * 6.28f,
                    0.3f + rnd.nextFloat() * 0.7f, 60 + rnd.nextInt(140))
            }
            orbitRects = planets.map { p ->
                val r = rOut * p.orbitFrac.pow(s.spread)
                RectF(cx - r, cy - r * yScale, cx + r, cy + r * yScale)
            }
            glowPaints = planets.map { p ->
                val r = dp(p.sizeDp) * 3.2f
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(0f, 0f, r,
                        intArrayOf(Color.argb((110 * s.glow).toInt(), Color.red(p.color), Color.green(p.color), Color.blue(p.color)),
                            Color.argb(0, Color.red(p.color), Color.green(p.color), Color.blue(p.color))),
                        floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
                }
            }
            val sunR = dp(14f)
            sunPaint.shader = RadialGradient(0f, 0f, sunR * 5f * (0.5f + s.glow),
                intArrayOf(Color.argb(160, 140, 210, 255), Color.argb(60, 70, 140, 230), Color.argb(0, 40, 90, 200)),
                floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
            orbitPaint.strokeWidth = dp(0.9f); tickPaint.strokeWidth = dp(1f)
            trailPaint.strokeWidth = dp(1.6f); ringPaint.strokeWidth = dp(1.4f)
            textPaint.textSize = dp(12f); textPaint.letterSpacing = 0.12f
            labelPaint.textSize = dp(9f); labelPaint.letterSpacing = 0.08f
            bgPaint = Paint().apply {
                shader = LinearGradient(0f, 0f, 0f, h.toFloat(), Color.rgb(3, 5, 10), Color.rgb(9, 16, 34), Shader.TileMode.CLAMP)
            }
            nebulaA = Paint().apply {
                shader = RadialGradient(w * 0.25f, h * 0.3f, maxOf(w, h) * 0.7f,
                    Color.argb(22, 40, 110, 200), Color.argb(0, 20, 60, 140), Shader.TileMode.CLAMP)
            }
            nebulaB = Paint().apply {
                shader = RadialGradient(w * 0.8f, h * 0.75f, maxOf(w, h) * 0.6f,
                    Color.argb(16, 90, 60, 180), Color.argb(0, 50, 30, 120), Shader.TileMode.CLAMP)
            }
            vignettePaint = Paint().apply {
                shader = RadialGradient(w / 2f, h / 2f, maxOf(w, h) * 0.75f,
                    intArrayOf(Color.argb(0, 0, 0, 0), Color.argb(0, 0, 0, 0), Color.argb(190, 0, 0, 0)),
                    floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            }
        }

        // ==================== события: планировщик ====================
        private fun scheduleNext(t: Double, s: OrreryPrefs.Settings) {
            nextEventAt = t + (90.0 + rng.nextDouble() * 150.0) / s.rate.coerceAtLeast(0.1f)
        }
        private fun startEvent(t: Double, forceCat: Boolean) {
            val s = settings
            val pool = EventType.values().filter {
                if (forceCat) it.cat else (!it.cat || s.catastrophes)
            }
            val type = pool[rng.nextInt(pool.size)]
            activeEvent = CosmicEvent(
                type, t, rng.nextLong(), rng.nextInt(planets.size),
                rng.nextFloat() * worldW, rng.nextFloat() * surfaceH * 0.7f,
                rng.nextFloat() * 6.283f,
            )
            scheduleNext(t, s)
        }
        private fun updateEvents(t: Double, s: OrreryPrefs.Settings) {
            if (!s.events) { activeEvent = null; return }
            if (nextEventAt == 0.0) scheduleNext(t, s)
            if (pendingForce != 0) {
                startEvent(t, pendingForce == 2)
                pendingForce = 0
            } else if (activeEvent == null && t >= nextEventAt) startEvent(t, false)
            val ev = activeEvent
            if (ev != null && t - ev.start >= ev.type.dur) activeEvent = null
        }

        // ==================== кадр ====================
        private fun drawFrame(t: Double) {
            val holder = surfaceHolder
            val canvas = try { holder.lockHardwareCanvas() } catch (e: Exception) {
                try { holder.lockCanvas() } catch (e2: Exception) { null }
            } ?: return
            try {
                val w = surfaceW.toFloat(); val h = surfaceH.toFloat()
                val s = settings
                val dt = if (lastT == 0.0) 0.0 else t - lastT
                lastT = t
                simDays += dt * s.speed
                updateEvents(t, s)

                canvas.drawRect(0f, 0f, w, h, bgPaint)
                canvas.drawRect(0f, 0f, w, h, nebulaA)
                canvas.drawRect(0f, 0f, w, h, nebulaB)
                val cameraX = xOffset * (worldW - w)

                canvas.save(); canvas.translate(-cameraX * 0.35f, 0f)
                for (st in stars) {
                    val tw = 0.6f + 0.4f * sin(t * st.fr + st.ph).toFloat()
                    bodyPaint.color = Color.argb((st.a * tw).toInt().coerceIn(0, 255), 210, 230, 250)
                    canvas.drawCircle(st.x, st.y, st.r, bodyPaint)
                }
                canvas.restore()

                canvas.save(); canvas.translate(-cameraX, 0f)
                drawChapterRing(canvas)
                for (r in orbitRects) canvas.drawOval(r, orbitPaint)
                drawTrails(canvas, s)
                val pos = planets.mapIndexed { i, p -> planetPos(i, p) }
                for (i in planets.indices) if (pos[i].third < 0) drawPlanet(canvas, i, pos[i], 0.72f)
                drawSun(canvas, t)
                for (i in planets.indices) if (pos[i].third >= 0) drawPlanet(canvas, i, pos[i], 1f)
                if (s.labels) for (i in planets.indices) {
                    val (x, y, _) = pos[i]
                    canvas.drawText(planets[i].name, x + dp(planets[i].sizeDp) + dp(5f), y - dp(4f), labelPaint)
                }
                activeEvent?.let { drawEventWorld(canvas, it, progress(it, t), t, pos) }
                canvas.restore()

                activeEvent?.let { drawEventScreen(canvas, it, progress(it, t), w, h) }
                if (s.date) {
                    calendar.timeInMillis = System.currentTimeMillis()
                    val line = String.format(Locale.US, "%02d.%02d.%04d  ·  %02d:%02d  ·  %.1f сут/с",
                        calendar.get(Calendar.DAY_OF_MONTH), calendar.get(Calendar.MONTH) + 1,
                        calendar.get(Calendar.YEAR), calendar.get(Calendar.HOUR_OF_DAY),
                        calendar.get(Calendar.MINUTE), s.speed)
                    canvas.drawText(line, w / 2f, h - dp(20f), textPaint)
                }
                canvas.drawRect(0f, 0f, w, h, vignettePaint)
            } finally {
                try { holder.unlockCanvasAndPost(canvas) } catch (_: IllegalArgumentException) {}
            }
        }

        private fun progress(ev: CosmicEvent, t: Double) = ((t - ev.start) / ev.type.dur).toFloat().coerceIn(0f, 1f)
        private fun smooth(p: Float) = p * p * (3f - 2f * p)
        private fun argbA(a: Int, c: Int) = Color.argb(a.coerceIn(0, 255), Color.red(c), Color.green(c), Color.blue(c))
        private fun angleDeg(i: Int): Double {
            val p = planets[i]
            return (p.l0 + 360.0 * simDays / p.periodDays) % 360.0
        }
        private fun planetPos(i: Int, p: Planet): Triple<Float, Float, Float> {
            val a = Math.toRadians(angleDeg(i))
            val r = orbitRects[i].width() / 2f
            return Triple(cx + (r * cos(a)).toFloat(), cy + (r * yScale * sin(a)).toFloat(), sin(a).toFloat())
        }
        private fun drawTrails(canvas: Canvas, s: OrreryPrefs.Settings) {
            val len = 8f + 46f * s.trail
            val seg = 12
            for (i in planets.indices) {
                val a = angleDeg(i).toFloat()
                val depth = planetPos(i, planets[i]).third
                val mul = if (depth < 0) 0.45f else 1f
                val c = planets[i].color
                for (k in 0 until seg) {
                    val f = k.toFloat() / seg
                    trailPaint.color = Color.argb((90 * f * mul * (0.4f + 0.6f * s.glow)).toInt().coerceIn(0, 255),
                        Color.red(c), Color.green(c), Color.blue(c))
                    canvas.drawArc(orbitRects[i], a - len + len * f, len / seg + 0.6f, false, trailPaint)
                }
            }
        }
        private fun drawChapterRing(canvas: Canvas) {
            val r = rOut * 1.07f
            canvas.drawOval(RectF(cx - r, cy - r * yScale, cx + r, cy + r * yScale), orbitPaint)
            for (i in 0 until 24) {
                val a = Math.toRadians((i * 15 - 90).toDouble())
                val x1 = cx + (r * cos(a)).toFloat(); val y1 = cy + (r * yScale * sin(a)).toFloat()
                val r2 = r - dp(if (i % 6 == 0) 10f else 5f)
                canvas.drawLine(x1, y1, cx + (r2 * cos(a)).toFloat(), cy + (r2 * yScale * sin(a)).toFloat(), tickPaint)
            }
            calendar.timeInMillis = System.currentTimeMillis()
            val hf = calendar.get(Calendar.HOUR_OF_DAY) + calendar.get(Calendar.MINUTE) / 60f
            val a = Math.toRadians((hf / 24f * 360f - 90f).toDouble())
            bodyPaint.color = Color.argb(220, 120, 210, 255)
            canvas.drawCircle(cx + (r * cos(a)).toFloat(), cy + (r * yScale * sin(a)).toFloat(), dp(3f), bodyPaint)
        }
        private fun drawSun(canvas: Canvas, t: Double) {
            val breath = (1.0 + 0.05 * sin(t * 0.8)).toFloat()
            canvas.save(); canvas.translate(cx, cy); canvas.scale(breath, breath)
            canvas.drawCircle(0f, 0f, dp(14f) * 5f, sunPaint)
            canvas.drawCircle(0f, 0f, dp(14f), sunCore)
            canvas.restore()
        }
        private fun drawPlanet(canvas: Canvas, i: Int, pos: Triple<Float, Float, Float>, alphaMul: Float) {
            val p = planets[i]
            val (x, y, depth) = pos
            val scale = 0.85f + 0.3f * ((depth + 1f) / 2f)
                    val approach = (p / 0.45f).coerceIn(0f, 1f)
                        val ip = smooth(((p - 0.45f) / 0.55f).coerceIn(0f, 1f))
                canvas.drawOval(RectF(-mr, -mr * yScale, mr, mr * yScale), orbitPaint)
                    val hx = ev.x + cos(ev.angle) * worldW * travel
                    eventPaint.color = Color.WHITE
                    eventPaint.shader = null
        private fun drawPlanet(canvas: Canvas, i: Int, pos: Triple<Float, Float, Float>, alphaMul: Float) {
            canvas.drawCircle(0f, 0f, dp(14f) * 5f, sunPaint)
            if (p.ring) {
                    canvas.drawLine(tx, ty, hx, hy, eventStroke)
        private fun drawSun(canvas: Canvas, t: Double) {
                        for (k in 0 until 14) {
                        val ax = tp.first + cos(ev.angle) * dist
                    eventPaint.shader = null
            for (i in 0 until 24) {
        }
                        eventStroke.color = argbA((220 * (1f - ip) * fade).toInt(), Color.rgb(255, 150, 80))
                    canvas.drawCircle(hx, hy, dp(26f), eventPaint)
            val hf = calendar.get(Calendar.HOUR_OF_DAY) + calendar.get(Calendar.MINUTE) / 60f
            val breath = (1.0 + 0.05 * sin(t * 0.8)).toFloat()
                            argbA((120 * (1f - p) * fade).toInt(), Color.rgb(120, 200, 255)),
                        intArrayOf(argbA((220 * fade).toInt(), Color.WHITE), Color.argb(0, 120, 220, 255)),
            val a = Math.toRadians((hf / 24f * 360f - 90f).toDouble())
            bodyPaint.color = argbA((255 * alphaMul).toInt(), p.color)
                        eventPaint.color = argbA((255 * fade).toInt(), Color.rgb(255, 230, 180))
                    val travel = p * 1.5f - 0.25f
                            eventPaint.color = argbA((180 * (1f - ip)).toInt(), Color.rgb(255, 170, 90))
                        tile = Shader.TileMode.CLAMP)
            canvas.drawCircle(0f, 0f, dp(14f), sunCore)
                val a = Math.toRadians((i * 15 - 90).toDouble())
                        floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        }
                        color0 = Color.argb(0, 120, 220, 255), color1 = argbA((220 * fade).toInt(), Color.rgb(190, 240, 255)),

            canvas.drawCircle(0f, 0f, size * 3.2f, glowPaints[i])
                canvas.restore()
            canvas.save(); canvas.translate(cx, cy); canvas.scale(breath, breath)
            canvas.restore()
            val (x, y, depth) = pos
        // ==================== события: отрисовка ====================
                    canvas.drawCircle(ev.x, ev.y, r, eventPaint)
                EventType.COMET -> {
                val mr = dp(11f)
                canvas.drawLine(x1, y1, cx + (r2 * cos(a)).toFloat(), cy + (r2 * yScale * sin(a)).toFloat(), tickPaint)
                }
                    eventPaint.shader = RadialGradient(ev.x, ev.y, r.coerceAtLeast(1f),
                        eventStroke.color = argbA((200 * fade).toInt(), Color.rgb(255, 190, 120))
                bodyPaint.color = Color.argb((210 * alphaMul).toInt(), 200, 205, 215)
                        val dist = dp(520f) * (1f - approach)
        }
                        eventStroke.strokeWidth = dp(2.4f)
                    }
            }
                    val tp = pos[ev.target]
                val r2 = r - dp(if (i % 6 == 0) 10f else 5f)
                        eventStroke.strokeWidth = dp(2f)
                    eventStroke.shader = LinearGradient(ax0 = tx, ay0 = ty, ax1 = hx, ay1 = hy,
                canvas.drawOval(-size * 2.2f, -size * 0.9f, size * 2.2f, size * 0.9f, ringPaint)
            if (p.moon) {
                        canvas.drawCircle(tp.first, tp.second, dp(planets[ev.target].sizeDp) * 1.15f, eventPaint)
                    val tx = hx - cos(ev.angle) * tl; val ty = hy - sin(ev.angle) * tl * yScale
                        }
            calendar.timeInMillis = System.currentTimeMillis()
            canvas.drawCircle(cx + (r * cos(a)).toFloat(), cy + (r * yScale * sin(a)).toFloat(), dp(3f), bodyPaint)
            val size = dp(p.sizeDp) * scale
                    if (p < 0.45f) {
                val ma = Math.toRadians((360.0 * simDays / 27.32) % 360.0)
                    canvas.drawCircle(hx, hy, dp(2.6f), eventPaint)
                    eventPaint.color = argbA((255 * fade).toInt(), Color.rgb(240, 250, 255))
                canvas.save(); canvas.rotate(-18f)
                            val a = rnd.nextFloat() * 6.283f
                        val ay = tp.second + sin(ev.angle) * dist * yScale
                            Color.argb(0, 40, 90, 200)), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
            canvas.drawCircle(0f, 0f, size, bodyPaint)
            }
                EventType.ASTEROID_IMPACT -> {
                        intArrayOf(argbA((230 * (1f - p) * fade).toInt(), Color.WHITE),
                canvas.drawCircle((mr * cos(ma)).toFloat(), (mr * yScale * sin(ma)).toFloat(), dp(1.6f), bodyPaint)
                        canvas.drawCircle(tp.first, tp.second, dp(8f) + ip * dp(70f), eventStroke)
            canvas.save(); canvas.translate(x, y)
                }
                    canvas.drawCircle(ev.x, ev.y, dp(5f) * (1f + p), eventPaint)
                            val d = ip * dp(90f) * (0.4f + rnd.nextFloat())
                EventType.SOLAR_FLARE -> {
                    val fp = smooth(p)
                    eventStroke.color = argbA((200 * fade * (1f - p * 0.5f)).toInt(), Color.rgb(255, 190, 90))
                    eventStroke.strokeWidth = dp(2f)
                    for (k in 0 until 3) {
                        val a = ev.angle + k * 2.1f
                        val sx = cx + cos(a) * dp(14f); val sy = cy + sin(a) * dp(14f) * yScale
                        val ex = cx + cos(a) * dp(14f + 120f * fp); val ey = cy + sin(a) * dp(14f + 120f * fp) * yScale
                        eventPath.rewind()
                        eventPath.moveTo(sx, sy)
                        eventPath.quadTo((sx + ex) / 2f + cos(a + 1.57f) * dp(60f) * fp, (sy + ey) / 2f + sin(a + 1.57f) * dp(60f) * fp, ex, ey)
                        canvas.drawPath(eventPath, eventStroke)
                    }
                }
                EventType.METEOR_SHOWER -> {
                    for (k in 0 until 16) {
                        val sp = (p * 1.7f - k * 0.055f).coerceIn(0f, 1f)
                        if (sp <= 0f || sp >= 1f) continue
                        val sx = rnd.nextFloat() * worldW; val sy = rnd.nextFloat() * surfaceH * 0.6f
                        val len = dp(90f) * (1f - sp)
                        val a = ev.angle
                        eventStroke.color = Color.argb((180 * (1f - sp) * fade).toInt(), 200, 230, 255)
                        eventStroke.strokeWidth = dp(1.4f)
                        canvas.drawLine(sx + cos(a) * len, sy + sin(a) * len, sx, sy, eventStroke)
                    }
                }    
                EventType.RING_SHIMMER -> {
                        eventStroke.color = Color.argb((140 * fade * (1f - k * 0.25f)).toInt(), 200, 230, 255)
                        val rr = size * (2.2f + k * 0.5f) + smooth(p) * dp(10f)
                    for (k in 0 until 3) {
                    canvas.drawPath(eventPath, eventStroke)
                        canvas.drawCircle(q.first, q.second, dp(4f), eventPaint)
                    val size = dp(planets[5].sizeDp)
}
                    eventStroke.color = argbA((200 * fade).toInt(), Color.rgb(255, 200, 120))
                    eventStroke.strokeWidth = dp(2f)
                    for (k in 0 until 16) {
                    eventPaint.shader = LinearGradient(0f, 0f, len, 0f,
                        floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
                    eventPaint.shader = null
                            canvas.drawCircle(tp.first + cos(a) * d, tp.second + sin(a) * d * yScale, dp(1.4f), eventPaint)
                EventType.COMET -> {
                        eventPath.rewind()
                        canvas.drawLine(sx + cos(a) * len, sy + sin(a) * len, sx, sy, eventStroke)
                    eventStroke.color = argbA((160 * fade).toInt(), Color.rgb(255, 215, 130))
                        eventPaint.color = argbA((180 * fade).toInt(), Color.rgb(255, 230, 160))
                        }
                        eventPaint.color = Color.argb((140 * (1f - ip)).toInt(), 40, 10, 5)
                        intArrayOf(argbA((220 * fade).toInt(), Color.WHITE), Color.argb(0, 120, 220, 255)),
                        canvas.drawCircle(tp.first, tp.second, dp(planets[ev.target].sizeDp) * 1.15f, eventPaint)
                    eventStroke.shader = LinearGradient(ax0 = tx, ay0 = ty, ax1 = hx, ay1 = hy,
                        val len = dp(90f) * (1f - sp)
                }
                    val fp = smooth(p)
                    for (i in 1 until pos.size) eventPath.lineTo(pos[i].first, pos[i].second)
                    eventPaint.color = Color.WHITE
                    eventPaint.color = Color.BLACK
                        val sx = rnd.nextFloat() * worldW; val sy = rnd.nextFloat() * surfaceH * 0.6f
                    eventPath.rewind()
                    eventPath.moveTo(pos[0].first, pos[0].second)
                        floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
                        eventStroke.color = Color.argb((90 * fade).toInt(), 180, 220, 255)
                    val tl = dp(200f)
                    val q = pos[5]
                    val hy = ev.y + sin(ev.angle) * surfaceH * travel * 0.6f
                        val ex = cx + cos(a) * dp(14f + 120f * fp); val ey = cy + sin(a) * dp(14f + 120f * fp) * yScale
                    for (k in 0 until 10) {
                    }
                        val sp = (p * 1.7f - k * 0.055f).coerceIn(0f, 1f)
                    canvas.drawCircle(hx, hy, dp(26f), eventPaint)
                    canvas.drawCircle(bx, by, r * 1.5f, eventStroke)
                    val hx = ev.x + cos(ev.angle) * worldW * travel
                EventType.METEOR_SHOWER -> {
                        val d = r * (2f + rnd.nextFloat() * 3f)
                    eventPaint.shader = null
                    for (q in pos) {
                }
                    }
                    eventStroke.strokeWidth = dp(1.6f)
                        eventStroke.strokeWidth = dp(1.2f)
                        canvas.save(); canvas.translate(q.first, q.second); canvas.rotate(-18f + k * 8f * smooth(p))
                        canvas.drawOval(-rr, -rr * 0.4f, rr, rr * 0.4f, eventStroke)
                        canvas.restore()
                    }
                }
                EventType.EARTH_AURORA -> {
                    val q = pos[2]
                    val size = dp(planets[2].sizeDp)
                    for (k in 0 until 3) {
                        val rr = size * (1.6f + k * 0.5f) + sin(t * 2 + k).toFloat() * dp(2f)
                        eventStroke.color = Color.argb((150 * fade * (1f - k * 0.3f)).toInt(), 80, 255, 170)
                        eventStroke.strokeWidth = dp(1.6f)
                        canvas.drawArc(RectF(q.first - rr, q.second - rr, q.first + rr, q.second + rr), 200f + k * 20f, 140f, false, eventStroke)
                    }
                }
                    EventType.WORMHOLE -> {
                    eventPaint.shader = null
                        canvas.drawLine(bx + cos(a) * d, by + sin(a) * d, bx + cos(a) * (d + dp(14f)), by + sin(a) * (d + dp(14f)), eventStroke)
                    val size = dp(planets[5].sizeDp)
                EventType.NEBULA_BLOOM -> {
                    val bx = worldW * (p * 1.4f - 0.2f)
                    eventPaint.color = Color.argb((235 * fade).toInt(), 2, 2, 6)

                    for (k in 0 until 3) {
                    eventPaint.shader = LinearGradient(0f, 0f, len, 0f,
                        floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
                    eventPaint.shader = null
                    eventStroke.color = argbA((160 * fade).toInt(), Color.rgb(255, 215, 130))
                        eventPaint.color = argbA((180 * fade).toInt(), Color.rgb(255, 230, 160))
                }
                }
                }
                    for (i in 1 until pos.size) eventPath.lineTo(pos[i].first, pos[i].second)
                            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
                    val r = dp(10f)
                    eventPath.rewind()
                    eventPath.moveTo(pos[0].first, pos[0].second)
                        eventStroke.strokeWidth = dp(1.2f)
                    }
                    canvas.drawCircle(ev.x, ev.y, r, eventStroke)
                        val c = when (k) { 0 -> Color.rgb(60, 140, 255); 1 -> Color.rgb(160, 80, 255); else -> Color.rgb(60, 220, 200) }
                        canvas.drawCircle(nx, ny, r, eventPaint)
                        eventStroke.color = Color.argb((90 * fade).toInt(), 180, 220, 255)
                        val r = maxOf(worldW, surfaceH.toFloat()) * (0.25f + 0.1f * k)
                    eventPaint.color = argbA((255 * fade).toInt(), Color.rgb(255, 235, 200))
                    }
                    val q = pos[5]
                    for (k in 0 until 10) {
                    for (k in 0 until 6) {
                    canvas.drawCircle(ev.x, ev.y, r * 0.35f, eventPaint)
                EventType.VACUUM_DECAY -> {
                            intArrayOf(Color.argb((40 * fade).toInt(), Color.red(c), Color.green(c), Color.blue(c)), Color.argb(0, 0, 0, 0)),
                }
                        val d = r * (2f + rnd.nextFloat() * 3f)
                        canvas.save(); canvas.translate(ev.x, ev.y); canvas.rotate((t * 20f + k * 15f).toFloat())
                        canvas.drawOval(-rr, -rr * 0.6f, rr, rr * 0.6f, eventStroke)
            }
                        eventStroke.strokeWidth = dp(1.4f)
                    val size = dp(planets[2].sizeDp)
                        intArrayOf(argbA((200 * fade).toInt(), Color.rgb(255, 210, 140)), Color.argb(0, 255, 140, 60)),
                EventType.EARTH_AURORA -> {
                        canvas.restore()
                    canvas.drawCircle(bx, by, r, eventPaint)
        }
                    for (q in pos) {
                    }
                    eventPaint.color = Color.argb((200 * fade).toInt(), 10, 8, 24)
                        floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
                        val rr = r * (1f - k * 0.13f)
                        eventStroke.color = Color.argb((150 * fade * (1f - k * 0.3f)).toInt(), 80, 255, 170)
                        eventPaint.shader = RadialGradient(nx, ny, r,
                        canvas.drawOval(-rr, -rr * 0.4f, rr, rr * 0.4f, eventStroke)
                    eventStroke.strokeWidth = dp(1.6f)
                        eventStroke.strokeWidth = dp(1.6f)
                    }
                        val ny = ev.y + sin(ev.angle + k * 2f) * surfaceH * 0.1f * p
            val fade = min(1f, min(p / 0.1f, (1f - p) / 0.15f))
                    val by = surfaceH * 0.25f
                        val rr = size * (1.6f + k * 0.5f) + sin(t * 2 + k).toFloat() * dp(2f)
                    eventPaint.shader = RadialGradient(bx, by, r * 6f,
                        canvas.save(); canvas.translate(q.first, q.second); canvas.rotate(-18f + k * 8f * smooth(p))
                }
                        canvas.restore()
                    val q = pos[2]
                        eventStroke.color = Color.argb((160 * fade * (1f - k * 0.14f)).toInt(), if (k % 2 == 0) 120 else 200, 160, 255)
            // вспышки на весь экран
            if (ev.type == EventType.SUPERNOVA || ev.type == EventType.GAMMA_BURST) {
                val fl = if (p < 0.25f) smooth(p / 0.25f) else (1f - smooth((p - 0.25f) / 0.75f))
                eventPaint.shader = null
                eventPaint.color = Color.argb((70 * fl * fade).toInt(), 200, 235, 255)
                canvas.drawRect(0f, 0f, w, h, eventPaint)
            }
            // название события
            labelPaint.color = Color.argb((170 * fade).toInt(), 190, 220, 245)
            canvas.drawText("✦ " + ev.type.title, dp(16f), h - dp(44f), labelPaint)
            // перезапуск вселенной для катастроф
            if (ev.cat && p > 0.7f) {
                val rp = smooth(((p - 0.7f) / 0.3f).coerceIn(0f, 1f))
                eventPaint.color = Color.argb((235 * rp).toInt(), 1, 2, 5)
                canvas.drawRect(0f, 0f, w, h, eventPaint)
                val ta = (255 * min(1f, rp * 2f)).toInt()
                textPaint.color = Color.argb(ta, 210, 235, 255)
                textPaint.textSize = dp(16f)
                canvas.drawText("ПЕРЕЗАПУСК ВСЕЛЕННОЙ", w / 2f, h / 2f - dp(8f), textPaint)
                textPaint.textSize = dp(11f)
                textPaint.color = Color.argb((ta * 0.7f).toInt(), 140, 180, 215)
                canvas.drawText("восстановление модели орбит…", w / 2f, h / 2f + dp(16f), textPaint)
                eventStroke.color = Color.argb(ta, 90, 180, 230)
                eventStroke.strokeWidth = dp(2f)
                canvas.drawArc(RectF(w / 2f - dp(26f), h / 2f + dp(34f), w / 2f + dp(26f), h / 2f + dp(86f)),
                    -90f, 360f * rp, false, eventStroke)
                // вернуть стили текста
                textPaint.textSize = dp(12f)
                textPaint.color = Color.argb(185, 210, 230, 245)
            }
        }
    }
                                                       }
