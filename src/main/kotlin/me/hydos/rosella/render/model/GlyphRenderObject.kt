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
	val charWidth: Float,
	val cachedFont: CachedFont
) : GuiRenderObject(materialIdentifier, z, colour) {

	override fun loadModelInfo() {
		vertices = ArrayList()
		indices = ArrayList()
	}

	fun clone(): GlyphRenderObject {
		return GlyphRenderObject(materialIdentifier, z, colour, charOffsetX, charWidth, cachedFont)
	}
}