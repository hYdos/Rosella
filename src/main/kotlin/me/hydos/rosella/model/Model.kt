package me.hydos.rosella.model

import me.hydos.rosella.core.Device
import me.hydos.rosella.util.findMemoryType
import me.hydos.rosella.util.memcpy
import me.hydos.rosella.util.ok
import org.joml.Vector2f
import org.joml.Vector3f
import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkBufferCreateInfo
import org.lwjgl.vulkan.VkMemoryAllocateInfo
import org.lwjgl.vulkan.VkMemoryRequirements
import java.nio.LongBuffer


class Model {
	// Tmp things
	internal val VERTICES: Array<Vertex> = arrayOf(
		Vertex(Vector2f(0.0f, -0.5f), Vector3f(1.0f, 0.0f, 0.0f)),
		Vertex(Vector2f(0.5f, 0.5f), Vector3f(0.0f, 1.0f, 0.0f)),
		Vertex(Vector2f(-0.5f, 0.5f), Vector3f(0.0f, 0.0f, 1.0f))
	)

	var vertexBuffer: Long = 0
	var vertexBufferMemory: Long = 0

	fun createVertBuf(device: Device) {
		stackPush().use {
			val bufferInfo: VkBufferCreateInfo = VkBufferCreateInfo.callocStack(it)
				.sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
				.size((Vertex.SIZEOF * VERTICES.size).toLong())
				.usage(VK_BUFFER_USAGE_VERTEX_BUFFER_BIT)
				.sharingMode(VK_SHARING_MODE_EXCLUSIVE)

			val pVertexBuffer: LongBuffer = it.mallocLong(1)

			vkCreateBuffer(device.device, bufferInfo, null, pVertexBuffer).ok()
			vertexBuffer = pVertexBuffer[0]

			val memRequirements: VkMemoryRequirements = VkMemoryRequirements.mallocStack(it)
			vkGetBufferMemoryRequirements(device.device, vertexBuffer, memRequirements)

			val allocInfo: VkMemoryAllocateInfo = VkMemoryAllocateInfo.callocStack(it)
				.sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
				.allocationSize(memRequirements.size())
				.memoryTypeIndex(
					findMemoryType(
						memRequirements.memoryTypeBits(),
						VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
						device
					)
				)

			val pVertexBufferMemory: LongBuffer = it.mallocLong(1)
			vkAllocateMemory(device.device, allocInfo, null, pVertexBufferMemory).ok()
			vertexBufferMemory = pVertexBufferMemory[0]
			vkBindBufferMemory(device.device, vertexBuffer, vertexBufferMemory, 0)

			val data: PointerBuffer = it.mallocPointer(1)

			vkMapMemory(device.device, vertexBufferMemory, 0, bufferInfo.size(), 0, data)
			run {
				memcpy(data.getByteBuffer(0, bufferInfo.size().toInt()), VERTICES)
			}
			vkUnmapMemory(device.device, vertexBufferMemory)
		}
	}

	fun destroy(device: Device) {
		vkDestroyBuffer(device.device, vertexBuffer, null)
		vkFreeMemory(device.device, vertexBufferMemory, null)
	}
}