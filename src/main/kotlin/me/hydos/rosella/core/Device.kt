package me.hydos.rosella.core

import me.hydos.rosella.core.swapchain.SwapChainSupportDetails
import me.hydos.rosella.core.swapchain.querySwapChainSupport
import me.hydos.rosella.util.ok
import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.KHRSurface.*
import org.lwjgl.vulkan.KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME
import org.lwjgl.vulkan.VK10.*


val DEVICE_EXTENSIONS: Set<String> = listOf(VK_KHR_SWAPCHAIN_EXTENSION_NAME).toSet()

class Device(private val engine: Rosella, private val layers: Set<String>) {

	internal var device: VkDevice

	internal val physicalDevice: VkPhysicalDevice = stackPush().use {
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
			val uniqueQueueFamilies = indices.unique()
			val queueCreateInfos = VkDeviceQueueCreateInfo.callocStack(uniqueQueueFamilies.size, it)

			for (i in uniqueQueueFamilies.indices) {
				queueCreateInfos[i]
					.sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
					.queueFamilyIndex(uniqueQueueFamilies[i])
					.pQueuePriorities(it.floats(1.0f))
			}

			val deviceFeatures: VkPhysicalDeviceFeatures = VkPhysicalDeviceFeatures.callocStack(it)
				.samplerAnisotropy(true)

			val createInfo: VkDeviceCreateInfo = VkDeviceCreateInfo.callocStack(it)
				.sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
				.pQueueCreateInfos(queueCreateInfos)
				.pEnabledFeatures(deviceFeatures)
				.ppEnabledExtensionNames(engine.asPtrBuffer(DEVICE_EXTENSIONS))

			if (engine.enableValidationLayers) {
				createInfo.ppEnabledLayerNames(engine.asPtrBuffer(layers))
			}
			val pDevice: PointerBuffer = it.pointers(VK_NULL_HANDLE)
			if (vkCreateDevice(physicalDevice, createInfo, null, pDevice) != VK_SUCCESS) {
				throw RuntimeException("Failed to create logical device")
			}
			device = VkDevice(pDevice[0], physicalDevice, createInfo)

			val pQueue: PointerBuffer = it.pointers(VK_NULL_HANDLE)

			vkGetDeviceQueue(device, indices.graphicsFamily!!, 0, pQueue)
			engine.graphicsQueue = VkQueue(pQueue[0], device)

			vkGetDeviceQueue(device, indices.presentFamily!!, 0, pQueue)
			engine.presentQueue = VkQueue(pQueue[0], device)
		}
	}


	private fun isDeviceSuitable(device: VkPhysicalDevice, engine: Rosella): Boolean {
		val indices = findQueueFamilies(device, engine)

		val extensionsSupported = checkDeviceExtensionsSupport(device)
		var swapChainAdequate = false
		var anisotropySupported = false

		if (extensionsSupported) {
			stackPush().use {
				val swapChainSupport: SwapChainSupportDetails = querySwapChainSupport(device, it, engine.surface)
				swapChainAdequate =
					swapChainSupport.formats!!.hasRemaining() && swapChainSupport.presentModes!!.hasRemaining()
				val supportedFeatures: VkPhysicalDeviceFeatures = VkPhysicalDeviceFeatures.mallocStack(it)
				vkGetPhysicalDeviceFeatures(device, supportedFeatures)
				anisotropySupported = supportedFeatures.samplerAnisotropy()
			}
		}

		return indices.isComplete && extensionsSupported && swapChainAdequate && anisotropySupported
	}

	private fun checkDeviceExtensionsSupport(device: VkPhysicalDevice): Boolean {
		stackPush().use { stack ->
			val extensionCount = stack.ints(0)
			vkEnumerateDeviceExtensionProperties(device, null as String?, extensionCount, null)
//			val availableExtensions =
			VkExtensionProperties.mallocStack(extensionCount[0], stack)
//			return availableExtensions.stream().collect(toSet()).containsAll(DEVICE_EXTENSIONS)
			return true
//			TODO("something broke here. based workaround")
		}
	}
}

internal fun findQueueFamilies(device: VkPhysicalDevice, engine: Rosella): QueueFamilyIndices {
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