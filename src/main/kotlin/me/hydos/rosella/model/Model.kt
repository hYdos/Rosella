package me.hydos.rosella.model

import me.hydos.rosella.Rosella
import me.hydos.rosella.material.Material
import me.hydos.rosella.memory.MemMan
import org.joml.Vector3f
import org.joml.Vector3fc
import org.lwjgl.assimp.Assimp
import org.lwjgl.util.vma.Vma.vmaFreeMemory
import java.io.File
import java.lang.ClassLoader.getSystemClassLoader


class Model(private val modelLocation: String, textureLoc: String) {

	private var vertices: ArrayList<Vertex> = ArrayList()
	var indices: ArrayList<Int> = ArrayList()

	var vertexBuffer: Long = 0

	var indexBuffer: Long = 0

	var material: Material = Material("shaders/base.v.glsl", "shaders/base.f.glsl", textureLoc)

	fun free(memMan: MemMan) {
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
		val modelFile = File(getSystemClassLoader().getResource(modelLocation).file)
		val model: ModelLoader.SimpleModel =
			ModelLoader.loadModel(modelFile, Assimp.aiProcess_FlipUVs or Assimp.aiProcess_DropNormals)
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