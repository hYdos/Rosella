package me.hydos.rosella.render.model

import me.hydos.rosella.render.resource.Identifier
import org.joml.Vector2f
import org.joml.Vector3f

class ColourGuiRenderObject(material: Identifier, private val colour: Vector3f): GuiRenderObject(material) {

	override fun loadModelInfo() {
		vertices = ArrayList()
		indices = ArrayList()

		vertices.add(Vertex(Vector3f(-0.5f, -0.5f, 0f), colour, Vector2f(0f, 0f)))
		vertices.add(Vertex(Vector3f(0.5f, -0.5f, 0f), colour, Vector2f(0f, 0f)))
		vertices.add(Vertex(Vector3f(0.5f, 0.5f, 0f), colour, Vector2f(0f, 0f)))
		vertices.add(Vertex(Vector3f(-0.5f, 0.5f, 0f), colour, Vector2f(0f, 0f)))

		indices.add(0)
		indices.add(1)
		indices.add(2)
		indices.add(2)
		indices.add(3)
		indices.add(0)
	}
}