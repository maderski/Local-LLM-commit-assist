package com.maderskitech.localllmcommitassist

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.BasicStroke
import java.awt.Color
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.GeneralPath
import java.awt.geom.Line2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage

fun createAppIcon(): BitmapPainter {
    val size = 512
    val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics()

    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

    val slate900 = Color(0x0F, 0x17, 0x2A)
    val slate800 = Color(0x1E, 0x29, 0x3B)
    val slate700 = Color(0x33, 0x41, 0x55)
    val blue400 = Color(0x60, 0xA5, 0xFA)
    val blue500 = Color(0x3B, 0x82, 0xF6)
    val green400 = Color(0x4A, 0xDE, 0x80)
    val green500 = Color(0x22, 0xC5, 0x5E)
    val dark = Color(0x0F, 0x17, 0x2A)

    // Background rounded square
    val bgShape = RoundRectangle2D.Float(16f, 16f, 480f, 480f, 96f, 96f)
    g.paint = GradientPaint(16f, 16f, slate800, 496f, 496f, slate900)
    g.fill(bgShape)
    g.color = slate700
    g.stroke = BasicStroke(4f)
    g.draw(bgShape)

    // Git branch main line
    g.paint = GradientPaint(180f, 140f, blue400, 180f, 380f, blue500)
    g.stroke = BasicStroke(20f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    g.draw(Line2D.Float(180f, 160f, 180f, 360f))

    // Git branch fork curve
    g.stroke = BasicStroke(16f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    val fork = GeneralPath()
    fork.moveTo(180f, 240f)
    fork.quadTo(220f, 220f, 260f, 210f)
    fork.quadTo(280f, 205f, 300f, 200f)
    g.draw(fork)

    // Git commit dots
    fun fillCircle(g: Graphics2D, cx: Float, cy: Float, r: Float, c1: Color, c2: Color) {
        g.paint = GradientPaint(cx - r, cy - r, c1, cx + r, cy + r, c2)
        g.fill(Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2))
    }

    fillCircle(g, 180f, 160f, 24f, blue400, blue500)
    fillCircle(g, 180f, 260f, 24f, blue400, blue500)
    fillCircle(g, 180f, 360f, 24f, green400, green500)
    fillCircle(g, 300f, 200f, 24f, blue400, blue500)

    // Chat bubble (LLM)
    g.paint = GradientPaint(290f, 260f, green400, 390f, 340f, green500)
    val bubble = RoundRectangle2D.Float(290f, 268f, 112f, 72f, 20f, 20f)
    g.fill(bubble)
    // Bubble tail
    val tail = GeneralPath()
    tail.moveTo(310f, 340f)
    tail.lineTo(320f, 358f)
    tail.lineTo(335f, 340f)
    tail.closePath()
    g.fill(tail)

    // Dots inside chat bubble
    g.color = dark
    g.fill(Ellipse2D.Float(318f, 296f, 16f, 16f))
    g.fill(Ellipse2D.Float(338f, 296f, 16f, 16f))
    g.fill(Ellipse2D.Float(358f, 296f, 16f, 16f))

    // Checkmark on green commit dot
    g.color = dark
    g.stroke = BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    val check = GeneralPath()
    check.moveTo(168f, 360f)
    check.lineTo(178f, 372f)
    check.lineTo(196f, 348f)
    g.draw(check)

    g.dispose()

    return BitmapPainter(img.toComposeImageBitmap())
}
