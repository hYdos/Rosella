package me.hydos.rosella.util

import me.hydos.rosella.core.Device
import me.hydos.rosella.model.Vertex
import org.lwjgl.vulkan.KHRSurface
import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceMemoryProperties

import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties
import java.nio.ByteBuffer


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

fun Int.ok(): Int {
	if (this != VK10.VK_SUCCESS) {
		throw RuntimeException(map[this] ?: toString(16))
	}

	return this
}

fun memcpy(buffer: ByteBuffer, vertices: Array<Vertex>) {
	for (vertex in vertices) {
		buffer.putFloat(vertex.pos.x())
		buffer.putFloat(vertex.pos.y())
		buffer.putFloat(vertex.color.x())
		buffer.putFloat(vertex.color.y())
		buffer.putFloat(vertex.color.z())
	}
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
