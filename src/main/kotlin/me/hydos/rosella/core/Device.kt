package me.hydos.rosella.core

import me.hydos.rosella.util.ok
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkPhysicalDevice
import org.lwjgl.vulkan.VkQueueFamilyProperties

class Device(private val engine: Rosella) {

	val physicalDevice: VkPhysicalDevice = MemoryStack.stackPush().use {
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

		for (i in 0..deviceCount[0]) {
			val device = VkPhysicalDevice(pPhysicalDevices[i], engine.vulkanInstance)

			if (isDeviceSuitable(device)) {
				return@use device
			}
		}

		error("No suitable device found")
	}
}

private fun isDeviceSuitable(device: VkPhysicalDevice): Boolean {
	return MemoryStack.stackPush().use {
		val queueFamilyCount = it.ints(0)
		vkGetPhysicalDeviceQueueFamilyProperties(device, queueFamilyCount, null)

		val queueFamilies = VkQueueFamilyProperties.mallocStack(queueFamilyCount.get(0), it)
		vkGetPhysicalDeviceQueueFamilyProperties(device, queueFamilyCount, queueFamilies)

		for (i in 0..queueFamilies.capacity()) {
			if (queueFamilies[i].queueFlags() and VK_QUEUE_GRAPHICS_BIT != 0) {
				return@use true
			}
		}

		return@use false
	}
}
