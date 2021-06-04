package me.hydos.rosella.font

import java.awt.Color
import java.awt.Font
import java.awt.image.BufferedImage
import kotlin.math.ceil


class CachedFont(font: Font) {

	private var allChars: String = ""
	private val charMap = HashMap<Char, GlyphBounds>()
	private val fontImage: BufferedImage

	init {
		// Load All Glyphs in font into string for parsing
		var c = 0x0000.toChar()
		var xOffset = 0f
		var yOffset = 0f

		val image = BufferedImage(1, 1, BufferedImage.TYPE_4BYTE_ABGR)
		val g = image.graphics
		g.font = font
		val context = g.fontMetrics.fontRenderContext
		while (c.toInt() <= 64258) {
			if (font.canDisplay(c)) {
				allChars += c
				val rect = font.getStringBounds(c.toString(), context)
				charMap[c] = fromAwt(rect, xOffset, yOffset)
				xOffset += rect.width.toFloat()
				yOffset += rect.height.toFloat()
			}
			c++
		}
		g.dispose()

		fontImage = toImage(font)
	}

	private fun toImage(font: Font): BufferedImage {
		var image = BufferedImage(1, 1, BufferedImage.TYPE_4BYTE_ABGR)
		var g = image.graphics
		g.font = font
		val context = g.fontMetrics.fontRenderContext
		val rect = font.getStringBounds(allChars, context)
		g.dispose()

		image = BufferedImage(
			ceil(rect.width).toInt(),
			ceil(rect.height).toInt(), BufferedImage.TYPE_4BYTE_ABGR
		)
		g = image.graphics
		g.color = Color.black

		g.font = font

		val fm = g.fontMetrics

		g.drawString(allChars, 0, fm.ascent)
		g.dispose()
		return image
	}
}