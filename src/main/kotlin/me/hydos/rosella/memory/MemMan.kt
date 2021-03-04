package me.hydos.rosella.memory

import me.hydos.rosella.device.Device
import me.hydos.rosella.model.Vertex
import me.hydos.rosella.shader.ubo.ModelPushConstant
import me.hydos.rosella.shader.ubo.ModelUbo
import me.hydos.rosella.util.alignas
import me.hydos.rosella.util.alignof
import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.util.vma.Vma
import org.lwjgl.util.vma.VmaAllocationCreateInfo
import org.lwjgl.util.vma.VmaAllocatorCreateInfo
import org.lwjgl.util.vma.VmaVulkanFunctions
import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VkBufferCreateInfo
import org.lwjgl.vulkan.VkInstance
import java.nio.ByteBuffer
import java.nio.LongBuffer

/**
 * Used for managing CPU and GPU memory. if your getting memory problems, blame this class
 */
class MemMan(val device: Device, private val instance: VkInstance) {

	var allocator: Long = 0

	init {
		stackPush().use {
			val vulkanFunctions: VmaVulkanFunctions = VmaVulkanFunctions.callocStack(it)
				.set(instance, device.device)

			val createInfo: VmaAllocatorCreateInfo = VmaAllocatorCreateInfo.callocStack(it)
				.device(device.device)
				.physicalDevice(device.physicalDevice)
				.instance(instance)
				.pVulkanFunctions(vulkanFunctions)

			val pAllocator = it.mallocPointer(1)
			Vma.vmaCreateAllocator(createInfo, pAllocator)
			this.allocator = pAllocator[0]
		}
	}

	/**
	 * Used for creating the buffer written to before copied to the GPU
	 */
	fun createStagingBuf(size: Int, pBuffer: LongBuffer, stack: MemoryStack, callback: (data: PointerBuffer) -> Unit): BufferInfo {
		val stagingBufferAllocation: Long = createBuf(
			size,
			VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
			Vma.VMA_MEMORY_USAGE_CPU_ONLY,
			pBuffer
		)
		val stagingBuffer = pBuffer[0]
		val data = stack.mallocPointer(1)
		Vma.vmaMapMemory(allocator, stagingBufferAllocation, data)
		run { callback(data) }
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

	data class BufferInfo(val buffer: Long, val allocation: Long)
}

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
 * Copies an UBO(Uniform Buffer Object) into the specified buffer
 */
fun memcpy(buffer: ByteBuffer, ubo: ModelUbo) {
	val mat4Size = 16 * java.lang.Float.BYTES
	ubo.model[0, buffer]
	ubo.view.get(alignas(mat4Size, alignof(ubo.view)), buffer)
	ubo.proj.get(alignas(mat4Size * 2, alignof(ubo.view)), buffer)
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