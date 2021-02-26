package me.hydos.rosella.util

import me.hydos.rosella.core.Device
import me.hydos.rosella.model.Vertex
import me.hydos.rosella.model.ubo.UniformBufferObject
import org.joml.Matrix4f
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceMemoryProperties
import java.nio.ByteBuffer
import java.nio.LongBuffer





private val map = mutableMapOf<Int, String>().apply {
	this[VK10.VK_NOT_READY] = "VK_NOT_READY"
	this[VK10.VK_TIMEOUT] = "VK_TIMEOUT"
	this[VK10.VK_EVENT_SET] = "VK_EVENT_SET"
	this[VK10.VK_EVENT_RESET] = "VK_EVENT_RESET"
	this[VK10.VK_INCOMPLETE] = "VK_INCOMPLETE"
	this[VK10.VK_ERROR_OUT_OF_HOST_MEMORY] = "VK_ERROR_OUT_OF_HOST_MEMORY"
	this[VK10.VK_ERROR_OUT_OF_DEVICE_MEMORY] = "VK_ERROR_OUT_OF_DEVICE_MEMORY"
	this[VK10.VK_ERROR_INITIALIZATION_FAILED] = "VK_ERROR_INITIALIZATION_FAILED"
	this[VK10.VK_ERROR_DEVICE_LOST] = "VK_ERROR_DEVICE_LOST"
	this[VK10.VK_ERROR_MEMORY_MAP_FAILED] = "VK_ERROR_MEMORY_MAP_FAILED"
	this[VK10.VK_ERROR_LAYER_NOT_PRESENT] = "VK_ERROR_LAYER_NOT_PRESENT"
	this[VK10.VK_ERROR_EXTENSION_NOT_PRESENT] = "VK_ERROR_EXTENSION_NOT_PRESENT"
	this[VK10.VK_ERROR_FEATURE_NOT_PRESENT] = "VK_ERROR_FEATURE_NOT_PRESENT"
	this[VK10.VK_ERROR_INCOMPATIBLE_DRIVER] = "VK_ERROR_INCOMPATIBLE_DRIVER"
	this[VK10.VK_ERROR_TOO_MANY_OBJECTS] = "VK_ERROR_TOO_MANY_OBJECTS"
	this[VK10.VK_ERROR_FORMAT_NOT_SUPPORTED] = "VK_ERROR_FORMAT_NOT_SUPPORTED"
	this[VK10.VK_ERROR_FRAGMENTED_POOL] = "VK_ERROR_FRAGMENTED_POOL"
	this[VK10.VK_ERROR_UNKNOWN] = "VK_ERROR_UNKNOWN"
	this[KHRSurface.VK_ERROR_NATIVE_WINDOW_IN_USE_KHR] = "VK_ERROR_NATIVE_WINDOW_IN_USE_KHR"
}

private val SIZEOF_CACHE = mutableMapOf<Class<*>, Int>().apply {
	this[Byte::class.java] = Byte.SIZE_BYTES
	this[Character::class.java] = Character.BYTES
	this[Short::class.java] = Short.SIZE_BYTES
	this[Integer::class.java] = Integer.BYTES
	this[Float::class.java] = Float.SIZE_BYTES
	this[Long::class.java] = Long.SIZE_BYTES
	this[Double::class.java] = Double.SIZE_BYTES

	this[Vector2f::class.java] = 2 * Float.SIZE_BYTES
	this[Vector3f::class.java] = 3 * Float.SIZE_BYTES
	this[Vector4f::class.java] = 4 * Float.SIZE_BYTES

	this[Matrix4f::class.java] = this[Vector4f::class.java]!!
}

fun sizeof(obj: Any?): Int {
	return if (obj == null) 0 else SIZEOF_CACHE[obj.javaClass] ?: 0
}

fun alignof(obj: Any?): Int {
	return if (obj == null) 0 else SIZEOF_CACHE[obj.javaClass] ?: Integer.BYTES
}

fun alignas(offset: Int, alignment: Int): Int {
	return if (offset % alignment == 0) offset else (offset - 1 or alignment - 1) + 1
}

fun Int.ok(): Int {
	if (this != VK10.VK_SUCCESS) {
		throw RuntimeException(map[this] ?: toString(16))
	}

	return this
}

fun createBuffer(
	size: Int,
	usage: Int,
	properties: Int,
	pBuffer: LongBuffer,
	pBufferMemory: LongBuffer,
	device: Device
) {
	MemoryStack.stackPush().use { stack ->
		val bufferInfo = VkBufferCreateInfo.callocStack(stack)
			.sType(VK10.VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
			.size(size.toLong())
			.usage(usage)
			.sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE)
		VK10.vkCreateBuffer(device.device, bufferInfo, null, pBuffer).ok()
		val memRequirements = VkMemoryRequirements.mallocStack(stack)
		VK10.vkGetBufferMemoryRequirements(device.device, pBuffer[0], memRequirements)
		val allocInfo = VkMemoryAllocateInfo.callocStack(stack)
			.sType(VK10.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
			.allocationSize(memRequirements.size())
			.memoryTypeIndex(findMemoryType(memRequirements.memoryTypeBits(), properties, device))
		VK10.vkAllocateMemory(device.device, allocInfo, null, pBufferMemory).ok()
		VK10.vkBindBufferMemory(device.device, pBuffer[0], pBufferMemory[0], 0)
	}
}

fun memcpy(buffer: ByteBuffer, vertices: Array<Vertex>) {
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

fun memcpy(buffer: ByteBuffer, indices: ShortArray) {
	for (index in indices) {
		buffer.putShort(index)
	}
	buffer.rewind()
}

fun memcpy(buffer: ByteBuffer, ubo: UniformBufferObject) {
	val mat4Size = 16 * java.lang.Float.BYTES
	ubo.model[0, buffer]
	ubo.view.get(alignas(mat4Size, alignof(ubo.view)), buffer)
	ubo.proj.get(alignas(mat4Size * 2, alignof(ubo.view)), buffer)
}

fun memcpy(dst: ByteBuffer, src: ByteBuffer, size: Long) {
	src.limit(size.toInt())
	dst.put(src)
	src.limit(src.capacity()).rewind()
}

fun findMemoryType(typeFilter: Int, properties: Int, device: Device): Int {
	val memProperties = VkPhysicalDeviceMemoryProperties.mallocStack()
	vkGetPhysicalDeviceMemoryProperties(device.physicalDevice, memProperties)
	for (i in 0 until memProperties.memoryTypeCount()) {
		if (typeFilter and (1 shl i) != 0 && memProperties.memoryTypes(i)
				.propertyFlags() and properties == properties
		) {
			return i
		}
	}
	throw RuntimeException("Failed to find suitable memory type")
}
