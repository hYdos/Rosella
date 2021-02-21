package me.hydos.rosella.model

import me.hydos.rosella.core.Device
import me.hydos.rosella.core.Rosella
import me.hydos.rosella.util.findMemoryType
import me.hydos.rosella.util.memcpy
import me.hydos.rosella.util.ok
import org.joml.Vector2f
import org.joml.Vector3f
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*
import java.nio.LongBuffer


class Model {
	val vertices = arrayOf(
		Vertex(Vector2f(-0.5f, -0.5f), Vector3f(1.0f, 0.0f, 0.0f)),
		Vertex(Vector2f(0.5f, -0.5f), Vector3f(0.0f, 1.0f, 0.0f)),
		Vertex(Vector2f(0.5f, 0.5f), Vector3f(0.0f, 0.0f, 1.0f)),
		Vertex(Vector2f(-0.5f, 0.5f), Vector3f(1.0f, 1.0f, 1.0f))
	)

	internal val indices = shortArrayOf(
		0, 1, 2, 2, 3, 0
	)

	var vertexBuffer: Long = 0
	var vertexBufferMemory: Long = 0

	var indexBuffer: Long = 0
	var indexBufferMemory: Long = 0

	fun createVertexBuffer(device: Device, rosella: Rosella) {
		stackPush().use { stack ->
			val bufferSize: Int = Vertex.SIZEOF * vertices.size
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
			run { memcpy(data.getByteBuffer(0, bufferSize), vertices) }
			vkUnmapMemory(device.device, stagingBufferMemory)
			createBuffer(
				bufferSize,
				VK_BUFFER_USAGE_TRANSFER_DST_BIT or VK_BUFFER_USAGE_VERTEX_BUFFER_BIT,
				VK_MEMORY_HEAP_DEVICE_LOCAL_BIT,
				pBuffer,
				pBufferMemory,
				device
			)
			vertexBuffer = pBuffer[0]
			vertexBufferMemory = pBufferMemory[0]
			copyBuffer(stagingBuffer, vertexBuffer, bufferSize, rosella, device)
			vkDestroyBuffer(device.device, stagingBuffer, null)
			vkFreeMemory(device.device, stagingBufferMemory, null)
		}
	}


	internal fun createIndexBuffer(device: Device, engine: Rosella) {
		stackPush().use { stack ->
			val bufferSize: Long = (java.lang.Short.BYTES * indices.size).toLong()
			val pBuffer = stack.mallocLong(1)
			val pBufferMemory = stack.mallocLong(1)
			createBuffer(
				bufferSize.toInt(),
				VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
				VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
				pBuffer,
				pBufferMemory,
				device
			)
			val stagingBuffer = pBuffer[0]
			val stagingBufferMemory = pBufferMemory[0]
			val data = stack.mallocPointer(1)
			vkMapMemory(device.device, stagingBufferMemory, 0, bufferSize, 0, data)
			run { memcpy(data.getByteBuffer(0, bufferSize.toInt()), indices) }
			vkUnmapMemory(device.device, stagingBufferMemory)
			createBuffer(
				bufferSize.toInt(),
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


	private fun createBuffer(
		size: Int,
		usage: Int,
		properties: Int,
		pBuffer: LongBuffer,
		pBufferMemory: LongBuffer,
		device: Device
	) {
		stackPush().use { stack ->
			val bufferInfo = VkBufferCreateInfo.callocStack(stack)
			bufferInfo.sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
			bufferInfo.size(size.toLong())
			bufferInfo.usage(usage)
			bufferInfo.sharingMode(VK_SHARING_MODE_EXCLUSIVE)
			vkCreateBuffer(device.device, bufferInfo, null, pBuffer).ok()
			val memRequirements = VkMemoryRequirements.mallocStack(stack)
			vkGetBufferMemoryRequirements(device.device, pBuffer[0], memRequirements)
			val allocInfo = VkMemoryAllocateInfo.callocStack(stack)
			allocInfo.sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
			allocInfo.allocationSize(memRequirements.size())
			allocInfo.memoryTypeIndex(findMemoryType(memRequirements.memoryTypeBits(), properties, device))
			vkAllocateMemory(device.device, allocInfo, null, pBufferMemory).ok()
			vkBindBufferMemory(device.device, pBuffer[0], pBufferMemory[0], 0)
		}
	}

	private fun copyBuffer(srcBuffer: Long, dstBuffer: Long, size: Int, engine: Rosella, device: Device) {
		stackPush().use { stack ->
			val allocInfo = VkCommandBufferAllocateInfo.callocStack(stack)
			allocInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
			allocInfo.level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
			allocInfo.commandPool(engine.commandBuffers.commandPool)
			allocInfo.commandBufferCount(1)
			val pCommandBuffer = stack.mallocPointer(1)
			vkAllocateCommandBuffers(device.device, allocInfo, pCommandBuffer)
			val commandBuffer = VkCommandBuffer(pCommandBuffer[0], device.device)
			val beginInfo = VkCommandBufferBeginInfo.callocStack(stack)
			beginInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
			beginInfo.flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT)
			vkBeginCommandBuffer(commandBuffer, beginInfo)
			run {
				val copyRegion = VkBufferCopy.callocStack(1, stack)
				copyRegion.size(size.toLong())
				vkCmdCopyBuffer(commandBuffer, srcBuffer, dstBuffer, copyRegion)
			}
			vkEndCommandBuffer(commandBuffer)
			val submitInfo = VkSubmitInfo.callocStack(stack)
			submitInfo.sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
			submitInfo.pCommandBuffers(pCommandBuffer)
			vkQueueSubmit(engine.graphicsQueue, submitInfo, VK_NULL_HANDLE).ok()
			vkQueueWaitIdle(engine.graphicsQueue)
			vkFreeCommandBuffers(device.device, engine.commandBuffers.commandPool, pCommandBuffer)
		}
	}

	fun destroy(device: Device) {
		vkDestroyBuffer(device.device, vertexBuffer, null)
		vkFreeMemory(device.device, vertexBufferMemory, null)

		vkDestroyBuffer(device.device, indexBuffer, null)
		vkFreeMemory(device.device, indexBufferMemory, null)
	}
}