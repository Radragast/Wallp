package com.example.gearwallpaper

import android.graphics.*
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.service.wallpaper.WallpaperService
import android.view.Choreographer
import android.view.SurfaceHolder
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

// ==================== ШЕСТЕРЁНКА ====================
class Gear(
    val teeth: Int,
    moduleDp: Float,
    toothDepthDp: Float,
    val color: Int,
    private val spokeCount: Int = 5,
) {
    val pitchRadiusDp = moduleDp * teeth / 2f
    val radiusDp = pitchRadiusDp + toothDepthDp / 2f
    private val rootRadiusDp = radiusDp - toothDepthDp
    private val rimInnerDp = rootRadiusDp * 0.74f
    private val hubRadiusDp = radiusDp * 0.26f
    private val holeRadiusDp = radiusDp * 0.13f

    var cx = 0f
    var cy = 0f
    var speedFactor = 1f
    var phaseDeg = 0f

    private val ux = FloatArray(teeth * 4)
    private val uy = FloatArray(teeth * 4)
    private val ur = FloatArray(teeth * 4)

    private val bodyPath = Path()
    private val hubPath = Path()
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.4f
    }

    init {
        val step = (2.0 * PI / teeth).toFloat()
        val rootK = rootRadiusDp / radiusDp
        var k = 0
        for (i in 0 until teeth) {
            val a = i * step
            val ang = floatArrayOf(a - 0.20f * step, a + 0.20f * step, a + 0.30f * step, a + 0.70f * step)
            val rad = floatArrayOf(1f, 1f, rootK, rootK)
            for (j in 0..3) {
                ux[k] = cos(ang[j]); uy[k] = sin(ang[j]); ur[k] = rad[j]; k++
            }
        }
        bodyPaint.color = color
        spokePaint.color = shade(color, 1.18f)
        edgePaint.color = shade(color, 0.55f)
        bodyPath.fillType = Path.FillType.EVEN_ODD
        hubPath.fillType = Path.FillType.EVEN_ODD
    }

    fun drawGear(
        canvas: Canvas, cx: Float, cy: Float,
        radius: Float = radiusDp, teeth: Int = this.teeth,
        angle: Float, color: Int = this.color,
    ) {
        val s = radius / radiusDp
        bodyPath.rewind()
        for (k in 0 until teeth * 4) {
            val r = radius * ur[k]
            val x = cx + ux[k] * r
            val y = cy + uy[k] * r
            if (k == 0) bodyPath.moveTo(x, y) else bodyPath.lineTo(x, y)
        }
        bodyPath.close()
        bodyPath.addCircle(cx, cy, rimInnerDp * s, Path.Direction.CW)

        canvas.save()
        canvas.rotate(angle, cx, cy)
        bodyPaint.color = color
        canvas.drawPath(bodyPath, bodyPaint)
        canvas.drawPath(bodyPath, edgePaint)

        val half = radius * 0.055f
        val top = cy - rimInnerDp * s - 2f
        val bottom = cy - hubRadiusDp * s + 2f
        for (i in 0 until spokeCount) {
            canvas.save()
            canvas.rotate(i * 360f / spokeCount, cx, cy)
            canvas.drawRoundRect(cx - half, top, cx + half, bottom, half, half, spokePaint)
            canvas.restore()
        }

        hubPath.rewind()
        hubPath.addCircle(cx, cy, hubRadiusDp * s, Path.Direction.CW)
        hubPath.addCircle(cx, cy, holeRadiusDp * s, Path.Direction.CW)
        canvas.drawPath(hubPath, bodyPaint)
        canvas.drawPath(hubPath, edgePaint)
        canvas.restore()
    }

    private fun shade(c: Int, f: Float): Int = Color.rgb(
        (Color.red(c) * f).toInt().coerceIn(0, 255),
        (Color.green(c) * f).toInt().coerceIn(0, 255),
        (Color.blue(c) * f).toInt().coerceIn(0, 255),
    )
}

// ==================== МАЯТНИК ====================
class Pendulum(
    private val pivotX: Float,
    private val pivotY: Float,
    private val lengthDp: Float,
    private val bobRadiusDp: Float,
    private val maxAngleDeg: Float = 20f,
    periodSec: Double = 2.0,
) {
    private val omega = 2.0 * PI / periodSec
    private val rodPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(152, 158, 166); strokeWidth = 5f; strokeCap = Paint.Cap.ROUND
    }
    private val mountPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(56, 58, 62) }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(190, 196, 204) }
    private val bobPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bobEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.rgb(64, 48, 22)
    }

    init {
        val by = pivotY + lengthDp
        bobPaint.shader = RadialGradient(
            pivotX - bobRadiusDp * 0.35f, by - bobRadiusDp * 0.40f, bobRadiusDp * 1.7f,
            intArrayOf(Color.rgb(242, 216, 142), Color.rgb(178, 142, 80), Color.rgb(86, 64, 30)),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
        )
    }

    fun draw(canvas: Canvas, t: Double) {
        val angle = (sin(t * omega) * maxAngleDeg).toFloat()
        val bobY = pivotY + lengthDp
        canvas.drawRoundRect(pivotX - 16f, pivotY - 10f, pivotX + 16f, pivotY + 8f, 4f, 4f, mountPaint)
        canvas.save()
        canvas.rotate(angle, pivotX, pivotY)
        canvas.drawLine(pivotX, pivotY, pivotX, bobY, rodPaint)
        canvas.drawCircle(pivotX, bobY, bobRadiusDp, bobPaint)
        canvas.drawCircle(pivotX, bobY, bobRadiusDp, bobEdgePaint)
        canvas.drawRect(pivotX - 5f, bobY + bobRadiusDp + 2f, pivotX + 5f, bobY + bobRadiusDp + 9f, rodPaint)
        canvas.restore()
        canvas.drawCircle(pivotX, pivotY, 6f, mountPaint)
        canvas.drawCircle(pivotX, pivotY, 2.5f, axisPaint)
    }
}

// ==================== СТРЕЛКИ ====================
class ClockHands(
    private val dialRadiusDp: Float = 84f,
    private val hourLenDp: Float = 46f,
    private val minuteLenDp: Float = 68f,
    private val secondLenDp: Float = 78f,
    var useRealTime: Boolean = false,
) {
    private val dialPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(96, 0, 0, 0) }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(170, 200, 210, 222); strokeWidth = 2f; strokeCap = Paint.Cap.ROUND
    }
    private val hourPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(232, 228, 216); strokeWidth = 7f; strokeCap = Paint.Cap.ROUND
    }
    private val minutePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(232, 228, 216); strokeWidth = 4.5f; strokeCap = Paint.Cap.ROUND
    }
    private val secondPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(217, 83, 79); strokeWidth = 2f; strokeCap = Paint.Cap.ROUND
    }
    private val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(30, 30, 32) }
    private val capDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(217, 83, 79) }
    private val calendar = Calendar.getInstance()

    fun draw(canvas: Canvas, cx: Float, cy: Float, t: Double) {
        canvas.drawCircle(cx, cy, dialRadiusDp, dialPaint)
        for (i in 0 until 12) {
            canvas.save()
            canvas.rotate(i * 30f, cx, cy)
            val outer = dialRadiusDp - 3f
            val inner = outer - if (i % 3 == 0) 10f else 6f
            canvas.drawLine(cx, cy - outer, cx, cy - inner, tickPaint)
            canvas.restore()
        }
        val secDeg: Float; val minDeg: Float; val hourDeg: Float
        if (useRealTime) {
            calendar.timeInMillis = System.currentTimeMillis()
            val s = calendar.get(Calendar.SECOND) + calendar.get(Calendar.MILLISECOND) / 1000f
            val m = calendar.get(Calendar.MINUTE) + s / 60f
            val h = calendar.get(Calendar.HOUR_OF_DAY) % 12 + m / 60f
            secDeg = s * 6f; minDeg = m * 6f; hourDeg = h * 30f
        } else {
            secDeg = ((t * 6.0) % 360.0).toFloat()
            minDeg = ((t * 0.1) % 360.0).toFloat()
            hourDeg = ((t / 120.0) % 360.0).toFloat()
        }
        drawHand(canvas, cx, cy, hourDeg, hourLenDp, 12f, hourPaint)
        drawHand(canvas, cx, cy, minDeg, minuteLenDp, 14f, minutePaint)
        canvas.save()
        canvas.rotate(secDeg, cx, cy)
        canvas.drawLine(cx, cy + 18f, cx, cy - secondLenDp, secondPaint)
        canvas.drawCircle(cx, cy + 18f, 4.5f, secondPaint)
        canvas.restore()
        canvas.drawCircle(cx, cy, 7f, capPaint)
        canvas.drawCircle(cx, cy, 3f, capDotPaint)
    }

    private fun drawHand(canvas: Canvas, cx: Float, cy: Float, deg: Float, len: Float, tail: Float, p: Paint) {
        canvas.save()
        canvas.rotate(deg, cx, cy)
        canvas.drawLine(cx, cy + tail, cx, cy - len, p)
        canvas.restore()
    }
}

// ==================== СЕРВИС ОБОЕВ ====================
class GearWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = MechanismEngine()

    inner class MechanismEngine : Engine(), Choreographer.FrameCallback {

        private val sceneW = 440f
        private val sceneH = 880f
        private val moduleDp = 11.5f
        private val toothDepthDp = 10f
        private val baseSpeedDegPerSec = 6.0

        @Volatile private var visible = false
        @Volatile private var surfaceValid = false
        @Volatile private var surfaceW = 0
        @Volatile private var surfaceH = 0
        @Volatile private var layoutDirty = true

        private val startTimeNanos = System.nanoTime()

        private val threadLock = Any()
        private var renderThread: HandlerThread? = null
        private var renderHandler: Handler? = null
        private var choreographer: Choreographer? = null
        private var frameScheduled = false
        private var lastFrameNanos = 0L
        private val minFrameIntervalNanos = 15_000_000L

        private var gears = emptyList<Gear>()
        private var drive: Gear? = null
        private var pendulum: Pendulum? = null
        private var hands: ClockHands? = null
        private var sceneScale = 1f
        private var bgPaint = Paint()
        private var glarePaint = Paint()
        private var vignettePaint = Paint()

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            if (isVisible) startRendering() else stopRendering()
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            surfaceValid = true
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            surfaceW = width; surfaceH = height; layoutDirty = true
            if (visible) startRendering()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            surfaceValid = false
            stopRendering()
        }

        override fun onDestroy() {
            super.onDestroy()
            visible = false
            stopRendering()
        }

        private fun startRendering() {
            synchronized(threadLock) {
                val existing = renderHandler
                if (existing != null) {
                    existing.post { scheduleFrame() }
                } else {
                    val thread = HandlerThread("GearWallpaperRender", Process.THREAD_PRIORITY_DISPLAY)
                    thread.start()
                    val handler = Handler(thread.looper)
                    renderThread = thread
                    renderHandler = handler
                    handler.post {
                        choreographer = Choreographer.getInstance()
                        scheduleFrame()
                    }
                }
            }
        }

        private fun stopRendering() {
            synchronized(threadLock) {
                val handler = renderHandler ?: return
                val thread = renderThread
                renderHandler = null
                renderThread = null
                handler.post {
                    choreographer?.removeFrameCallback(this)
                    choreographer = null
                    frameScheduled = false
                    thread?.quitSafely()
                }
            }
        }

        private fun scheduleFrame() {
            if (frameScheduled || !visible || !surfaceValid) return
            frameScheduled = true
            choreographer?.postFrameCallback(this)
        }

        override fun doFrame(frameTimeNanos: Long) {
            frameScheduled = false
            if (!visible || !surfaceValid) return
            if (surfaceW > 0 && surfaceH > 0 &&
                frameTimeNanos - lastFrameNanos >= minFrameIntervalNanos
            ) {
                lastFrameNanos = frameTimeNanos
                if (layoutDirty) rebuildScene()
                val elapsed = (System.nanoTime() - startTimeNanos) / 1e9
                drawFrame(elapsed)
            }
            scheduleFrame()
        }

        private fun drawFrame(t: Double) {
            val holder = surfaceHolder
            val canvas = lockCanvas(holder) ?: return
            try {
                val w = surfaceW.toFloat()
                val h = surfaceH.toFloat()
                canvas.drawRect(0f, 0f, w, h, bgPaint)
                canvas.save()
                canvas.translate(w / 2f, h / 2f)
                canvas.scale(sceneScale, sceneScale)
                canvas.translate(-sceneW / 2f, -sceneH / 2f)
                val baseDeg = t * baseSpeedDegPerSec
                for (g in gears) {
                    val angle = ((baseDeg * g.speedFactor + g.phaseDeg) % 360.0).toFloat()
                    g.drawGear(canvas, g.cx, g.cy, g.radiusDp, g.teeth, angle, g.color)
                }
                pendulum?.draw(canvas, t)
                drive?.let { hands?.draw(canvas, it.cx, it.cy, t) }
                canvas.restore()
                canvas.drawRect(0f, 0f, w, h, glarePaint)
                canvas.drawRect(0f, 0f, w, h, vignettePaint)
            } finally {
                try { holder.unlockCanvasAndPost(canvas) } catch (_: IllegalArgumentException) {}
            }
        }

        private fun lockCanvas(holder: SurfaceHolder): Canvas? = try {
            holder.lockHardwareCanvas()
        } catch (e: Exception) {
            try { holder.lockCanvas() } catch (e2: Exception) { null }
        }

        private fun rebuildScene() {
            val w = surfaceW
            val h = surfaceH
            if (w <= 0 || h <= 0) return
            layoutDirty = false
            val density = this@GearWallpaperService.resources.displayMetrics.density
            sceneScale = min(w / (sceneW * density), h / (sceneH * density)) * density

            val brass = Color.rgb(179, 146, 79)
            val steel = Color.rgb(154, 163, 173)
            val copper = Color.rgb(176, 106, 74)

            val g1 = Gear(20, moduleDp, toothDepthDp, brass, spokeCount = 6)
            val g2 = Gear(12, moduleDp, toothDepthDp, steel)
            val g3 = Gear(10, moduleDp, toothDepthDp, copper)
            val g4 = Gear(8, moduleDp, toothDepthDp, steel)
            val g5 = Gear(7, moduleDp, toothDepthDp, brass)
            val g6 = Gear(7, moduleDp, toothDepthDp, copper)

            g1.cx = 220f; g1.cy = 350f
            mesh(g1, g2, -50f)
            mesh(g1, g3, 210f)
            mesh(g1, g4, 95f)
            mesh(g2, g5, -90f)
            mesh(g4, g6, 160f)

            gears = listOf(g1, g2, g3, g4, g5, g6)
            drive = g1
            pendulum = Pendulum(220f, 610f, lengthDp = 185f, bobRadiusDp = 26f,
                maxAngleDeg = 20f, periodSec = 2.0)
            hands = ClockHands()

            bgPaint = Paint().apply {
                shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(),
                    Color.parseColor("#1a1a1a"), Color.parseColor("#2e2e2e"), Shader.TileMode.CLAMP)
            }
            glarePaint = Paint().apply {
                shader = RadialGradient(w * 0.32f, h * 0.22f, maxOf(w, h) * 0.85f,
                    Color.argb(26, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP)
            }
            vignettePaint = Paint().apply {
                shader = RadialGradient(w / 2f, h / 2f, hypot(w / 2.0, h / 2.0).toFloat() * 1.02f,
                    intArrayOf(0x00000000, 0x00000000, 0xB0000000.toInt()),
                    floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            }
        }

        private fun mesh(parent: Gear, child: Gear, phiDeg: Float) {
            val phi = Math.toRadians(phiDeg.toDouble())
            val d = parent.pitchRadiusDp + child.pitchRadiusDp
            child.cx = parent.cx + (d * cos(phi)).toFloat()
            child.cy = parent.cy + (d * sin(phi)).toFloat()
            val ratio = parent.teeth.toFloat() / child.teeth
            child.speedFactor = -parent.speedFactor * ratio
            val c = phiDeg * (1f + ratio) + 180f - 180f / child.teeth
            child.phaseDeg = c - ratio * parent.phaseDeg
        }
    }
}
