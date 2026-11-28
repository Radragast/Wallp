package com.example.gearwallpaper

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.*
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.service.wallpaper.WallpaperService
import android.view.Choreographer
import android.view.SurfaceHolder
import java.util.Calendar
import java.util.Locale
import java.util.Random
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private data class OrreryStar(val x: Float, val y: Float, val r: Float, val ph: Float, val a: Int)

class OrreryWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = OrreryEngine()

    inner class OrreryEngine : Engine(), Choreographer.FrameCallback {
        private val minFrameNanos = 33_000_000L
        @Volatile private var visible = false
        @Volatile private var surfaceValid = false
        @Volatile private var surfaceW = 0
        @Volatile private var surfaceH = 0
        @Volatile private var layoutDirty = true
        @Volatile private var xOffset = 0.5f
        @Volatile private var pages = 1
        @Volatile private var settings = OrreryPrefs.Settings()

        private val startTime = System.nanoTime()
        private var lastT = 0.0
        private val threadLock = Any()
        private var renderThread: HandlerThread? = null
        private var renderHandler: Handler? = null
        private var choreographer: Choreographer? = null
        private var frameScheduled = false
        private var lastFrameNanos = 0L

        private val prefs = this@OrreryWallpaperService.getSharedPreferences(OrreryPrefs.NAME, MODE_PRIVATE)
        private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                OrreryPrefs.FORCE_RANDOM -> sim.pendingRandom = true
                OrreryPrefs.FORCE_CAT -> sim.pendingCat = true
                OrreryPrefs.FORCE_RESTORE -> sim.pendingRestore = true
            }
            settings = OrreryPrefs.read(prefs)
            layoutDirty = true
            updateSensor()
        }

        private val sensorManager =
            this@OrreryWallpaperService.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        private val tiltSensor: Sensor? =
            sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
                ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        @Volatile private var tiltX = 0f
        @Volatile private var tiltY = 0f
        private var sensorRegistered = false
        private val sensorListener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val nx = (e.values[0] / 9.81f).coerceIn(-1f, 1f)
                val ny = (e.values[1] / 9.81f).coerceIn(-1f, 1f)
                tiltX += (-nx - tiltX) * 0.12f
                tiltY += (ny - tiltY) * 0.12f
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        private var charging = false
        private var batteryLevel = 1f
        private var batteryTimer = 0f
        private var saveTimer = 0f
        private var stateLoaded = false

        private val sim = OrrerySim()
        private val rng = Random(7L)
        private var stars = emptyList<OrreryStar>()
        private val tex = HashMap<Int, Bitmap>()
        private val tints = HashMap<Int, PorterDuffColorFilter>()
        private var shadeBmp: Bitmap? = null
        private var glowBmp: Bitmap? = null

        private var density = 1f
        private var baseScale = 1f
        private var sw = 0f
        private var sh = 0f
        private var yScale = 0.6f
        private var s = 1f
        private var pageShift = 0f
        private var camX = 0f
        private var camY = 0f
        private var camZoom = 1f
        private var shotT = 0f
        private var targetIdx = 2
        private var closeZ = 4f
        private val wideZ = 1f
        private val inDur = 7f
        private val holdDur = 5f
        private val outDur = 13f
        private val wideDur = 9f

        private val bg = Paint()
        private val nebA = Paint()
        private val nebB = Paint()
        private val vig = Paint()
        private val orb = Paint(Paint.ANTI_ALIAS_FLAG)
        private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
        private val disc = Paint(Paint.ANTI_ALIAS_FLAG)
        private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG)
        private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        private val glowPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        private val cardFill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val cardEdge = Paint(Paint.ANTI_ALIAS_FLAG)
        private val title = Paint(Paint.ANTI_ALIAS_FLAG)
        private val body = Paint(Paint.ANTI_ALIAS_FLAG)
        private val tag = Paint(Paint.ANTI_ALIAS_FLAG)
        private val small = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()
        private val cal = Calendar.getInstance()

        init {
            settings = OrreryPrefs.read(prefs)
            prefs.registerOnSharedPreferenceChangeListener(listener)
            orb.style = Paint.Style.STROKE
            stroke.style = Paint.Style.STROKE
            stroke.strokeCap = Paint.Cap.ROUND
            cardEdge.style = Paint.Style.STROKE
            title.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            body.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            tag.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            small.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }

        override fun onVisibilityChanged(v: Boolean) {
            visible = v
            if (v) start() else stop()
        }
        override fun onSurfaceCreated(h: SurfaceHolder) { super.onSurfaceCreated(h); surfaceValid = true }
        override fun onSurfaceChanged(h: SurfaceHolder, f: Int, w: Int, hh: Int) {
            super.onSurfaceChanged(h, f, w, hh)
            surfaceW = w; surfaceH = hh; layoutDirty = true
            if (visible) start()
        }
        override fun onSurfaceDestroyed(h: SurfaceHolder) { super.onSurfaceDestroyed(h); surfaceValid = false; stop() }
        override fun onDestroy() {
            super.onDestroy()
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
            if (sensorRegistered) {
                sensorManager.unregisterListener(sensorListener)
                sensorRegistered = false
            }
            visible = false
            stop()
        }
        override fun onOffsetsChanged(xo: Float, yo: Float, xs: Float, ys: Float, xp: Int, yp: Int) {
            xOffset = xo.coerceIn(0f, 1f)
            val p = if (xs > 0.0001f) (1f / xs).toInt() + 1 else 1
            val c = p.coerceIn(1, 9)
            if (c != pages) { pages = c; layoutDirty = true }
        }

        private fun updateSensor() {
            val want = visible && settings.sensorTilt && tiltSensor != null
            if (want && !sensorRegistered) {
                sensorManager.registerListener(sensorListener, tiltSensor, SensorManager.SENSOR_DELAY_UI)
                sensorRegistered = true
            } else if (!want && sensorRegistered) {
                sensorManager.unregisterListener(sensorListener)
                sensorRegistered = false
                tiltX = 0f
                tiltY = 0f
            }
        }

        private fun readBattery() {
            val i = this@OrreryWallpaperService.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (i != null) {
                val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
                charging = plugged != 0
                val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 100)
                val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                batteryLevel = if (scale > 0) level.toFloat() / scale else 1f
            }
        }

        private fun start() {
            updateSensor()
            readBattery()
            synchronized(threadLock) {
                val ex = renderHandler
                if (ex != null) ex.post { schedule() }
                else {
                    val th = HandlerThread("OrreryRender", Process.THREAD_PRIORITY_DISPLAY)
                    th.start()
                    val h = Handler(th.looper)
                    renderThread = th; renderHandler = h
                    h.post { choreographer = Choreographer.getInstance(); schedule() }
                }
            }
        }
       private fun stop() {
            if (stateLoaded) OrreryState.store(this@OrreryWallpaperService, sim)
            if (sensorRegistered) {
                sensorManager.unregisterListener(sensorListener)
                sensorRegistered = false
            }
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
        private fun schedule() {
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
                render((System.nanoTime() - startTime) / 1e9)
            }
            schedule()
        }

        private fun dp(v: Float) = v * density
        private fun smooth(p: Float) = p * p * (3f - 2f * p)
        private fun px(x: Float) = (x - camX) * s + sw / 2f + pageShift
        private fun py(y: Float) = (y * yScale - camY) * s + sh / 2f
        private fun rad(b: SimBody) = max(1.2f, b.radius * s * settings.planetSize)

        private fun rebuild() {
            val w = surfaceW.toFloat()
            val h = surfaceH.toFloat()
            if (w <= 0f || h <= 0f) return
            layoutDirty = false
            sw = w; sh = h
            density = resources.displayMetrics.density
            baseScale = min(w, h) / 1700f
            yScale = 1f - 0.6f * settings.tilt
            if (tex.isEmpty()) {
                for (i in sim.bodies.indices) {
                    val b = sim.bodies[i]
                    val size = if (b.parent >= 0) 192 else 288
                    tex[i] = OrreryTextures.body(b.name, b.c1, b.c2, size, 1000L + i * 37L)
                    tints[i] = PorterDuffColorFilter(b.c1, PorterDuff.Mode.SRC_IN)
                }
                shadeBmp = OrreryTextures.shade(256)
                glowBmp = OrreryTextures.glow(128)
            }
            if (!stateLoaded) {
                stateLoaded = true
                OrreryState.restore(this@OrreryWallpaperService, sim, settings)
            }
            val r = Random(42L)
            stars = List((150 * settings.stars).toInt().coerceAtLeast(30)) {
                OrreryStar(r.nextFloat() * w, r.nextFloat() * h,
                    dp(0.5f + r.nextFloat() * 1.3f), r.nextFloat() * 6.28f, 50 + r.nextInt(150))
            }
            bg.shader = LinearGradient(0f, 0f, 0f, h,
                intArrayOf(Color.rgb(3, 5, 11), Color.rgb(9, 15, 32)),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
            nebA.shader = RadialGradient(w * 0.28f, h * 0.3f, max(w, h) * 0.75f,
                intArrayOf(Color.argb(24, 46, 108, 200), Color.argb(0, 0, 0, 0)),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
            nebB.shader = RadialGradient(w * 0.78f, h * 0.74f, max(w, h) * 0.65f,
                intArrayOf(Color.argb(18, 110, 60, 180), Color.argb(0, 0, 0, 0)),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
            vig.shader = RadialGradient(w / 2f, h / 2f, max(w, h) * 0.74f,
                intArrayOf(Color.argb(0, 0, 0, 0), Color.argb(0, 0, 0, 0), Color.argb(180, 0, 0, 0)),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            orb.strokeWidth = dp(0.8f)
            title.textSize = dp(10.5f)
            title.letterSpacing = 0.16f
            body.textSize = dp(11.5f)
            tag.textSize = dp(8.5f)
            tag.letterSpacing = 0.2f
            small.textSize = dp(10f)
            cardEdge.strokeWidth = dp(0.9f)
        }

        private fun pickTarget() {
            val live = ArrayList<Int>()
            for (i in sim.bodies.indices) {
                val b = sim.bodies[i]
                if (b.pop > 1e3 || b.destroyed || b.scars > 0.3f) live.add(i)
            }
            targetIdx = if (live.isNotEmpty() && rng.nextFloat() < 0.65f) live[rng.nextInt(live.size)]
            else rng.nextInt(sim.bodies.size)
            val b = sim.bodies[targetIdx]
            val want = min(sw, sh) * (if (b.parent >= 0) 0.075f else 0.115f)
            closeZ = (want / max(0.5f, b.radius * settings.planetSize * baseScale)).coerceIn(2.5f, 26f)
        }

        private fun render(t: Double) {
            val holder = surfaceHolder
            val canvas = try { holder.lockHardwareCanvas() } catch (e: Exception) {
                try { holder.lockCanvas() } catch (e2: Exception) { null }
            } ?: return
            try {
                val dt = if (lastT == 0.0) 0.016f else (t - lastT).toFloat().coerceIn(0f, 0.05f)
                lastT = t
                batteryTimer += dt
                if (batteryTimer >= 2f) { batteryTimer = 0f; readBattery() }
                val boost = settings.chargeReaction && charging
                sim.civSpeed = settings.civSpeed * (if (boost) 1.6f else 1f)
                sim.moonSpeed = settings.moonSpeed
                sim.eventRate = settings.rate * (if (boost) 1.35f else 1f)
                sim.eventsOn = settings.events
                sim.catOn = settings.catastrophes
                sim.update(dt, settings.speed)
                saveTimer += dt
                if (saveTimer >= 30f) {
                    saveTimer = 0f
                    OrreryState.store(this@OrreryWallpaperService, sim)
                }

                if (settings.camera) {
                    shotT += dt
                    val total = inDur + holdDur + outDur + wideDur
                    if (shotT >= total) { shotT = 0f; pickTarget() }
                    val k = when {
                        shotT < inDur -> smooth(shotT / inDur)
                        shotT < inDur + holdDur -> 1f
                        shotT < inDur + holdDur + outDur -> 1f - smooth((shotT - inDur - holdDur) / outDur)
                        else -> 0f
                    }
                    val b = sim.bodies[targetIdx]
                    camX = b.x * k + sin(t * 0.07).toFloat() * 5f
                    camY = b.y * yScale * k + cos(t * 0.05).toFloat() * 4f
                    camZoom = wideZ + (closeZ - wideZ) * k
                } else {
                    camX = 0f; camY = 0f; camZoom = wideZ
                }
                val ts = if (settings.sensorTilt) settings.tiltStrength else 0f
                camX += tiltX * 55f * ts
                camY += tiltY * 38f * ts
                s = baseScale * camZoom * settings.systemScale
                pageShift = if (settings.multiScreen) -(xOffset - 0.5f) * sw * (pages - 1) * 0.5f else 0f

                canvas.drawRect(0f, 0f, sw, sh, bg)
                canvas.drawRect(0f, 0f, sw, sh, nebA)
                canvas.drawRect(0f, 0f, sw, sh, nebB)
                drawStars(canvas, t, ts)
                drawOrbits(canvas)
                drawSwarm(canvas)
                val order = sim.bodies.indices.sortedBy { sim.bodies[it].y }
                for (i in order) if (sim.bodies[i].y < 0f) drawBody(canvas, i)
                drawSun(canvas, t)
                for (i in order) if (sim.bodies[i].y >= 0f) drawBody(canvas, i)
                for (e in sim.events) drawEvent(canvas, e, t)
                if (settings.facts) drawCard(canvas)
                drawHud(canvas)
                canvas.drawRect(0f, 0f, sw, sh, vig)
            } finally {
                try { holder.unlockCanvasAndPost(canvas) } catch (_: IllegalArgumentException) {}
            }
        }

        private fun drawStars(canvas: Canvas, t: Double, ts: Float) {
            val off = -camX * 0.02f * s + pageShift * 0.3f + tiltX * dp(16f) * ts
            for (st in stars) {
                var x = (st.x + off) % sw
                if (x < 0f) x += sw
                val tw = 0.55f + 0.45f * sin(t * 0.6 + st.ph).toFloat()
                dot.color = Color.argb((st.a * tw).toInt().coerceIn(0, 255), 208, 228, 250)
                canvas.drawCircle(x, st.y, st.r, dot)
            }
        }

        private fun drawOrbits(canvas: Canvas) {
            val cx = px(0f); val cy = py(0f)
            orb.color = Color.argb(44, 120, 180, 225)
            for (b in sim.bodies) {
                if (b.parent >= 0) continue
                val r = b.orbit * s
                canvas.drawOval(RectF(cx - r, cy - r * yScale, cx + r, cy + r * yScale), orb)
            }
            if (s > 2.2f) {
                orb.color = Color.argb(36, 140, 200, 235)
                for (b in sim.bodies) {
                    if (b.parent < 0) continue
                    val p = sim.bodies[b.parent]
                    val mx = px(p.x); val my = py(p.y)
                    val r = b.orbit * s
                    canvas.drawOval(RectF(mx - r, my - r * yScale, mx + r, my + r * yScale), orb)
                }
            }
            val cr = 800f * s
            orb.color = Color.argb(38, 150, 200, 235)
            canvas.drawOval(RectF(cx - cr, cy - cr * yScale, cx + cr, cy + cr * yScale), orb)
            stroke.strokeWidth = dp(1f)
            stroke.color = Color.argb(65, 150, 205, 240)
            for (i in 0 until 24) {
                val a = Math.toRadians((i * 15 - 90).toDouble())
                val r2 = cr - dp(if (i % 6 == 0) 9f else 4f)
                canvas.drawLine(cx + (cr * cos(a)).toFloat(), cy + (cr * yScale * sin(a)).toFloat(),
                    cx + (r2 * cos(a)).toFloat(), cy + (r2 * yScale * sin(a)).toFloat(), stroke)
            }
            cal.timeInMillis = System.currentTimeMillis()
            val hf = cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60f
            val ha = Math.toRadians((hf / 24f * 360f - 90f).toDouble())
            dot.color = Color.argb(215, 201, 162, 39)
            canvas.drawCircle(cx + (cr * cos(ha)).toFloat(), cy + (cr * yScale * sin(ha)).toFloat(), dp(3f), dot)
        }

        private fun drawSwarm(canvas: Canvas) {
            if (sim.swarm <= 0.001f) return
            val n = (sim.swarm * 150f).toInt()
            val cx = px(0f); val cy = py(0f)
            for (k in 0 until n) {
                val a = k * 2.3999 + sim.simDays * 0.03
                val r = (70f + (k % 5) * 6f) * s
                dot.color = Color.argb(190, 235, 200, 130)
                canvas.drawCircle(cx + (r * cos(a)).toFloat(), cy + (r * yScale * sin(a)).toFloat(),
                    max(0.8f, dp(1.1f)), dot)
            }
        }

         private fun drawSun(canvas: Canvas, t: Double) {
            val cx = px(0f); val cy = py(0f)
            val r = max(2f, 34f * s)
            val boost = settings.chargeReaction && charging
            val power = if (settings.chargeReaction) 0.5f + 0.5f * batteryLevel else 1f
            val dim = (1f - sim.swarm * 0.4f) * power
            val breath = (1.0 + 0.04 * sin(t * 0.7)).toFloat() * (if (boost) 1.18f else 1f)
            glow.shader = RadialGradient(cx, cy, max(1f, r * 6f * breath),
                intArrayOf(Color.argb((150 * dim).toInt().coerceIn(0, 255), 150, 215, 255),
                    Color.argb((55 * dim).toInt().coerceIn(0, 255), 70, 140, 235), Color.argb(0, 30, 80, 190)),
                floatArrayOf(0f, 0.32f, 1f), Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, r * 6f * breath, glow)
            glow.shader = null
            if (sim.swarm > 0.05f) {
                glow.shader = RadialGradient(cx, cy, max(1f, r * 9f),
                    intArrayOf(Color.argb((70 * sim.swarm).toInt(), 255, 150, 70), Color.argb(0, 120, 40, 0)),
                    floatArrayOf(0.3f, 1f), Shader.TileMode.CLAMP)
                canvas.drawCircle(cx, cy, r * 9f, glow)
                glow.shader = null
            }
            if (boost) {
                stroke.strokeWidth = dp(1.2f)
                stroke.color = Color.argb(120, 255, 210, 120)
                stroke.pathEffect = DashPathEffect(floatArrayOf(dp(6f), dp(10f)), -t.toFloat() * dp(20f))
                canvas.drawCircle(cx, cy, r * 2.2f, stroke)
                stroke.pathEffect = null
            }
            dot.color = Color.rgb((235 * dim).toInt().coerceIn(0, 255) + 15,
                (248 * dim).toInt().coerceIn(0, 255) + 6, 255)
            canvas.drawCircle(cx, cy, r, dot)
        }

        private fun ringArcs(canvas: Canvas, cx: Float, cy: Float, r: Float, front: Boolean) {
            canvas.save()
            canvas.translate(cx, cy)
            canvas.rotate(-16f)
            val inner = floatArrayOf(1.34f, 1.70f, 2.02f)
            val outer = floatArrayOf(1.62f, 1.96f, 2.20f)
            val alpha = intArrayOf(120, 165, 95)
            for (k in 0 until 3) {
                val rr = r * (inner[k] + outer[k]) / 2f
                stroke.strokeWidth = max(0.8f, r * (outer[k] - inner[k]))
                stroke.color = Color.argb(if (front) alpha[k] else alpha[k] * 3 / 5, 228, 214, 178)
                val oval = RectF(-rr, -rr * 0.34f, rr, rr * 0.34f)
                canvas.drawArc(oval, if (front) 0f else 180f, 180f, false, stroke)
            }
            canvas.restore()
        }

        private fun drawBody(canvas: Canvas, i: Int) {
            val b = sim.bodies[i]
            val cx = px(b.x); val cy = py(b.y)
            val r = rad(b)
            if (cx + r * 4f < 0f || cx - r * 4f > sw || cy + r * 4f < 0f || cy - r * 4f > sh) return

            if (b.destroyed) {
                stroke.strokeWidth = dp(1f)
                stroke.color = Color.argb(120, 150, 120, 110)
                val rr = r * 1.5f
                canvas.drawOval(RectF(cx - rr, cy - rr * yScale, cx + rr, cy + rr * yScale), stroke)
                val rb = b.rebuild
                if (rb > 0f) {
                    val nr = max(1f, r * rb)
                    glow.shader = RadialGradient(cx, cy, max(1f, r * 3f),
                        intArrayOf(Color.argb((150 * rb).toInt(), 130, 255, 210), Color.argb(0, 40, 160, 130)),
                        floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
                    canvas.drawCircle(cx, cy, r * 3f, glow)
                    glow.shader = null
                    val tb = tex[i]
                    if (tb != null) canvas.drawBitmap(tb, null, RectF(cx - nr, cy - nr, cx + nr, cy + nr), bmpPaint)
                    val rnd = Random(i * 31L + 5L)
                    for (k in 0 until 24) {
                        val a = rnd.nextFloat() * 6.283f
                        val d = r * (3.2f - 2.4f * rb) * (0.5f + rnd.nextFloat())
                        dot.color = Color.argb(190, 160, 255, 220)
                        canvas.drawCircle(cx + cos(a) * d, cy + sin(a) * d * yScale, dp(1.2f), dot)
                    }
                }
                return
            }

            val sb = shadeBmp
            val gb = glowBmp
            val tb = tex[i]
            val sx = px(0f); val sy = py(0f)
            val dx = sx - cx; val dy = sy - cy
            val dist = max(1f, sqrt(dx * dx + dy * dy))
            val ang = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()

            if (gb != null && settings.glow > 0.05f && r > 2f) {
                glowPaint.alpha = (settings.glow * 150f).toInt().coerceIn(0, 255)
                glowPaint.colorFilter = tints[i]
                val gr = r * 2.5f
                canvas.drawBitmap(gb, null, RectF(cx - gr, cy - gr, cx + gr, cy + gr), glowPaint)
            }
            if (b.ring) ringArcs(canvas, cx, cy, r, false)
            if (tb != null) canvas.drawBitmap(tb, null, RectF(cx - r, cy - r, cx + r, cy + r), bmpPaint)
            if (sb != null) {
                canvas.save()
                canvas.translate(cx, cy)
                canvas.rotate(ang)
                canvas.drawBitmap(sb, null, RectF(-r, -r, r, r), bmpPaint)
                canvas.restore()
            }
            if (b.ring) ringArcs(canvas, cx, cy, r, true)

            if (b.pop > 1e3 && b.stage >= 4 && r > 5f) {
                val n = min(40, max(4, ((log10(b.pop) - 3.0) * 6.0).toInt()))
                val rnd = Random(i * 7919L)
                val alpha = if (b.ruined) 55 else 215
                for (k in 0 until n) {
                    val a = rnd.nextFloat() * 6.283f
                    val rr = sqrt(rnd.nextFloat()) * r * 0.9f
                    val ox = cos(a) * rr; val oy = sin(a) * rr
                    if (ox * dx + oy * dy < 0f) {
                        dot.color = Color.argb(alpha, 255, 196, 110)
                        canvas.drawCircle(cx + ox, cy + oy, max(0.6f, r * 0.03f), dot)
                    }
                }
            }
            if (b.ruined && r > 4f) {
                val rnd = Random(i * 104729L)
                for (k in 0 until 6) {
                    val a = rnd.nextFloat() * 6.283f
                    val rr = rnd.nextFloat() * r * 0.8f
                    dot.color = Color.argb(150, 220, 80, 50)
                    canvas.drawCircle(cx + cos(a) * rr, cy + sin(a) * rr, max(0.6f, r * 0.05f), dot)
                }
            }
            if (b.scars > 0.02f) {
                dot.color = Color.argb((90 * b.scars).toInt(), 60, 20, 12)
                canvas.drawCircle(cx + r * 0.2f, cy + r * 0.15f, r * 0.72f, dot)
            }
            if (b.ringBuilt) {
                stroke.strokeWidth = max(0.8f, dp(1f))
                stroke.color = Color.argb(170, 130, 235, 255)
                val rr = r * 1.5f
                canvas.drawOval(RectF(cx - rr, cy - rr * 0.34f, cx + rr, cy + rr * 0.34f), stroke)
            }
            if (b.elevator) {
                stroke.strokeWidth = max(0.6f, dp(0.7f))
                stroke.color = Color.argb(150, 150, 245, 255)
                canvas.drawLine(cx, cy - r, cx, cy - r * 2.3f, stroke)
                dot.color = Color.argb(200, 180, 250, 255)
                canvas.drawCircle(cx, cy - r * 2.3f, max(1f, dp(1.6f)), dot)
            }
            if (b.colonyOf >= 0) {
                dot.color = Color.argb(200, 201, 162, 39)
                canvas.drawCircle(cx + r * 1.3f, cy - r * 0.85f, max(1f, dp(1.8f)), dot)
            }
            if (b.flash > 0.01f) {
                dot.color = Color.argb((160 * b.flash).toInt(),
                    Color.red(b.flashColor), Color.green(b.flashColor), Color.blue(b.flashColor))
                canvas.drawCircle(cx, cy, r * (1f + b.flash * 0.5f), dot)
            }
            if (settings.labels && r > 3f) {
                small.color = Color.argb(150, 175, 210, 240)
                canvas.drawText(b.name, cx + r + dp(5f), cy - dp(3f), small)
            }
        }

        private fun wrap(text: String, paint: Paint, maxW: Float): List<String> {
            val out = ArrayList<String>()
            var line = StringBuilder()
            for (w in text.split(" ")) {
                val probe = if (line.isEmpty()) w else line.toString() + " " + w
                if (paint.measureText(probe) > maxW && line.isNotEmpty()) {
                    out.add(line.toString())
                    line = StringBuilder(w)
                } else {
                    if (line.isNotEmpty()) line.append(" ")
                    line.append(w)
                }
            }
            if (line.isNotEmpty()) out.add(line.toString())
            return out
        }

        private fun drawCard(canvas: Canvas) {
            val idx = sim.cardBody
            if (idx < 0 || idx >= sim.bodies.size) return
            val b = sim.bodies[idx]
            if (b.destroyed) return
            val fade = min(1f, min(sim.cardAge / 0.7f, (sim.cardDur - sim.cardAge) / 0.9f)).coerceIn(0f, 1f)
            if (fade <= 0.01f) return
            val a = (fade * 255f).toInt().coerceIn(0, 255)

            val cw = min(dp(214f), sw - dp(28f))
            val pad = dp(11f)
            val lines = wrap(sim.cardText, body, cw - pad * 2f)
            val lh = dp(15f)
            val ch = pad * 2f + dp(13f) + lines.size * lh + dp(12f)
            val bx = px(b.x); val by = py(b.y)
            val r = rad(b)
            val right = bx < sw * 0.52f
            var x = if (right) bx + r + dp(26f) else bx - r - dp(26f) - cw
            x = x.coerceIn(dp(12f), max(dp(12f), sw - cw - dp(12f)))
            var y = by - ch / 2f
            y = y.coerceIn(dp(58f), max(dp(58f), sh - ch - dp(76f)))

            val anchorX = if (right) x else x + cw
            val anchorY = y + ch / 2f
            stroke.strokeWidth = dp(0.8f)
            stroke.color = Color.argb((a * 0.55f).toInt(), 201, 162, 39)
            canvas.drawLine(bx + (if (right) r * 0.75f else -r * 0.75f), by, anchorX, anchorY, stroke)
            dot.color = Color.argb((a * 0.8f).toInt(), 201, 162, 39)
            canvas.drawCircle(anchorX, anchorY, dp(1.8f), dot)

            val rect = RectF(x, y, x + cw, y + ch)
            cardFill.color = Color.argb((a * 0.82f).toInt(), 7, 10, 17)
            canvas.drawRoundRect(rect, dp(5f), dp(5f), cardFill)
            cardEdge.color = Color.argb(a, 201, 162, 39)
            canvas.drawRoundRect(rect, dp(5f), dp(5f), cardEdge)
            stroke.color = Color.argb((a * 0.35f).toInt(), 228, 199, 124)
            stroke.strokeWidth = dp(0.6f)
            canvas.drawLine(x + pad, y + pad + dp(11f), x + cw - pad, y + pad + dp(11f), stroke)

            title.color = Color.argb(a, 228, 199, 124)
            canvas.drawText(sim.cardTitle, x + pad, y + pad + dp(7f), title)
            body.color = Color.argb(a, 236, 240, 246)
            var ty = y + pad + dp(13f) + dp(11f)
            for (l in lines) {
                canvas.drawText(l, x + pad, ty, body)
                ty += lh
            }
            tag.color = Color.argb((a * 0.75f).toInt(), 150, 175, 200)
            canvas.drawText(sim.cardTag, x + pad, y + ch - pad + dp(2f), tag)
            if (b.pop > 1e3) {
                tag.color = Color.argb((a * 0.75f).toInt(), 201, 162, 39)
                val ps = sim.popText(b.pop)
                tag.textAlign = Paint.Align.RIGHT
                canvas.drawText(ps, x + cw - pad, y + ch - pad + dp(2f), tag)
                tag.textAlign = Paint.Align.LEFT
            }
        }

        private fun drawHud(canvas: Canvas) {
            cal.timeInMillis = System.currentTimeMillis()
            small.color = Color.argb(150, 150, 190, 225)
            val left = if (settings.date) String.format(Locale.US, "%02d.%02d  %02d:%02d",
                cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)) + "   " else ""
            var line = left + "K " + String.format(Locale.US, "%.2f", sim.kIndex)
            if (sim.colonies > 0) line += "  ·  КОЛОНИЙ " + sim.colonies
            if (sim.swarm > 0.01f) line += "  ·  РОЙ " + (sim.swarm * 100).toInt() + "%"
            if (charging && settings.chargeReaction) line += "  ·  ЭНЕРГИЯ+"
            canvas.drawText(line, dp(16f), dp(48f), small)
        }

        private fun drawEvent(canvas: Canvas, e: SimEvent, t: Double) {
            val p = (e.t / e.dur).coerceIn(0f, 1f)
            val fade = min(1f, min(p / 0.12f, (1f - p) / 0.15f))
            val a = (200 * fade).toInt().coerceIn(0, 255)
            when (e.type) {
                0, 1 -> {
                    val cx = px(e.x); val cy = py(e.y)
                    val sx = px(0f); val sy = py(0f)
                    val dx = cx - sx; val dy = cy - sy
                    val d = max(1f, sqrt(dx * dx + dy * dy))
                    val near = (300f / max(60f, d / max(0.001f, s))).coerceIn(0.3f, 1.6f)
                    val ionL = dp(150f) * near
                    stroke.strokeWidth = dp(2.2f)
                    stroke.color = Color.argb(a, 130, 215, 255)
                    canvas.drawLine(cx, cy, cx + dx / d * ionL, cy + dy / d * ionL, stroke)
                    stroke.strokeWidth = dp(4f)
                    stroke.color = Color.argb((a * 0.5f).toInt(), 255, 225, 170)
                    val lag = e.rot + e.nu - 0.35f
                    canvas.drawLine(cx, cy, cx + dx / d * ionL * 0.6f - cos(lag) * dp(40f),
                        cy + dy / d * ionL * 0.6f - sin(lag) * dp(40f) * yScale, stroke)
                    glow.shader = RadialGradient(cx, cy, max(1f, dp(16f)),
                        intArrayOf(Color.argb(a, 235, 250, 255), Color.argb(0, 120, 210, 255)),
                        floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
                    canvas.drawCircle(cx, cy, dp(16f), glow)
                    glow.shader = null
                    dot.color = Color.WHITE
                    canvas.drawCircle(cx, cy, dp(2.2f), dot)
                }
                2 -> {
                    val A = sim.bodies[e.a]; val B = sim.bodies[e.b]
                    val ax = px(A.x); val ay = py(A.y)
                    val bx = px(B.x); val by = py(B.y)
                    val mx = (ax + bx) / 2f; val my = (ay + by) / 2f
                    val sx = px(0f); val sy = py(0f)
                    val dx = mx - sx; val dy = my - sy
                    val d = max(1f, sqrt(dx * dx + dy * dy))
                    val qx = mx + dx / d * d * 0.3f
                    val qy = my + dy / d * d * 0.3f
                    path.rewind()
                    path.moveTo(ax, ay)
                    path.quadTo(qx, qy, bx, by)
                    stroke.strokeWidth = dp(1.2f)
                    stroke.color = Color.argb((a * 0.7f).toInt(), 201, 162, 39)
                    stroke.pathEffect = DashPathEffect(floatArrayOf(dp(6f), dp(8f)), -e.t * dp(22f))
                    canvas.drawPath(path, stroke)
                    stroke.pathEffect = null
                    val u = p; val iu = 1f - u
                    val rx = iu * iu * ax + 2 * iu * u * qx + u * u * bx
                    val ry = iu * iu * ay + 2 * iu * u * qy + u * u * by
                    val tx = 2 * iu * (qx - ax) + 2 * u * (bx - qx)
                    val ty = 2 * iu * (qy - ay) + 2 * u * (by - qy)
                    canvas.save()
                    canvas.translate(rx, ry)
                    canvas.rotate(Math.toDegrees(atan2(ty.toDouble(), tx.toDouble())).toFloat())
                    path.rewind()
                    path.moveTo(dp(6f), 0f)
                    path.lineTo(-dp(4f), -dp(2.6f))
                    path.lineTo(-dp(4f), dp(2.6f))
                    path.close()
                    dot.color = Color.argb(255, 235, 245, 255)
                    canvas.drawPath(path, dot)
                    dot.color = Color.argb((180 * fade).toInt(), 255, 170, 90)
                    canvas.drawCircle(-dp(5f), 0f, dp(2f), dot)
                    canvas.restore()
                }
                3 -> {
                    val b = sim.bodies[e.a]
                    val bx = px(b.x); val by = py(b.y)
                    val r = max(2f, rad(b))
                    val hx = bx; val hy = by - r * 2.4f
                    val startX = bx + sw * 0.6f; val startY = by - sh * 0.4f
                    val ux: Float; val uy: Float
                    if (p < 0.3f) {
                        val k = smooth(p / 0.3f)
                        ux = startX + (hx - startX) * k; uy = startY + (hy - startY) * k
                    } else if (p < 0.75f) {
                        ux = hx; uy = hy + sin(t * 2.0).toFloat() * dp(3f)
                    } else {
                        val k = smooth((p - 0.75f) / 0.25f)
                        ux = hx + (startX - hx) * k; uy = hy + (startY - hy) * k
                    }
                    if (p > 0.33f && p < 0.72f) {
                        path.rewind()
                        path.moveTo(ux - r * 0.35f, uy)
                        path.lineTo(ux + r * 0.35f, uy)
                        path.lineTo(bx + r * 0.8f, by)
                        path.lineTo(bx - r * 0.8f, by)
                        path.close()
                        disc.shader = LinearGradient(ux, uy, bx, by,
                            intArrayOf(Color.argb((110 * fade).toInt(), 150, 255, 210), Color.argb(0, 120, 255, 200)),
                            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
                        canvas.drawPath(path, disc)
                        disc.shader = null
                    }
                    dot.color = Color.argb(255, 190, 205, 220)
                    canvas.drawOval(RectF(ux - r * 0.9f, uy - r * 0.22f, ux + r * 0.9f, uy + r * 0.22f), dot)
                    dot.color = Color.argb(230, 150, 230, 255)
                    canvas.drawOval(RectF(ux - r * 0.4f, uy - r * 0.5f, ux + r * 0.4f, uy + r * 0.1f), dot)
                    for (k in 0 until 5) {
                        val lx = ux - r * 0.7f + r * 1.4f * k / 4f
                        val on = ((t * 4.0 + k).toInt() % 5) == 0
                        dot.color = Color.argb(if (on) 255 else 90, 255, 210, 120)
                        canvas.drawCircle(lx, uy + r * 0.12f, max(0.8f, dp(1.2f)), dot)
                    }
                }
                15 -> {
                    val b = sim.bodies[e.a]
                    val bx = px(b.x); val by = py(b.y)
                    val sx = px(0f); val sy = py(0f)
                    stroke.strokeWidth = dp(1.6f)
                    stroke.color = Color.argb((a * 0.6f).toInt(), 130, 255, 210)
                    stroke.pathEffect = DashPathEffect(floatArrayOf(dp(4f), dp(10f)), -e.t * dp(30f))
                    canvas.drawLine(sx, sy, bx, by, stroke)
                    stroke.pathEffect = null
                    val k = (p * 1.2f).coerceIn(0f, 1f)
                    dot.color = Color.argb(255, 180, 255, 230)
                    canvas.drawCircle(sx + (bx - sx) * k, sy + (by - sy) * k, dp(3f), dot)
                }
                4 -> {
                    val b = sim.bodies[e.a]
                    val cx = px(b.x) + dp(120f); val cy = py(b.y) - dp(150f)
                    val rr = dp(20f) + smooth(p) * max(sw, sh) * 0.7f
                    glow.shader = RadialGradient(cx, cy, max(1f, rr),
                        intArrayOf(Color.argb((220 * (1f - p) * fade).toInt(), 255, 250, 235),
                            Color.argb((110 * (1f - p) * fade).toInt(), 130, 200, 255),
                            Color.argb(0, 40, 90, 200)),
                        floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
                    canvas.drawCircle(cx, cy, rr, glow)
                    glow.shader = null
                }
                5 -> {
                    val b = sim.bodies[e.a]
                    val bx = px(b.x); val by = py(b.y)
                    if (p < 0.55f) {
                        val k = 1f - p / 0.55f
                        stroke.strokeWidth = dp(2.4f)
                        stroke.color = Color.argb(a, 255, 190, 120)
                        canvas.drawLine(bx + dp(420f) * k, by - dp(300f) * k, bx, by, stroke)
                    } else {
                        val k = smooth(((p - 0.55f) / 0.45f).coerceIn(0f, 1f))
                        stroke.strokeWidth = dp(2f)
                        stroke.color = Color.argb((220 * (1f - k) * fade).toInt(), 255, 150, 80)
                        canvas.drawCircle(bx, by, dp(10f) + k * dp(90f), stroke)
                    }
                }
                6 -> {
                    val cx = px(0f); val cy = py(0f)
                    val fp = smooth(p)
                    stroke.strokeWidth = dp(2f)
                    stroke.color = Color.argb((200 * fade).toInt(), 255, 195, 100)
                    for (k in 0 until 3) {
                        val ang = e.rot + k * 2.1f
                        val r0 = 34f * s
                        val r1 = r0 + dp(130f) * fp
                        path.rewind()
                        path.moveTo(cx + cos(ang) * r0, cy + sin(ang) * r0 * yScale)
                        path.quadTo(cx + cos(ang + 0.5f) * r1 * 0.8f, cy + sin(ang + 0.5f) * r1 * 0.8f * yScale,
                            cx + cos(ang) * r1, cy + sin(ang) * r1 * yScale)
                        canvas.drawPath(path, stroke)
                    }
                }
                7 -> {
                    val rnd = Random(e.seed)
                    for (k in 0 until 14) {
                        val sp = (p * 1.7f - k * 0.06f).coerceIn(0f, 1f)
                        if (sp <= 0f || sp >= 1f) continue
                        val x = rnd.nextFloat() * sw; val y = rnd.nextFloat() * sh * 0.7f
                        val len = dp(80f) * (1f - sp)
                        stroke.strokeWidth = dp(1.3f)
                        stroke.color = Color.argb((170 * (1f - sp)).toInt(), 205, 230, 255)
                        canvas.drawLine(x + len, y + len * 0.4f, x, y, stroke)
                    }
                }
                8 -> {
                    val b = sim.bodies[e.a]
                    val bx = px(b.x); val by = py(b.y)
                    val rr = dp(26f)
                    glow.shader = RadialGradient(bx, by, rr * 4f,
                        intArrayOf(Color.argb(0, 0, 0, 0), Color.argb((150 * fade).toInt(), 255, 170, 80), Color.argb(0, 0, 0, 0)),
                        floatArrayOf(0.34f, 0.5f, 1f), Shader.TileMode.CLAMP)
                    canvas.drawCircle(bx, by, rr * 4f, glow)
                    glow.shader = null
                    dot.color = Color.BLACK
                    canvas.drawCircle(bx, by, rr, dot)
                    stroke.strokeWidth = dp(1.6f)
                    stroke.color = Color.argb((210 * fade).toInt(), 255, 200, 120)
                    canvas.drawCircle(bx, by, rr * 1.5f, stroke)
                }
                9 -> {
                    val cx = px(0f); val cy = py(0f)
                    val len = max(sw, sh) * 1.2f
                    canvas.save()
                    canvas.translate(cx, cy)
                    canvas.rotate(e.rot * 57.3f + smooth(p) * 25f)
                    glow.shader = LinearGradient(0f, 0f, len, 0f,
                        intArrayOf(Color.argb(0, 120, 220, 255), Color.argb((200 * fade).toInt(), 170, 245, 255), Color.argb(0, 120, 220, 255)),
                        floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
                    val rw = dp(9f) * (1f + smooth(p))
                    canvas.drawRect(-len, -rw, len, rw, glow)
                    glow.shader = null
                    canvas.restore()
                }
                10 -> {
                    stroke.strokeWidth = dp(1.4f)
                    stroke.color = Color.argb((150 * fade).toInt(), 201, 162, 39)
                    path.rewind()
                    var first = true
                    for (b in sim.bodies) {
                        if (b.parent >= 0) continue
                        if (first) { path.moveTo(px(b.x), py(b.y)); first = false }
                        else path.lineTo(px(b.x), py(b.y))
                    }
                    canvas.drawPath(path, stroke)
                }
                11 -> {
                    val b = sim.bodies[2]
                    val bx = px(b.x); val by = py(b.y)
                    val r = max(3f, rad(b))
                    for (k in 0 until 3) {
                        val rr = r * (1.5f + k * 0.4f)
                        stroke.strokeWidth = dp(1.5f)
                        stroke.color = Color.argb((150 * fade * (1f - k * 0.3f)).toInt(), 90, 255, 175)
                        canvas.drawArc(RectF(bx - rr, by - rr, bx + rr, by + rr), 200f + k * 18f, 140f, false, stroke)
                    }
                }
                12 -> {
                    val cx = sw * 0.5f; val cy = sh * 0.32f
                    val r = dp(30f) + smooth(p) * dp(55f)
                    for (k in 0 until 6) {
                        val rr = r * (1f - k * 0.13f)
                        stroke.strokeWidth = dp(1.3f)
                        stroke.color = Color.argb((160 * fade * (1f - k * 0.14f)).toInt(), if (k % 2 == 0) 130 else 205, 165, 255)
                        canvas.save()
                        canvas.translate(cx, cy)
                        canvas.rotate((t * 18.0 + k * 15.0).toFloat())
                        canvas.drawOval(-rr, -rr * 0.6f, rr, rr * 0.6f, stroke)
                        canvas.restore()
                    }
                }
                13 -> {
                    val rnd = Random(e.seed)
                    for (k in 0 until 3) {
                        val nx = rnd.nextFloat() * sw; val ny = rnd.nextFloat() * sh
                        val rr = max(sw, sh) * (0.25f + 0.1f * k)
                        val c = when (k) { 0 -> Color.rgb(60, 140, 255); 1 -> Color.rgb(160, 80, 255); else -> Color.rgb(60, 220, 200) }
                        glow.shader = RadialGradient(nx, ny, rr,
                            intArrayOf(Color.argb((40 * fade).toInt(), Color.red(c), Color.green(c), Color.blue(c)), Color.argb(0, 0, 0, 0)),
                            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
                        canvas.drawCircle(nx, ny, rr, glow)
                        glow.shader = null
                    }
                }
                14 -> {
                    val bx = sw * (p * 1.4f - 0.2f); val by = sh * 0.2f
                    val rr = dp(10f)
                    glow.shader = RadialGradient(bx, by, rr * 6f,
                        intArrayOf(Color.argb((200 * fade).toInt(), 255, 215, 145), Color.argb(0, 255, 140, 60)),
                        floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
                    canvas.drawCircle(bx, by, rr * 6f, glow)
                    glow.shader = null
                    dot.color = Color.rgb(255, 238, 205)
                    canvas.drawCircle(bx, by, rr, dot)
                }
                16 -> {
                    val b = sim.bodies[10]
                    val bx = px(b.x); val by = py(b.y)
                    val r = max(3f, rad(b))
                    for (k in 0 until 3) {
                        val rr = r * (2.1f + k * 0.4f) + smooth(p) * dp(8f)
                        stroke.strokeWidth = dp(1.1f)
                        stroke.color = Color.argb((140 * fade * (1f - k * 0.25f)).toInt(), 228, 199, 124)
                        canvas.save()
                        canvas.translate(bx, by)
                        canvas.rotate(-16f + k * 7f * smooth(p))
                        canvas.drawOval(-rr, -rr * 0.4f, rr, rr * 0.4f, stroke)
                        canvas.restore()
                    }
                }
            }
        }
    }
}
