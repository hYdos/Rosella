package me.hydos.rosella.util.memory

import me.hydos.rosella.Rosella
import me.hydos.rosella.device.Device
import me.hydos.rosella.model.Vertex
import me.hydos.rosella.shader.pushconstant.ModelPushConstant
import me.hydos.rosella.util.ok
import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.system.Pointer
import org.lwjgl.util.vma.Vma
import org.lwjgl.util.vma.VmaAllocationCreateInfo
import org.lwjgl.util.vma.VmaAllocatorCreateInfo
import org.lwjgl.util.vma.VmaVulkanFunctions
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK11.VK_API_VERSION_1_1
import java.nio.ByteBuffer
import java.nio.LongBuffer

/**
 * Used for managing CPU and GPU memory.
 * This class will try to handle most vma stuff for the user so they dont have to touch much memory related stuff
 */
class Memory(val device: Device, private val instance: VkInstance) {

	val buffers = ArrayList<BufferInfo>()
	val mappedMemory = ArrayList<Long>()

	val allocator: Long = stackPush().use {
		val vulkanFunctions: VmaVulkanFunctions = VmaVulkanFunctions.callocStack(it)
			.set(instance, device.device)

		val createInfo: VmaAllocatorCreateInfo = VmaAllocatorCreateInfo.callocStack(it)
			.physicalDevice(device.physicalDevice)
			.device(device.device)
			.pVulkanFunctions(vulkanFunctions)
			.instance(instance)
			.vulkanApiVersion(VK_API_VERSION_1_1)

		val pAllocator = it.mallocPointer(1)
		Vma.vmaCreateAllocator(createInfo, pAllocator)
		pAllocator[0]
	}

	/**
	 * Used for creating the buffer written to before copied to the GPU
	 */
	fun createStagingBuf(
		size: Int,
		pBuffer: LongBuffer,
		stack: MemoryStack,
		callback: (data: PointerBuffer) -> Unit
	): BufferInfo {
		val stagingBufferAllocation: Long = createBuf(
			size,
			VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
			Vma.VMA_MEMORY_USAGE_CPU_ONLY,
			pBuffer
		)
		val stagingBuffer = pBuffer[0]
		val data = stack.mallocPointer(1)
		Vma.vmaMapMemory(allocator, stagingBufferAllocation, data)
		callback(data)
		Vma.vmaUnmapMemory(allocator, stagingBuffer)
		return BufferInfo(stagingBuffer, stagingBufferAllocation)
	}


	/**
	 * Used to create a Vulkan Memory Allocator Buffer.
	 */
	fun createBuf(
		size: Int,
		usage: Int,
		vmaUsage: Int,
		pBuffer: LongBuffer
	): Long {
		stackPush().use {
			val vulkanBufferInfo = VkBufferCreateInfo.callocStack(it)
				.sType(VK10.VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
				.size(size.toLong())
				.usage(usage)
				.sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE)

			val vmaBufferInfo: VmaAllocationCreateInfo = VmaAllocationCreateInfo.callocStack(it)
				.usage(vmaUsage)

			val allocation = it.mallocPointer(1)

			Vma.vmaCreateBuffer(allocator, vulkanBufferInfo, vmaBufferInfo, pBuffer, allocation, null)

			return allocation[0]
		}
	}

	/**
	 * Copies a buffer from one place to another. usually used to copy a staging buffer into GPU mem
	 */
	fun copyBuffer(srcBuffer: Long, dstBuffer: Long, size: Int, engine: Rosella, device: Device) {
		stackPush().use {
			val pCommandBuffer = it.mallocPointer(1)
			val commandBuffer = engine.renderer.beginCmdBuffer(it, pCommandBuffer)
			run {
				val copyRegion = VkBufferCopy.callocStack(1, it)
				copyRegion.size(size.toLong())
				VK10.vkCmdCopyBuffer(commandBuffer, srcBuffer, dstBuffer, copyRegion)
			}
			VK10.vkEndCommandBuffer(commandBuffer)
			val submitInfo = VkSubmitInfo.callocStack(it)
				.sType(VK10.VK_STRUCTURE_TYPE_SUBMIT_INFO)
				.pCommandBuffers(pCommandBuffer)
			VK10.vkQueueSubmit(engine.renderer.queues.graphicsQueue, submitInfo, VK10.VK_NULL_HANDLE).ok()
			VK10.vkQueueWaitIdle(engine.renderer.queues.graphicsQueue)
			VK10.vkFreeCommandBuffers(device.device, engine.renderer.commandPool, pCommandBuffer)
		}
	}

	fun createIndexBuffer(engine: Rosella, indices: ArrayList<Int>): Long {
		stackPush().use {
			val size: Int = (Integer.BYTES * indices.size)
			val pBuffer = it.mallocLong(1)
			val stagingBuffer = engine.memory.createStagingBuf(size, pBuffer, it) { data ->
				memcpy(data.getByteBuffer(0, size), indices)
			}

			createBuf(
				size,
				VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT or VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT,
				Vma.VMA_MEMORY_USAGE_CPU_TO_GPU,
				pBuffer
			)
			val indexBuffer = pBuffer[0]
			copyBuffer(stagingBuffer.buffer, indexBuffer, size, engine, device)
			freeBuffer(stagingBuffer)
			return indexBuffer
		}
	}

	private fun freeBuffer(buffer: BufferInfo) {
		Vma.vmaDestroyBuffer(allocator, buffer.buffer, buffer.allocation)
		Vma.vmaFreeMemory(allocator, buffer.buffer)
	}

	/**
	 * Creates a vertex buffer from an List of Vertices
	 */
	fun createVertexBuffer(engine: Rosella, vertices: List<Vertex>): Long {
		stackPush().use {
			val size: Int = Vertex.SIZEOF * vertices.size
			val pBuffer = it.mallocLong(1)
			val stagingBuffer = engine.memory.createStagingBuf(size, pBuffer, it) { data ->
				memcpy(data.getByteBuffer(0, size), vertices)
			}

			createBuf(
				size,
				VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT or VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT,
				Vma.VMA_MEMORY_USAGE_CPU_TO_GPU,
				pBuffer
			)
			val vertexBuffer = pBuffer[0]
			copyBuffer(stagingBuffer.buffer, vertexBuffer, size, engine, device)
			Vma.vmaDestroyBuffer(allocator, stagingBuffer.buffer, stagingBuffer.allocation)
			Vma.vmaFreeMemory(allocator, stagingBuffer.buffer)
			return vertexBuffer
		}
	}

	fun free() {
		for (buffer in buffers) {
			freeBuffer(buffer)
		}
	}
}

data class BufferInfo(val buffer: Long, val allocation: Long)

/**
 * Copies indices into the specified buffer
 */
fun memcpy(buffer: ByteBuffer, indices: ArrayList<Int>) {
	for (index in indices) {
		buffer.putInt(index)
	}
	buffer.rewind()
}

/**
 * Copies an Push Constant into the specified buffer
 */
fun memcpy(buffer: ByteBuffer, pushConstant: ModelPushConstant) {
	pushConstant.position[0, buffer]
}

/**
 * Copies an Vertex into the specified buffer
 */
fun memcpy(buffer: ByteBuffer, vertices: List<Vertex>) {
	for (vertex in vertices) {
		buffer.putFloat(vertex.pos.x())
		buffer.putFloat(vertex.pos.y())
		buffer.putFloat(vertex.pos.z())

		buffer.putFloat(vertex.color.x())
		buffer.putFloat(vertex.color.y())
		buffer.putFloat(vertex.color.z())

		buffer.putFloat(vertex.texCoords.x());
		buffer.putFloat(vertex.texCoords.y());
	}
}

/**
 * Copies an ByteBuffer into another ByteBuffer
 */
fun memcpy(dst: ByteBuffer, src: ByteBuffer, size: Long) {
	src.limit(size.toInt())
	dst.put(src)
	src.limit(src.capacity()).rewind()
}

fun List<Pointer>.asPointerBuffer(): PointerBuffer {
	val buffer = MemoryStack.stackGet().mallocPointer(size)

	for (pointer in this) {
		buffer.put(pointer)
	}

	return buffer.rewind()
}
