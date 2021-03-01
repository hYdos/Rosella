package me.hydos.rosella.model

import me.hydos.rosella.Rosella
import me.hydos.rosella.device.Device
import me.hydos.rosella.material.Material
import me.hydos.rosella.memory.MemMan
import me.hydos.rosella.util.createBuffer
import me.hydos.rosella.util.createVmaBuffer
import me.hydos.rosella.util.memcpy
import me.hydos.rosella.util.ok
import org.joml.Vector3f
import org.joml.Vector3fc
import org.lwjgl.assimp.Assimp
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.util.vma.Vma.VMA_MEMORY_USAGE_CPU_TO_GPU
import org.lwjgl.util.vma.Vma.vmaFreeMemory
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkBufferCopy
import org.lwjgl.vulkan.VkSubmitInfo
import java.io.File
import java.lang.ClassLoader.getSystemClassLoader


class Model(private val modelLocation: String) {

	private var vertices: ArrayList<Vertex> = ArrayList()
	var indices: ArrayList<Int> = ArrayList()

	var vertexBuffer: Long = 0

	var indexBuffer: Long = 0
	var indexBufferMemory: Long = 0

	var material: Material = Material("shaders/base.v.glsl", "shaders/base.f.glsl", "textures/fact_core_0.png")

	private fun createVertexBuffer(rosella: Rosella) {
		stackPush().use {
			val bufferSize: Int = Vertex.SIZEOF * vertices.size
			val pBuffer = it.mallocLong(1)
			createVmaBuffer(
				bufferSize,
				VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
				VMA_MEMORY_USAGE_CPU_TO_GPU,
				pBuffer,
				rosella.memMan
			)
			vertexBuffer = pBuffer[0]
		}
	}

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
			copyBuffer(stagingBuffer, indexBuffer, bufferSize.toInt(), engine, device)
			vkDestroyBuffer(device.device, stagingBuffer, null)
			vkFreeMemory(device.device, stagingBufferMemory, null)
		}
	}

	private fun copyBuffer(srcBuffer: Long, dstBuffer: Long, size: Int, engine: Rosella, device: Device) {
		stackPush().use { stack ->
			val pCommandBuffer = stack.mallocPointer(1)
			val commandBuffer = engine.beginCmdBuffer(stack, pCommandBuffer)
			run {
				val copyRegion = VkBufferCopy.callocStack(1, stack)
				copyRegion.size(size.toLong())
				vkCmdCopyBuffer(commandBuffer, srcBuffer, dstBuffer, copyRegion)
			}
			vkEndCommandBuffer(commandBuffer)
			val submitInfo = VkSubmitInfo.callocStack(stack)
				.sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
				.pCommandBuffers(pCommandBuffer)
			vkQueueSubmit(engine.queues.graphicsQueue, submitInfo, VK_NULL_HANDLE).ok()
			vkQueueWaitIdle(engine.queues.graphicsQueue)
			vkFreeCommandBuffers(device.device, engine.commandPool, pCommandBuffer)
		}
	}

	fun destroy(memMan: MemMan, device: Device) {
		vmaFreeMemory(memMan.allocator, vertexBuffer)

		vkDestroyBuffer(device.device, indexBuffer, null)
		vkFreeMemory(device.device, indexBufferMemory, null)
	}

	fun create(device: Device, engine: Rosella): Model {
		loadModelFile()
		createVertexBuffer(engine)
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