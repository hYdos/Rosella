package me.hydos.rosella.model

import me.hydos.rosella.Rosella
import me.hydos.rosella.material.Material
import me.hydos.rosella.memory.MemMan
import me.hydos.rosella.resource.Global
import me.hydos.rosella.resource.Identifier
import me.hydos.rosella.resource.Resource
import org.joml.Vector3f
import org.joml.Vector3fc
import org.lwjgl.assimp.Assimp
import org.lwjgl.util.vma.Vma.vmaFreeMemory

class Model(private val model: Resource) {

	private var vertices: ArrayList<Vertex> = ArrayList()
	var indices: ArrayList<Int> = ArrayList()

	var vertexBuffer: Long = 0

	var indexBuffer: Long = 0

	var material: Material = Material(
		Global.ensureResource(Identifier("rosella", "shaders/base.v.glsl")),
		Global.ensureResource(Identifier("rosella", "shaders/base.f.glsl")),
		Global.ensureResource(Identifier("rosella", "textures/fact_core_0.png"))
	)

	fun destroy(memMan: MemMan) {
		vmaFreeMemory(memMan.allocator, vertexBuffer)
		vmaFreeMemory(memMan.allocator, indexBuffer)
	}

	fun create(engine: Rosella): Model {
		loadModelFile()
		vertexBuffer = engine.memMan.createVertexBuffer(engine, vertices)
		indexBuffer = engine.memMan.createIndexBuffer(engine, indices)
		return this
	}

	private fun loadModelFile() {
		val model: ModelLoader.SimpleModel =
			ModelLoader.loadModel(model, Assimp.aiProcess_FlipUVs or Assimp.aiProcess_DropNormals)
		val vertexCount: Int = model.positions.size

		vertices = ArrayList()

		val color: Vector3fc = Vector3f(1.0f, 1.0f, 1.0f)
		for (i in 0 until vertexCount) {
			vertices.add(
				Vertex(
					model.positions[i],
					color,
					model.texCoords[i]
				)
			)
		}

		indices = ArrayList(model.indices.size)

		for (i in 0 until model.indices.size) {
			indices.add(model.indices[i])
		}
	}
}
