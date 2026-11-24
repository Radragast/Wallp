package com.example.gearwallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import java.util.Random
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

object OrreryTextures {

    fun glow(px: Int): Bitmap {
        val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val h = px / 2f
        p.shader = RadialGradient(h, h, h,
            intArrayOf(Color.argb(210, 255, 255, 255), Color.argb(60, 255, 255, 255), Color.argb(0, 255, 255, 255)),
            floatArrayOf(0f, 0.38f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(h, h, h, p)
        return b
    }

    fun shade(px: Int): Bitmap {
        val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val pix = IntArray(px * px)
        var lx = 1f; var ly = -0.12f; var lz = 0.42f
        val ln = sqrt(lx * lx + ly * ly + lz * lz)
        lx /= ln; ly /= ln; lz /= ln
        for (y in 0 until px) {
            for (x in 0 until px) {
                val nx = (x + 0.5f) / px * 2f - 1f
                val ny = (y + 0.5f) / px * 2f - 1f
                val r2 = nx * nx + ny * ny
                if (r2 > 1f) { pix[y * px + x] = 0; continue }
                val z = sqrt(1f - r2)
                val lam = max(0f, nx * lx + ny * ly + z * lz)
                var a = (1f - lam) * 0.80f + (1f - z) * 0.14f
                if (r2 > 0.985f) a *= (1f - r2) / 0.015f
                pix[y * px + x] = Color.argb((a.coerceIn(0f, 0.93f) * 255f).toInt(), 2, 4, 9)
            }
        }
        b.setPixels(pix, 0, px, 0, 0, px, px)
        return b
    }

    fun body(name: String, c1: Int, c2: Int, px: Int, seed: Long): Bitmap {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val clip = Path()
        val f = px.toFloat()
        val h = f / 2f
        clip.addCircle(h, h, h - 0.5f, Path.Direction.CW)
        c.clipPath(clip)
        val rnd = Random(seed)

        fun fill(col: Int) { p.style = Paint.Style.FILL; p.color = col; c.drawRect(0f, 0f, f, f, p) }

        fun band(y0: Float, y1: Float, col: Int, wave: Float) {
            val path = Path()
            var x = 0f
            path.moveTo(0f, y0 * f + sin(y0 * 11f) * wave * f)
            while (x <= f) {
                path.lineTo(x, y0 * f + sin(x / f * 12.56f + y0 * 11f) * wave * f)
                x += f / 28f
            }
            x = f
            path.lineTo(f, y1 * f + sin(f / f * 12.56f + y1 * 11f) * wave * f)
            while (x >= 0f) {
                path.lineTo(x, y1 * f + sin(x / f * 12.56f + y1 * 11f) * wave * f)
                x -= f / 28f
            }
            path.close()
            p.style = Paint.Style.FILL
            p.color = col
            c.drawPath(path, p)
        }

        fun crater(cx: Float, cy: Float, r: Float, dark: Int, light: Int) {
            p.style = Paint.Style.FILL
            p.color = dark
            c.drawCircle(cx * f, cy * f, r * f, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = max(0.8f, r * f * 0.22f)
            p.color = light
            c.drawCircle(cx * f, cy * f, r * f * 0.9f, p)
            p.style = Paint.Style.FILL
        }

        fun blob(cx: Float, cy: Float, r: Float, col: Int, n: Int, flat: Float) {
            val path = Path()
            for (k in 0 until n) {
                val a = k * 6.2832f / n
                val rr = r * (0.55f + rnd.nextFloat() * 0.75f)
                val x = (cx + cos(a) * rr) * f
                val y = (cy + sin(a) * rr * flat) * f
                if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            p.style = Paint.Style.FILL
            p.color = col
            c.drawPath(path, p)
        }

        fun oval(cx: Float, cy: Float, rx: Float, ry: Float, col: Int, rot: Float) {
            c.save()
            c.translate(cx * f, cy * f)
            c.rotate(rot)
            p.style = Paint.Style.FILL
            p.color = col
            c.drawOval(RectF(-rx * f, -ry * f, rx * f, ry * f), p)
            c.restore()
        }

        fun lines(n: Int, col: Int, w: Float, y0: Float, y1: Float) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = w * f
            p.color = col
            for (k in 0 until n) {
                val ax = rnd.nextFloat(); val ay = y0 + rnd.nextFloat() * (y1 - y0)
                val bx = rnd.nextFloat(); val by = y0 + rnd.nextFloat() * (y1 - y0)
                val path = Path()
                path.moveTo(ax * f, ay * f)
                path.quadTo((ax + bx) / 2f * f + (rnd.nextFloat() - 0.5f) * f * 0.2f,
                    (ay + by) / 2f * f, bx * f, by * f)
                c.drawPath(path, p)
            }
            p.style = Paint.Style.FILL
        }

        when (name) {
            "Юпитер" -> {
                fill(Color.rgb(214, 190, 156))
                band(0.00f, 0.09f, Color.rgb(140, 116, 88), 0.004f)
                band(0.09f, 0.17f, Color.rgb(201, 174, 134), 0.007f)
                band(0.17f, 0.25f, Color.rgb(239, 228, 203), 0.006f)
                band(0.25f, 0.34f, Color.rgb(185, 143, 98), 0.008f)
                band(0.34f, 0.41f, Color.rgb(232, 216, 184), 0.005f)
                band(0.41f, 0.48f, Color.rgb(156, 111, 69), 0.007f)
                band(0.48f, 0.56f, Color.rgb(240, 230, 207), 0.006f)
                band(0.56f, 0.64f, Color.rgb(193, 154, 107), 0.008f)
                band(0.64f, 0.72f, Color.rgb(230, 213, 180), 0.005f)
                band(0.72f, 0.81f, Color.rgb(166, 124, 80), 0.007f)
                band(0.81f, 0.91f, Color.rgb(216, 196, 160), 0.006f)
                band(0.91f, 1.00f, Color.rgb(138, 112, 83), 0.004f)
                for (k in 0 until 16) {
                    val y = 0.12f + rnd.nextFloat() * 0.76f
                    oval(rnd.nextFloat(), y, 0.05f + rnd.nextFloat() * 0.07f, 0.012f,
                        Color.argb(70, 255, 248, 232), 0f)
                }
                oval(0.66f, 0.615f, 0.115f, 0.055f, Color.rgb(176, 85, 60), -6f)
                oval(0.66f, 0.615f, 0.075f, 0.032f, Color.rgb(201, 112, 84), -6f)
                oval(0.30f, 0.44f, 0.05f, 0.022f, Color.argb(150, 250, 244, 228), 0f)
            }
            "Сатурн" -> {
                fill(Color.rgb(232, 217, 174))
                band(0.00f, 0.11f, Color.rgb(196, 178, 138), 0.003f)
                band(0.11f, 0.23f, Color.rgb(236, 224, 190), 0.004f)
                band(0.23f, 0.35f, Color.rgb(219, 201, 162), 0.003f)
                band(0.35f, 0.47f, Color.rgb(243, 233, 203), 0.004f)
                band(0.47f, 0.59f, Color.rgb(214, 195, 154), 0.003f)
                band(0.59f, 0.71f, Color.rgb(238, 226, 194), 0.004f)
                band(0.71f, 0.85f, Color.rgb(205, 185, 146), 0.003f)
                band(0.85f, 1.00f, Color.rgb(180, 162, 124), 0.003f)
                for (k in 0 until 10) {
                    oval(rnd.nextFloat(), 0.15f + rnd.nextFloat() * 0.7f,
                        0.07f + rnd.nextFloat() * 0.08f, 0.010f,
                        Color.argb(45, 255, 250, 235), 0f)
                }
                oval(0.5f, 0.045f, 0.30f, 0.045f, Color.argb(70, 150, 132, 96), 0f)
            }
            "Земля" -> {
                p.shader = LinearGradient(0f, 0f, 0f, f,
                    intArrayOf(Color.rgb(18, 62, 110), Color.rgb(32, 104, 168), Color.rgb(16, 58, 104)),
                    floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, f, f, p)
                p.shader = null
                blob(0.30f, 0.36f, 0.17f, Color.rgb(62, 104, 58), 11, 0.85f)
                blob(0.36f, 0.60f, 0.12f, Color.rgb(78, 116, 62), 10, 1.1f)
                blob(0.68f, 0.42f, 0.19f, Color.rgb(96, 112, 66), 12, 0.8f)
                blob(0.72f, 0.66f, 0.10f, Color.rgb(122, 108, 70), 9, 1.0f)
                blob(0.52f, 0.80f, 0.09f, Color.rgb(70, 100, 56), 9, 0.8f)
                for (k in 0 until 14) {
                    oval(rnd.nextFloat(), 0.12f + rnd.nextFloat() * 0.76f,
                        0.06f + rnd.nextFloat() * 0.09f, 0.018f + rnd.nextFloat() * 0.016f,
                        Color.argb(85, 255, 255, 255), (rnd.nextFloat() - 0.5f) * 30f)
                }
                oval(0.5f, 0.025f, 0.34f, 0.05f, Color.argb(220, 244, 250, 255), 0f)
                oval(0.5f, 0.975f, 0.36f, 0.055f, Color.argb(230, 246, 252, 255), 0f)
            }
            "Марс" -> {
                fill(Color.rgb(178, 96, 58))
                blob(0.34f, 0.42f, 0.20f, Color.rgb(140, 69, 38), 12, 0.8f)
                blob(0.66f, 0.58f, 0.17f, Color.rgb(150, 76, 42), 11, 0.9f)
                blob(0.55f, 0.30f, 0.12f, Color.rgb(201, 138, 94), 10, 0.7f)
                for (k in 0 until 12) {
                    crater(0.12f + rnd.nextFloat() * 0.76f, 0.16f + rnd.nextFloat() * 0.68f,
                        0.012f + rnd.nextFloat() * 0.026f,
                        Color.argb(90, 96, 46, 26), Color.argb(70, 214, 152, 108))
                }
                oval(0.5f, 0.03f, 0.24f, 0.04f, Color.argb(225, 248, 250, 252), 0f)
                oval(0.5f, 0.97f, 0.20f, 0.035f, Color.argb(210, 244, 248, 250), 0f)
            }
            "Венера" -> {
                fill(Color.rgb(227, 207, 159))
                for (k in 0 until 18) {
                    oval(rnd.nextFloat(), rnd.nextFloat(),
                        0.14f + rnd.nextFloat() * 0.16f, 0.022f + rnd.nextFloat() * 0.02f,
                        Color.argb(48, 203, 178, 127), (rnd.nextFloat() - 0.5f) * 24f)
                }
                for (k in 0 until 10) {
                    oval(rnd.nextFloat(), rnd.nextFloat(), 0.12f, 0.018f,
                        Color.argb(40, 250, 240, 214), (rnd.nextFloat() - 0.5f) * 20f)
                }
            }
            "Меркурий" -> {
                fill(Color.rgb(154, 149, 142))
                blob(0.4f, 0.4f, 0.24f, Color.rgb(132, 127, 120), 12, 0.9f)
                for (k in 0 until 34) {
                    crater(0.08f + rnd.nextFloat() * 0.84f, 0.08f + rnd.nextFloat() * 0.84f,
                        0.012f + rnd.nextFloat() * 0.042f,
                        Color.argb(110, 96, 92, 86), Color.argb(90, 196, 190, 182))
                }
            }
            "Луна" -> {
                fill(Color.rgb(196, 194, 189))
                blob(0.36f, 0.34f, 0.20f, Color.rgb(110, 108, 104), 12, 0.85f)
                blob(0.60f, 0.46f, 0.15f, Color.rgb(118, 116, 112), 11, 0.9f)
                blob(0.44f, 0.62f, 0.11f, Color.rgb(124, 122, 118), 10, 0.9f)
                for (k in 0 until 26) {
                    crater(0.08f + rnd.nextFloat() * 0.84f, 0.08f + rnd.nextFloat() * 0.84f,
                        0.010f + rnd.nextFloat() * 0.036f,
                        Color.argb(90, 128, 126, 122), Color.argb(110, 232, 230, 226))
                }
            }
            "Ио" -> {
                fill(Color.rgb(230, 208, 94))
                blob(0.35f, 0.40f, 0.18f, Color.rgb(226, 176, 62), 11, 0.9f)
                blob(0.66f, 0.60f, 0.15f, Color.rgb(238, 226, 150), 10, 0.9f)
                for (k in 0 until 16) {
                    val x = 0.12f + rnd.nextFloat() * 0.76f
                    val y = 0.14f + rnd.nextFloat() * 0.72f
                    oval(x, y, 0.022f + rnd.nextFloat() * 0.030f, 0.018f + rnd.nextFloat() * 0.022f,
                        Color.argb(190, 190, 74, 40), 0f)
                    oval(x, y, 0.010f, 0.008f, Color.argb(220, 60, 26, 16), 0f)
                }
                for (k in 0 until 8) {
                    oval(rnd.nextFloat(), rnd.nextFloat(), 0.05f, 0.03f,
                        Color.argb(110, 252, 250, 236), 0f)
                }
            }
            "Европа" -> {
                fill(Color.rgb(223, 233, 240))
                blob(0.4f, 0.5f, 0.26f, Color.rgb(212, 224, 233), 12, 0.9f)
                lines(22, Color.argb(150, 168, 118, 90), 0.005f, 0.05f, 0.95f)
                lines(8, Color.argb(90, 200, 160, 130), 0.010f, 0.1f, 0.9f)
                for (k in 0 until 6) {
                    crater(0.15f + rnd.nextFloat() * 0.7f, 0.15f + rnd.nextFloat() * 0.7f,
                        0.014f, Color.argb(70, 170, 150, 135), Color.argb(90, 250, 252, 255))
                }
            }
            "Ганимед" -> {
                fill(Color.rgb(141, 132, 120))
                blob(0.34f, 0.38f, 0.22f, Color.rgb(112, 104, 94), 12, 0.9f)
                blob(0.64f, 0.58f, 0.24f, Color.rgb(182, 175, 164), 13, 0.85f)
                lines(14, Color.argb(70, 206, 200, 190), 0.006f, 0.2f, 0.85f)
                for (k in 0 until 18) {
                    crater(0.1f + rnd.nextFloat() * 0.8f, 0.1f + rnd.nextFloat() * 0.8f,
                        0.010f + rnd.nextFloat() * 0.026f,
                        Color.argb(80, 92, 86, 78), Color.argb(110, 220, 214, 204))
                }
            }
            "Каллисто" -> {
                fill(Color.rgb(109, 100, 92))
                blob(0.5f, 0.5f, 0.3f, Color.rgb(96, 88, 80), 12, 0.9f)
                for (k in 0 until 44) {
                    crater(0.06f + rnd.nextFloat() * 0.88f, 0.06f + rnd.nextFloat() * 0.88f,
                        0.008f + rnd.nextFloat() * 0.030f,
                        Color.argb(100, 62, 56, 50), Color.argb(130, 206, 198, 186))
                }
            }
            "Титан" -> {
                fill(Color.rgb(213, 154, 72))
                band(0.00f, 0.16f, Color.rgb(196, 140, 66), 0.004f)
                band(0.34f, 0.66f, Color.rgb(168, 112, 44), 0.006f)
                band(0.84f, 1.00f, Color.rgb(198, 144, 70), 0.004f)
                for (k in 0 until 12) {
                    oval(rnd.nextFloat(), rnd.nextFloat(), 0.13f, 0.020f,
                        Color.argb(46, 246, 208, 150), 0f)
                }
                oval(0.5f, 0.5f, 0.44f, 0.44f, Color.argb(38, 250, 214, 158), 0f)
            }
            "Энцелад" -> {
                fill(Color.rgb(238, 246, 251))
                blob(0.4f, 0.35f, 0.22f, Color.rgb(226, 238, 246), 11, 0.9f)
                for (k in 0 until 4) {
                    val y = 0.70f + k * 0.055f
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = f * 0.014f
                    p.color = Color.argb(160, 147, 185, 204)
                    val path = Path()
                    path.moveTo(0.22f * f, y * f)
                    path.quadTo(0.5f * f, (y + 0.03f) * f, 0.78f * f, y * f)
                    c.drawPath(path, p)
                    p.style = Paint.Style.FILL
                }
                for (k in 0 until 10) {
                    crater(0.15f + rnd.nextFloat() * 0.7f, 0.12f + rnd.nextFloat() * 0.5f,
                        0.012f, Color.argb(50, 190, 208, 220), Color.argb(80, 255, 255, 255))
                }
            }
            else -> {
                p.shader = LinearGradient(0f, 0f, f, f,
                    intArrayOf(c1, c2), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, f, f, p)
                p.shader = null
            }
        }

        for (k in 0 until 260) {
            val x = rnd.nextFloat() * f
            val y = rnd.nextFloat() * f
            p.color = if (rnd.nextBoolean()) Color.argb(14, 255, 255, 255) else Color.argb(16, 0, 0, 0)
            c.drawCircle(x, y, f * 0.006f, p)
        }
        return bmp
    }
}
