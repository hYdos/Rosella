package me.hydos.rosella.core

import me.hydos.rosella.io.Screen
import me.hydos.rosella.util.ok
import org.lwjgl.PointerBuffer
import org.lwjgl.glfw.GLFWVulkan
import org.lwjgl.glfw.GLFWVulkan.glfwCreateWindowSurface
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.KHRSurface.vkDestroySurfaceKHR
import org.lwjgl.vulkan.KHRSwapchain.vkDestroySwapchainKHR
import org.lwjgl.vulkan.VK10.*
import java.nio.LongBuffer
import java.util.stream.Collectors


class Rosella(name: String, val enableValidationLayers: Boolean, private val screen: Screen) {
	val width: Int = screen.width
	val height: Int = screen.height
	internal var swapchain: Swapchain
	internal lateinit var vulkanInstance: VkInstance
	internal val device: Device
	internal val pipeline: GfxPipeline
	internal var state: State
	var surface: Long = 0
	var debugMessenger: Long = 0
	var graphicsQueue: VkQueue? = null
	var presentQueue: VkQueue? = null

	init {
		state = State.STARTING

		// Setup Validation Layers
		val validationLayers = defaultValidationLayers.toSet()
		if (enableValidationLayers && !validationLayersSupported(validationLayers)) {
			throw RuntimeException("Validation Layers are not available!")
		}

		createInstance(name, validationLayers)

		if (enableValidationLayers) {
			setupDebugMessenger()
		}

		createSurface()
		this.device = Device(this, validationLayers)
		this.swapchain = Swapchain(this, device.device, device.physicalDevice, surface, validationLayers)
		createImgViews();
		this.pipeline = GfxPipeline(device, swapchain)

		state = State.READY
	}

	private fun createImgViews() {
		swapchain.swapChainImageViews = ArrayList(swapchain.swapChainImages!!.size)

		stackPush().use {
			val pImageView: LongBuffer = it.mallocLong(1)

			for (swapChainImage in swapchain.swapChainImages!!) {
				val createInfo: VkImageViewCreateInfo = VkImageViewCreateInfo.callocStack(it)
				createInfo.sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
					.image(swapChainImage)
					.viewType(VK_IMAGE_VIEW_TYPE_2D)
					.format(swapchain.swapChainImageFormat)
				createInfo.components().r(VK_COMPONENT_SWIZZLE_IDENTITY)
					.g(VK_COMPONENT_SWIZZLE_IDENTITY)
					.b(VK_COMPONENT_SWIZZLE_IDENTITY)
					.a(VK_COMPONENT_SWIZZLE_IDENTITY)
				createInfo.subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
					.baseMipLevel(0)
					.levelCount(1)
					.baseArrayLayer(0)
					.layerCount(1)
				vkCreateImageView(device.device, createInfo, null, pImageView).ok()
				(swapchain.swapChainImageViews as ArrayList<Long>).add(pImageView[0])
			}
		}
	}

	private fun createInstance(name: String, validationLayers: Set<String>) {
		stackPush().use { stack ->
			val applicationInfo = VkApplicationInfo.callocStack(stack)
				.sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
				.pApplicationName(stack.UTF8Safe(name))
				.applicationVersion(VK_MAKE_VERSION(1, 0, 0))
				.pEngineName(stack.UTF8Safe("Rosella"))
				.engineVersion(VK_MAKE_VERSION(0, 1, 0))
				.apiVersion(VK12.VK_API_VERSION_1_2)
			val createInfo = VkInstanceCreateInfo.callocStack(stack)
				.pApplicationInfo(applicationInfo)
				.sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
				.ppEnabledExtensionNames(getRequiredExtensions(enableValidationLayers))
			if (enableValidationLayers) {
				createInfo.ppEnabledLayerNames(layersAsPtrBuffer(validationLayers))
				val debugCreateInfo = VkDebugUtilsMessengerCreateInfoEXT.callocStack(stack)
				populateDebugMessengerCreateInfo(debugCreateInfo)
				createInfo.pNext(debugCreateInfo.address())
			}

			val instancePtr = stack.mallocPointer(1)
			vkCreateInstance(createInfo, null, instancePtr).ok()

			vulkanInstance = VkInstance(instancePtr[0], createInfo)
		}
	}

	private fun createSurface() {
		stackPush().use {
			val pSurface: LongBuffer = it.longs(VK_NULL_HANDLE)
			glfwCreateWindowSurface(vulkanInstance!!, screen.windowPtr, null, pSurface).ok()
			this.surface = pSurface.get(0)
		}
	}

	fun destroy() {
		this.state = State.STOPPING

		swapchain.swapChainImageViews!!.forEach { imageView -> vkDestroyImageView(device.device, imageView, null) }
		vkDestroyPipelineLayout(device.device, pipeline.pipelineLayout, null)
		vkDestroySwapchainKHR(device.device, swapchain.swapChain, null)
		vkDestroyDevice(device.device, null);
		if (vkGetInstanceProcAddr(vulkanInstance, "vkDestroyDebugUtilsMessengerEXT") != MemoryUtil.NULL) {
			EXTDebugUtils.vkDestroyDebugUtilsMessengerEXT(vulkanInstance, debugMessenger, null)
		}

		vkDestroySurfaceKHR(vulkanInstance, surface, null)
		vkDestroyInstance(vulkanInstance, null)
	}

	private fun getRequiredExtensions(validationLayersEnabled: Boolean): PointerBuffer? {
		val glfwExtensions = GLFWVulkan.glfwGetRequiredInstanceExtensions()
		if (validationLayersEnabled) {
			val stack = MemoryStack.stackGet()
			val extensions = stack.mallocPointer(glfwExtensions!!.capacity() + 1)
			extensions.put(glfwExtensions)
			extensions.put(stack.UTF8(EXTDebugUtils.VK_EXT_DEBUG_UTILS_EXTENSION_NAME))
			return extensions.rewind()
		}
		return glfwExtensions
	}

	private val defaultValidationLayers: List<String>
		get() {
			val validationLayers: MutableList<String> = ArrayList()
			validationLayers.add("VK_LAYER_KHRONOS_validation")
			return validationLayers
		}

	private fun setupDebugMessenger() {
		stackPush().use { stack ->
			val createInfo = VkDebugUtilsMessengerCreateInfoEXT.callocStack(stack)
			populateDebugMessengerCreateInfo(createInfo)
			val pDebugMessenger = stack.longs(VK_NULL_HANDLE)
			if (createDebugUtilsMessengerEXT(vulkanInstance!!, createInfo, null, pDebugMessenger) != VK_SUCCESS) {
				throw RuntimeException("Failed to set up debug messenger")
			}
			debugMessenger = pDebugMessenger[0]
		}
	}

	private fun populateDebugMessengerCreateInfo(debugCreateInfo: VkDebugUtilsMessengerCreateInfoEXT) {
		debugCreateInfo.sType(EXTDebugUtils.VK_STRUCTURE_TYPE_DEBUG_UTILS_MESSENGER_CREATE_INFO_EXT)
			.messageSeverity(EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_VERBOSE_BIT_EXT or EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT or EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT)
			.messageType(EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT or EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT or EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT)
			.pfnUserCallback(this::debugCallback)
	}

	private fun debugCallback(severity: Int, messageType: Int, pCallbackData: Long, pUserData: Long): Int {
		val callbackData = VkDebugUtilsMessengerCallbackDataEXT.create(pCallbackData)
		if (severity == EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT) {
			System.err.println(callbackData.pMessageString())
		} else {
			println(callbackData.pMessageString())
		}
		return VK_FALSE
	}

	internal fun layersAsPtrBuffer(validationLayers: Set<String>): PointerBuffer {
		val stack = MemoryStack.stackGet()
		val buffer = stack.mallocPointer(validationLayers.size)
		for (validationLayer in validationLayers) {
			val byteBuffer = stack.UTF8(validationLayer)
			buffer.put(byteBuffer)
		}
		return buffer.rewind()
	}

	private fun validationLayersSupported(validationLayers: Set<String>): Boolean {
		stackPush().use { stack ->
			val layerCount = stack.ints(0)
			vkEnumerateInstanceLayerProperties(layerCount, null).ok()
			val availableLayers = VkLayerProperties.mallocStack(layerCount[0], stack)
			vkEnumerateInstanceLayerProperties(layerCount, availableLayers).ok()
			val availableLayerNames = availableLayers.stream()
				.map { obj: VkLayerProperties -> obj.layerNameString() }
				.collect(Collectors.toSet())
			return availableLayerNames.containsAll(validationLayers)
		}
	}

	enum class State {
		STARTING, READY, STOPPING, ERRORED
	}

	companion object {
		private fun createDebugUtilsMessengerEXT(
			instance: VkInstance,
			createInfo: VkDebugUtilsMessengerCreateInfoEXT,
			allocationCallbacks: VkAllocationCallbacks?,
			pDebugMessenger: LongBuffer
		): Int {
			return if (vkGetInstanceProcAddr(instance, "vkCreateDebugUtilsMessengerEXT") != MemoryUtil.NULL) {
				EXTDebugUtils.vkCreateDebugUtilsMessengerEXT(instance, createInfo, allocationCallbacks, pDebugMessenger)
			} else VK_ERROR_EXTENSION_NOT_PRESENT
		}
	}
}