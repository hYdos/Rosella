package me.hydos.rosella

import me.hydos.rosella.camera.Camera
import me.hydos.rosella.device.Device
import me.hydos.rosella.io.Window
import me.hydos.rosella.material.Material
import me.hydos.rosella.model.RenderObject
import me.hydos.rosella.renderer.Renderer
import me.hydos.rosella.resource.Identifier
import me.hydos.rosella.shader.ShaderPair
import me.hydos.rosella.swapchain.Frame
import me.hydos.rosella.util.memory.Memory
import me.hydos.rosella.util.ok
import org.lwjgl.PointerBuffer
import org.lwjgl.glfw.GLFW.glfwShowWindow
import org.lwjgl.glfw.GLFWVulkan
import org.lwjgl.glfw.GLFWVulkan.glfwCreateWindowSurface
import org.lwjgl.system.MemoryStack.stackGet
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.KHRSurface.vkDestroySurfaceKHR
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VK11.VK_API_VERSION_1_1
import java.nio.IntBuffer
import java.nio.LongBuffer
import java.util.function.Consumer
import java.util.stream.Collectors

/**
 * Main engine class. most interactions will happen here
 */
class Rosella(
	name: String,
	val enableValidationLayers: Boolean,
	val window: Window
) {
	var memory: Memory

	var renderer: Renderer = Renderer()

	var models = ArrayList<RenderObject>()
	var materials = HashMap<Identifier, Material>()
	var shaders = HashMap<Identifier, ShaderPair>()

	val camera = Camera(window)

	internal lateinit var vulkanInstance: VkInstance
	lateinit var maxImages: IntBuffer

	internal val device: Device
	private var debugMessenger: Long = 0
	var surface: Long = 0

	init {
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
		this.memory = Memory(device, vulkanInstance)
		renderer.initialize(this)

		glfwShowWindow(window.windowPtr)
	}

	private fun createInstance(name: String, validationLayers: Set<String>) {
		stackPush().use { stack ->
			val applicationInfo = VkApplicationInfo.callocStack(stack)
				.sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
				.pApplicationName(stack.UTF8Safe(name))
				.applicationVersion(VK_MAKE_VERSION(1, 0, 0))
				.pEngineName(stack.UTF8Safe("Rosella"))
				.engineVersion(VK_MAKE_VERSION(0, 1, 0))
				.apiVersion(VK_API_VERSION_1_1)
			val createInfo = VkInstanceCreateInfo.callocStack(stack)
				.pApplicationInfo(applicationInfo)
				.sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
				.ppEnabledExtensionNames(getRequiredExtensions(enableValidationLayers))
			if (enableValidationLayers) {
				createInfo.ppEnabledLayerNames(asPtrBuffer(validationLayers))
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
			glfwCreateWindowSurface(vulkanInstance, window.windowPtr, null, pSurface).ok()
			this.surface = pSurface.get(0)
		}
	}

	fun free() {
		for (model in models) {
			model.free(memory)
		}

		renderer.freeSwapChain(this)

		renderer.inFlightFrames!!.forEach(Consumer { frame: Frame ->
			vkDestroySemaphore(device.device, frame.renderFinishedSemaphore(), null)
			vkDestroySemaphore(device.device, frame.imageAvailableSemaphore(), null)
			vkDestroyFence(device.device, frame.fence(), null)
		})

		vkDestroyCommandPool(device.device, renderer.commandPool, null)

		renderer.swapChain.free(device.device)

		vkDestroyDevice(device.device, null)

		if (vkGetInstanceProcAddr(vulkanInstance, "vkDestroyDebugUtilsMessengerEXT") != NULL) {
			EXTDebugUtils.vkDestroyDebugUtilsMessengerEXT(vulkanInstance, debugMessenger, null)
		}

		vkDestroySurfaceKHR(vulkanInstance, surface, null)
		vkDestroyInstance(vulkanInstance, null)

		memory.free()
	}

	private fun getRequiredExtensions(validationLayersEnabled: Boolean): PointerBuffer? {
		val glfwExtensions = GLFWVulkan.glfwGetRequiredInstanceExtensions()
		if (validationLayersEnabled) {
			val stack = stackGet()
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
			if (createDebugUtilsMessengerEXT(vulkanInstance, createInfo, null, pDebugMessenger) != VK_SUCCESS) {
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

	internal fun asPtrBuffer(validationLayers: Set<String>): PointerBuffer {
		val stack = stackGet()
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

	fun addRenderObject(renderObject: RenderObject) {
		renderObject.loadMaterial(this)
		models.add(renderObject)
		renderObject.create(this)
	}

	fun registerMaterial(identifier: Identifier, material: Material) {
		materials[identifier] = material
	}

	fun registerShader(identifier: Identifier, shader: ShaderPair) {
		shaders[identifier] = shader
	}

	fun reloadMaterials() {
		for (material in materials.values) {
			material.loadShaders(this)
			material.loadTextures(device, this)
			material.shader.createDescriptorSetLayout()
			material.createPipeline(
				device,
				renderer.swapChain,
				renderer.renderPass,
				material.shader.descriptorSetLayout
			)
		}
		renderer.recreateSwapChain(window, camera, this)
	}
}

private fun createDebugUtilsMessengerEXT(
	instance: VkInstance,
	createInfo: VkDebugUtilsMessengerCreateInfoEXT,
	allocationCallbacks: VkAllocationCallbacks?,
	pDebugMessenger: LongBuffer
): Int {
	return if (vkGetInstanceProcAddr(instance, "vkCreateDebugUtilsMessengerEXT") != NULL) {
		EXTDebugUtils.vkCreateDebugUtilsMessengerEXT(instance, createInfo, allocationCallbacks, pDebugMessenger)
	} else VK_ERROR_EXTENSION_NOT_PRESENT
}
