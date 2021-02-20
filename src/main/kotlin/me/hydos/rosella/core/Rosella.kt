package me.hydos.rosella.core

import me.hydos.rosella.util.ok
import org.lwjgl.PointerBuffer
import org.lwjgl.glfw.GLFWVulkan
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.*
import java.nio.LongBuffer
import java.util.*
import java.util.stream.Collectors

class Rosella(name: String, val enableValidationLayers: Boolean) {
	internal val vulkanInstance: VkInstance
	private val device: Device
	private var state: State
	var debugMessenger: Long = 0

	init {
		state = State.STARTING

		MemoryStack.stackPush().use { stack ->
			// Setup Validation Layers
			val validationLayers = defaultValidationLayers
			if (enableValidationLayers && !validationLayersSupported(validationLayers)) {
				throw RuntimeException("Validation Layers are not available!")
			}

			// Setup an Vulkan Instance
			val applicationInfo = VkApplicationInfo.callocStack(stack)
					.sType(VK10.VK_STRUCTURE_TYPE_APPLICATION_INFO)
					.pApplicationName(stack.UTF8Safe(name))
					.applicationVersion(VK10.VK_MAKE_VERSION(1, 0, 0))
					.pEngineName(stack.UTF8Safe("Rosella"))
					.engineVersion(VK10.VK_MAKE_VERSION(0, 1, 0))
					.apiVersion(VK12.VK_API_VERSION_1_2)
			val createInfo = VkInstanceCreateInfo.callocStack(stack)
					.pApplicationInfo(applicationInfo)
					.sType(VK10.VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
					.ppEnabledExtensionNames(getRequiredExtensions(enableValidationLayers))
			if (enableValidationLayers) {
				createInfo.ppEnabledLayerNames(layersAsPtrBuffer(validationLayers))
				val debugCreateInfo = VkDebugUtilsMessengerCreateInfoEXT.callocStack(stack)
				populateDebugMessengerCreateInfo(debugCreateInfo)
				createInfo.pNext(debugCreateInfo.address())
			}

			val instancePtr = stack.mallocPointer(1)
			VK10.vkCreateInstance(createInfo, null, instancePtr).ok()

			vulkanInstance = VkInstance(instancePtr[0], createInfo)
			if (enableValidationLayers) {
				setupDebugMessenger()
			}

			// Get device information
			this.device = Device(this, validationLayers)

			state = State.READY
		}
	}

	fun destroy() {
		this.state = State.STOPPING
		if (VK10.vkGetInstanceProcAddr(vulkanInstance, "vkDestroyDebugUtilsMessengerEXT") != MemoryUtil.NULL) {
			EXTDebugUtils.vkDestroyDebugUtilsMessengerEXT(vulkanInstance, debugMessenger, null)
		}
		VK10.vkDestroyInstance(vulkanInstance, null)
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
		MemoryStack.stackPush().use { stack ->
			val createInfo = VkDebugUtilsMessengerCreateInfoEXT.callocStack(stack)
			populateDebugMessengerCreateInfo(createInfo)
			val pDebugMessenger = stack.longs(VK10.VK_NULL_HANDLE)
			if (createDebugUtilsMessengerEXT(vulkanInstance, createInfo, null, pDebugMessenger) != VK10.VK_SUCCESS) {
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
		return VK10.VK_FALSE
	}

	internal fun layersAsPtrBuffer(validationLayers: List<String>): PointerBuffer {
		val stack = MemoryStack.stackGet()
		val buffer = stack.mallocPointer(validationLayers.size)
		for (validationLayer in validationLayers) {
			val byteBuffer = stack.UTF8(validationLayer)
			buffer.put(byteBuffer)
		}
		return buffer.rewind()
	}

	private fun validationLayersSupported(validationLayers: List<String>): Boolean {
		MemoryStack.stackPush().use { stack ->
			val layerCount = stack.ints(0)
			VK10.vkEnumerateInstanceLayerProperties(layerCount, null).ok()
			val availableLayers = VkLayerProperties.mallocStack(layerCount[0], stack)
			VK10.vkEnumerateInstanceLayerProperties(layerCount, availableLayers).ok()
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
		private fun createDebugUtilsMessengerEXT(instance: VkInstance, createInfo: VkDebugUtilsMessengerCreateInfoEXT, allocationCallbacks: VkAllocationCallbacks?, pDebugMessenger: LongBuffer): Int {
			return if (VK10.vkGetInstanceProcAddr(instance, "vkCreateDebugUtilsMessengerEXT") != MemoryUtil.NULL) {
				EXTDebugUtils.vkCreateDebugUtilsMessengerEXT(instance, createInfo, allocationCallbacks, pDebugMessenger)
			} else VK10.VK_ERROR_EXTENSION_NOT_PRESENT
		}
	}
}