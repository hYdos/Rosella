package me.hydos.rosella.core

import me.hydos.rosella.util.ok
import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR
import org.lwjgl.vulkan.VK10.*


class Device(private val engine: Rosella, private val layers: List<String>) {

	internal var device: VkDevice

	private val physicalDevice: VkPhysicalDevice = stackPush().use {
		val deviceCount = run {
			val count = it.ints(0)
			vkEnumeratePhysicalDevices(engine.vulkanInstance, count, null).ok()
			count
		}

		if (deviceCount[0] == 0) {
			error("Failed to find GPUs supporting Vulkan")
		}

		val pPhysicalDevices = it.mallocPointer(deviceCount[0])
		vkEnumeratePhysicalDevices(engine.vulkanInstance, deviceCount, pPhysicalDevices).ok()

		for (i in 0 until deviceCount.capacity()) {
			val device = VkPhysicalDevice(pPhysicalDevices[i], engine.vulkanInstance)

			if (isDeviceSuitable(device, engine)) {
				return@use device
			}
		}

		error("No suitable device found")
	}

	init {
		stackPush().use {
			val indices = findQueueFamilies(physicalDevice, engine)

			val queueCreateInfos = VkDeviceQueueCreateInfo.callocStack(1, it)
					.sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
					.queueFamilyIndex(indices.graphicsFamily!!)
					.pQueuePriorities(it.floats(1.0f))

			val deviceFeatures = VkPhysicalDeviceFeatures.callocStack(it)
			val createInfo = VkDeviceCreateInfo.callocStack(it)
					.sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
					.pQueueCreateInfos(queueCreateInfos)
					.pEnabledFeatures(deviceFeatures)

			if (engine.enableValidationLayers) {
				createInfo.ppEnabledLayerNames(engine.layersAsPtrBuffer(layers))
			}

			val pDevice: PointerBuffer = it.pointers(VK_NULL_HANDLE)
			vkCreateDevice(physicalDevice, createInfo, null, pDevice).ok()
			this.device = VkDevice(pDevice.get(0), physicalDevice, createInfo)

			val pQueue: PointerBuffer = it.pointers(VK_NULL_HANDLE)

			vkGetDeviceQueue(device, indices.graphicsFamily!!, 0, pQueue)
			engine.graphicsQueue = VkQueue(pQueue.get(0), device)

			vkGetDeviceQueue(device, indices.presentFamily!!, 0, pQueue)
			engine.presentQueue = VkQueue(pQueue.get(0), device)
		}
	}
}

private fun isDeviceSuitable(device: VkPhysicalDevice, engine: Rosella): Boolean {
	return findQueueFamilies(device, engine).isComplete
}

private fun findQueueFamilies(device: VkPhysicalDevice, engine: Rosella): QueueFamilyIndices {
	val indices = QueueFamilyIndices()

	stackPush().use { stack ->
		val queueFamilyCount = stack.ints(0)
		vkGetPhysicalDeviceQueueFamilyProperties(device, queueFamilyCount, null)

		val queueFamilies = VkQueueFamilyProperties.mallocStack(queueFamilyCount[0], stack)
		vkGetPhysicalDeviceQueueFamilyProperties(device, queueFamilyCount, queueFamilies)

		val presentSupport = stack.ints(VK_FALSE)

		var i = 0
		while (i < queueFamilies.capacity() || !indices.isComplete) {
			if (queueFamilies[i].queueFlags() and VK_QUEUE_GRAPHICS_BIT != 0) {
				indices.graphicsFamily = i
			}
			vkGetPhysicalDeviceSurfaceSupportKHR(device, i, engine.surface, presentSupport)
			if (presentSupport.get(0) == VK_TRUE) {
				indices.presentFamily = i
			}
			i++
		}
		return indices
	}
}

