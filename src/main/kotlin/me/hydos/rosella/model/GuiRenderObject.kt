package me.hydos.rosella.model

import me.hydos.rosella.resource.Global
import me.hydos.rosella.resource.Identifier
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector3fc

class GuiRenderObject(materialIdentifier: Identifier) :
	RenderObject(Global.ensureResource(Identifier("null", "null")), materialIdentifier) {

	override fun loadModelFile() {
		vertices = ArrayList()
		indices = ArrayList()
		val color: Vector3fc = Vector3f(1.0f, 1.0f, 1.0f)

		vertices.add(Vertex(Vector3f(-0.5f, 0f, -0.5f), color, Vector2f(1f, 1f)))
		vertices.add(Vertex(Vector3f(0.5f, 0f, -0.5f), color, Vector2f(0f, 1f)))
		vertices.add(Vertex(Vector3f(0.5f, 0f, 0.5f), color, Vector2f(0f, 0f)))
		vertices.add(Vertex(Vector3f(-0.5f, 0f, 0.5f), color, Vector2f(1f, 0f)))

		indices.add(0)
		indices.add(1)
		indices.add(2)
		indices.add(2)
		indices.add(3)
		indices.add(0)
	}
}