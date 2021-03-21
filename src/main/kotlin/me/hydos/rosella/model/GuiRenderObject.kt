package me.hydos.rosella.model

import me.hydos.rosella.Rosella
import me.hydos.rosella.resource.Identifier
import me.hydos.rosella.resource.Resource
import me.hydos.rosella.shader.ubo.BasicUbo
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector3fc

class GuiRenderObject(materialIdentifier: Identifier, private val z: Float = -1f) :
	RenderObject(Resource.Empty, materialIdentifier) {

	override fun loadModelInfo() {
		vertices = ArrayList()
		indices = ArrayList()
		val color: Vector3fc = Vector3f(1.0f, 1.0f, 1.0f)

		vertices.add(Vertex(Vector3f(-0.5f, -0.5f, 0f), color, Vector2f(0f, 0f)))
		vertices.add(Vertex(Vector3f(0.5f, -0.5f, 0f), color, Vector2f(1f, 0f)))
		vertices.add(Vertex(Vector3f(0.5f, 0.5f, 0f), color, Vector2f(1f, 1f)))
		vertices.add(Vertex(Vector3f(-0.5f, 0.5f, 0f), color, Vector2f(0f, 1f)))

		indices.add(0)
		indices.add(1)
		indices.add(2)
		indices.add(2)
		indices.add(3)
		indices.add(0)
	}

	override fun load(engine: Rosella) {
		val retrievedMaterial = engine.materials[materialIdentifier]
			?: error("The material $materialIdentifier couldn't be found. (Are you registering the material?)")
		material = retrievedMaterial
		ubo = BasicUbo(engine.device, engine.memory)
		ubo.create(engine.renderer.swapChain)
		modelTransformMatrix.translate(0f, 0f, z)
	}

	fun scale(x: Float, y: Float, z:Float) {
		modelTransformMatrix.scale(x, y, z)
	}
}