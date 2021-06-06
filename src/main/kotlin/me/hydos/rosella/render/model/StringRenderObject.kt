package me.hydos.rosella.render.model

import me.hydos.rosella.render.font.CachedFont
import me.hydos.rosella.render.font.GlyphInfo
import me.hydos.rosella.render.resource.Identifier
import org.joml.Vector2f
import org.joml.Vector3f

class StringRenderObject(
	matId: Identifier,
	private val font: CachedFont,
	private val string: String,
	z: Float,
	colour: Vector3f,
	scaleX: Float,
	scaleZ: Float,
	translateX: Float,
	translateZ: Float
) : GuiRenderObject(matId, z, colour, scaleX, scaleZ, translateX, translateZ) {

	private val magicScaleVal: Float = 0.08190016f / 3

	init {
		scale(0.5f, 1f)
	}

	override fun loadModelInfo() {
		var xOffset = 0f
		var yOffset = 0f
		for (i in string.indices) {
			val c: Char = string[i]
			if (c == '\n') {
				yOffset -= 0.8f
				xOffset = 0f
				continue;
			}
			val charInfo = font.charMap[c]!!
			addCharModel(charInfo, xOffset, yOffset)
			xOffset += charInfo.charWidth * magicScaleVal
		}
	}

	private fun addCharModel(glyphInfo: GlyphInfo, xOffset: Float, yOffset: Float) {
		val prevIndSize = vertices.size
		val fontStringScale = 1 / font.allCharsLength
		val scaledCharOffsetMin = glyphInfo.charOffsetX * fontStringScale
		val scaledCharWidth = glyphInfo.charWidth * fontStringScale
		val scaledCharOffsetMax = scaledCharOffsetMin + scaledCharWidth

		vertices.add(
			Vertex(
				Vector3f(xOffset + -0.5f, yOffset + -0.5f, 0f),
				colour,
				Vector2f(scaledCharOffsetMin, 0f)
			)
		)
		vertices.add(
			Vertex(
				Vector3f(xOffset + 0.5f, yOffset + -0.5f, 0f),
				colour,
				Vector2f(scaledCharOffsetMax, 0f)
			)
		)
		vertices.add(
			Vertex(
				Vector3f(xOffset + 0.5f, yOffset + 0.5f, 0f),
				colour,
				Vector2f(scaledCharOffsetMax, 1f)
			)
		)
		vertices.add(
			Vertex(
				Vector3f(xOffset + -0.5f, yOffset + 0.5f, 0f),
				colour,
				Vector2f(scaledCharOffsetMin, 1f)
			)
		)

		indices.add(prevIndSize + 0)
		indices.add(prevIndSize + 1)
		indices.add(prevIndSize + 2)
		indices.add(prevIndSize + 2)
		indices.add(prevIndSize + 3)
		indices.add(prevIndSize + 0)
	}
}