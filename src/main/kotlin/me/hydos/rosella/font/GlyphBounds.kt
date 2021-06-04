package me.hydos.rosella.font

import java.awt.geom.Rectangle2D

/**
 * Used to store simple data about glyphs in the font
 */
data class GlyphBounds(
	val xTexCoord: Float,
	val yTexCoord: Float,
	val xMaxTexCoord: Float,
	val yMaxTexCoord: Float
)

fun fromAwt(
	bounds: Rectangle2D,
	xOffset: Float,
	yOffset: Float
): GlyphBounds {

	return GlyphBounds(
		xOffset,
		yOffset,
		xOffset + bounds.width.toFloat(),
		yOffset + bounds.height.toFloat()
	)
}