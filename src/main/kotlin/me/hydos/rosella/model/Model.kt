package me.hydos.rosella.model

import me.hydos.rosella.Rosella
import me.hydos.rosella.device.Device
import me.hydos.rosella.material.Material
import me.hydos.rosella.memory.MemMan
import me.hydos.rosella.memory.memcpy
import me.hydos.rosella.util.createBuffer
import org.joml.Vector3f
import org.joml.Vector3fc
import org.lwjgl.assimp.Assimp
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.util.vma.Vma.vmaFreeMemory
import org.lwjgl.vulkan.VK10.*
import java.io.File
import java.lang.ClassLoader.getSystemClassLoader


class Model(private val modelLocation: String) {

	private var vertices: ArrayList<Vertex> = ArrayList()
	var indices: ArrayList<Int> = ArrayList()

	var vertexBuffer: Long = 0

	var indexBuffer: Long = 0
	var indexBufferMemory: Long = 0

	var material: Material = Material("shaders/base.v.glsl", "shaders/base.f.glsl", "textures/fact_core_0.png")

	private fun createIndexBuffer(device: Device, engine: Rosella) {
		stackPush().use { stack ->
			val bufferSize: Int = (Integer.BYTES * indices.size)
			val pBuffer = stack.mallocLong(1)
			val pBufferMemory = stack.mallocLong(1)
			createBuffer(
				bufferSize,
				VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
				VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
				pBuffer,
				pBufferMemory,
				device
			)
			val stagingBuffer = pBuffer[0]
			val stagingBufferMemory = pBufferMemory[0]
			val data = stack.mallocPointer(1)
			vkMapMemory(device.device, stagingBufferMemory, 0, bufferSize.toLong(), 0, data)
			run { memcpy(data.getByteBuffer(0, bufferSize), indices) }
			vkUnmapMemory(device.device, stagingBufferMemory)
			createBuffer(
				bufferSize,
				VK_BUFFER_USAGE_TRANSFER_DST_BIT or VK_BUFFER_USAGE_INDEX_BUFFER_BIT,
				VK_MEMORY_HEAP_DEVICE_LOCAL_BIT,
				pBuffer,
				pBufferMemory,
				device
			)
			indexBuffer = pBuffer[0]
			indexBufferMemory = pBufferMemory[0]
			engine.memMan.copyBuffer(stagingBuffer, indexBuffer, bufferSize, engine, device)
			vkDestroyBuffer(device.device, stagingBuffer, null)
			vkFreeMemory(device.device, stagingBufferMemory, null)
		}
	}

	fun destroy(memMan: MemMan, device: Device) {
		vmaFreeMemory(memMan.allocator, vertexBuffer)

		vkDestroyBuffer(device.device, indexBuffer, null)
		vkFreeMemory(device.device, indexBufferMemory, null)
	}

	fun create(device: Device, engine: Rosella): Model {
		loadModelFile()
		vertexBuffer = engine.memMan.createVertexBuffer(engine, vertices)
		createIndexBuffer(device, engine)
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