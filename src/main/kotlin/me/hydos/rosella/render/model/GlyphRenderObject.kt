package me.hydos.rosella.render.model

import me.hydos.rosella.render.font.CachedFont
import me.hydos.rosella.render.resource.Identifier
import org.joml.Vector2f
import org.joml.Vector3f

class GlyphRenderObject(
	materialIdentifier: Identifier,
	z: Float = -1f,
	colour: Vector3f = Vector3f(0f, 0f, 0f),
	val charOffsetX: Float,
	val cachedFont: CachedFont
) : GuiRenderObject(materialIdentifier, z, colour) {

	override fun loadModelInfo() {
		vertices = ArrayList()
		indices = ArrayList()

		val fontStringScale = 1 / cachedFont.allCharsLength
		val scaledCharOffsetMin = charOffsetX * fontStringScale
		val scaledCharOffsetMax = fontStringScale - scaledCharOffsetMin

		vertices.add(
			Vertex(
				Vector3f(-0.5f, -0.5f, 0f),
				colour,
				Vector2f(0f + scaledCharOffsetMin, 0f + scaledCharOffsetMin)
			)
		)
		vertices.add(
			Vertex(
				Vector3f(0.5f, -0.5f, 0f),
				colour,
				Vector2f(1f - scaledCharOffsetMax, 0f + scaledCharOffsetMin)
			)
		)
		vertices.add(
			Vertex(
				Vector3f(0.5f, 0.5f, 0f),
				colour,
				Vector2f(1f - scaledCharOffsetMax, 1f - scaledCharOffsetMax)
			)
		)
		vertices.add(
			Vertex(
				Vector3f(-0.5f, 0.5f, 0f),
				colour,
				Vector2f(0f + scaledCharOffsetMin, 1f - scaledCharOffsetMax)
			)
		)

		indices.add(0)
		indices.add(1)
		indices.add(2)
		indices.add(2)
		indices.add(3)
		indices.add(0)
	}
}