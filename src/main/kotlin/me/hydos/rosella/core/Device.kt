package me.hydos.rosella.core

import me.hydos.rosella.util.ok
import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*
import java.util.stream.IntStream







class Device(private val engine: Rosella, private val layers: List<String>) {

	private var graphicsQueue: VkQueue
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

			if (isDeviceSuitable(device)) {
				return@use device
			}
		}

		error("No suitable device found")
	}

	init {
		stackPush().use {
			val indices = findQueueFamilies(physicalDevice)

			val queueCreateInfos = VkDeviceQueueCreateInfo.callocStack(1, it)
					.sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
					.queueFamilyIndex(indices.graphicsFamily!!)
					.pQueuePriorities(it.floats(1.0f))

			val deviceFeatures = VkPhysicalDeviceFeatures.callocStack(it)
			val createInfo = VkDeviceCreateInfo.callocStack(it)
					.sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
					.pQueueCreateInfos(queueCreateInfos)
					.pEnabledFeatures(deviceFeatures)

			if(engine.enableValidationLayers) {
				createInfo.ppEnabledLayerNames(engine.layersAsPtrBuffer(layers))
			}

			val pDevice: PointerBuffer = it.pointers(VK_NULL_HANDLE)
			vkCreateDevice(physicalDevice, createInfo, null, pDevice).ok()
			this.device = VkDevice(pDevice.get(0), physicalDevice, createInfo)

			val pGraphicsQueue: PointerBuffer = it.pointers(VK_NULL_HANDLE)
			vkGetDeviceQueue(device, indices.graphicsFamily!!, 0, pGraphicsQueue);
			this.graphicsQueue = VkQueue(pGraphicsQueue.get(0), device)
		}
	}
}

private fun isDeviceSuitable(device: VkPhysicalDevice): Boolean {
	return findQueueFamilies(device).isComplete
}

private fun findQueueFamilies(device: VkPhysicalDevice): QueueFamilyIndices {
	val indices = QueueFamilyIndices()

	stackPush().use { stack ->
		val queueFamilyCount = stack.ints(0)
		vkGetPhysicalDeviceQueueFamilyProperties(device, queueFamilyCount, null)

		val queueFamilies = VkQueueFamilyProperties.mallocStack(queueFamilyCount[0], stack)
		vkGetPhysicalDeviceQueueFamilyProperties(device, queueFamilyCount, queueFamilies)

		IntStream.range(0, queueFamilies.capacity())
				.filter { index: Int -> queueFamilies[index].queueFlags() and VK_QUEUE_GRAPHICS_BIT != 0 }
				.findFirst()
				.ifPresent { index: Int -> indices.graphicsFamily = index }

		return indices
	}
}

