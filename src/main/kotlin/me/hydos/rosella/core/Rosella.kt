package me.hydos.rosella.core

import me.hydos.rosella.core.swapchain.CommandBuffers
import me.hydos.rosella.core.swapchain.GfxPipeline
import me.hydos.rosella.core.swapchain.RenderPass
import me.hydos.rosella.core.swapchain.Swapchain
import me.hydos.rosella.io.Screen
import me.hydos.rosella.model.Model
import me.hydos.rosella.model.ubo.*
import me.hydos.rosella.util.findMemoryType
import me.hydos.rosella.util.ok
import org.lwjgl.PointerBuffer
import org.lwjgl.glfw.GLFW.glfwGetFramebufferSize
import org.lwjgl.glfw.GLFW.glfwWaitEvents
import org.lwjgl.glfw.GLFWVulkan
import org.lwjgl.glfw.GLFWVulkan.glfwCreateWindowSurface
import org.lwjgl.system.MemoryStack.stackGet
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.system.Pointer
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.KHRSurface.vkDestroySurfaceKHR
import org.lwjgl.vulkan.KHRSwapchain.*
import org.lwjgl.vulkan.VK10.*
import java.nio.IntBuffer
import java.nio.LongBuffer
import java.util.*
import java.util.function.Consumer
import java.util.stream.Collectors











class Rosella(name: String, val enableValidationLayers: Boolean, internal val screen: Screen) {


	private var depthImage: Long = 0
	private var depthImageMemory: Long = 0
	private var depthImageView: Long = 0

	var descriptorSetLayout: Long = 0
	var descriptorPool: Long = 0
	var descriptorSets: List<Long> = ArrayList()
	var model: Model = Model()

	private var inFlightFrames: List<Frame>? = null
	private var imagesInFlight: MutableMap<Int, Frame>? = null
	private var currentFrame = 0

	private var framebufferResize: Boolean = false

	internal var commandBuffers: CommandBuffers
	private lateinit var swapChain: Swapchain
	private lateinit var renderPass: RenderPass
	internal lateinit var vulkanInstance: VkInstance
	private lateinit var pipeline: GfxPipeline

	internal val device: Device
	private var state: State
	private var debugMessenger: Long = 0
	var surface: Long = 0

	lateinit var graphicsQueue: VkQueue
	lateinit var presentQueue: VkQueue

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
		this.commandBuffers = CommandBuffers(device, this)
		createModels()
		createFullSwapChain()

		state = State.READY
	}

	private fun createModels() {
		model.create(device, this)
		createDescriptorSetLayout()
	}

	private fun createDescriptorSetLayout() {
		stackPush().use {
			val bindings = VkDescriptorSetLayoutBinding.callocStack(2, it)

			// Ubo Layout
			bindings[0]
				.binding(0)
				.descriptorCount(1)
				.descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
				.pImmutableSamplers(null)
				.stageFlags(VK_SHADER_STAGE_VERTEX_BIT)

			// Sampler Layout
			bindings[1]
				.binding(1)
				.descriptorCount(1)
				.descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
				.pImmutableSamplers(null)
				.stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT)

			val layoutInfo = VkDescriptorSetLayoutCreateInfo.callocStack(it)
			layoutInfo.sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
			layoutInfo.pBindings(bindings)
			val pDescriptorSetLayout = it.mallocLong(1)
			if (vkCreateDescriptorSetLayout(device.device, layoutInfo, null, pDescriptorSetLayout) !== VK_SUCCESS) {
				throw RuntimeException("Failed to create descriptor set layout")
			}
			descriptorSetLayout = pDescriptorSetLayout[0]
		}
	}

	private fun createFullSwapChain() {
		this.swapChain = Swapchain(this, device.device, device.physicalDevice, surface)
		this.renderPass = RenderPass(device, swapChain, this)
		createImgViews()
		this.pipeline = GfxPipeline(device, swapChain, renderPass, descriptorSetLayout)
		createDepthResources()
		createFramebuffers()
		createUniformBuffers(swapChain, device)
		createDescriptorPool()
		createDescriptorSets()
		this.commandBuffers.createCommandBuffers(swapChain, renderPass, pipeline)
		createSyncObjects()
	}

	fun createImage(
		width: Int, height: Int, format: Int, tiling: Int, usage: Int, memProperties: Int,
		pTextureImage: LongBuffer, pTextureImageMemory: LongBuffer
	) {
		stackPush().use { stack ->
			val imageInfo = VkImageCreateInfo.callocStack(stack)
				.sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
				.imageType(VK_IMAGE_TYPE_2D)
			imageInfo.extent().width(width)
			imageInfo.extent().height(height)
			imageInfo.extent().depth(1)
			imageInfo.mipLevels(1)
				.arrayLayers(1)
				.format(format)
				.tiling(tiling)
				.initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
				.usage(usage)
				.samples(VK_SAMPLE_COUNT_1_BIT)
				.sharingMode(VK_SHARING_MODE_EXCLUSIVE)
			if (vkCreateImage(device.device, imageInfo, null, pTextureImage) !== VK_SUCCESS) {
				throw RuntimeException("Failed to create image")
			}
			val memRequirements = VkMemoryRequirements.mallocStack(stack)
			vkGetImageMemoryRequirements(device.device, pTextureImage[0], memRequirements)
			val allocInfo = VkMemoryAllocateInfo.callocStack(stack)
				.sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
				.allocationSize(memRequirements.size())
				.memoryTypeIndex(findMemoryType(memRequirements.memoryTypeBits(), memProperties, device))
			if (vkAllocateMemory(device.device, allocInfo, null, pTextureImageMemory) !== VK_SUCCESS) {
				throw RuntimeException("Failed to allocate image memory")
			}
			vkBindImageMemory(device.device, pTextureImage[0], pTextureImageMemory[0], 0)
		}
	}

	private fun createDepthResources() {
		stackPush().use { stack ->
			val depthFormat: Int = findDepthFormat()
			val pDepthImage = stack.mallocLong(1)
			val pDepthImageMemory = stack.mallocLong(1)
			createImage(
				swapChain.swapChainExtent!!.width(), swapChain.swapChainExtent!!.height(),
				depthFormat,
				VK_IMAGE_TILING_OPTIMAL,
				VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT,
				VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
				pDepthImage,
				pDepthImageMemory
			)
			depthImage = pDepthImage[0]
			depthImageMemory = pDepthImageMemory[0]
			depthImageView = createImageView(depthImage, depthFormat, VK_IMAGE_ASPECT_DEPTH_BIT)

			// Explicitly transitioning the depth image
			transitionImageLayout(
				depthImage, depthFormat,
				VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL
			)
		}
	}

	fun transitionImageLayout(
		image: Long,
		format: Int,
		oldLayout: Int,
		newLayout: Int,
	) {
		stackPush().use { stack ->
			val barrier = VkImageMemoryBarrier.callocStack(1, stack)
				.sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
				.oldLayout(oldLayout)
				.newLayout(newLayout)
				.srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
				.dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
				.image(image)
			barrier.subresourceRange().baseMipLevel(0)
			barrier.subresourceRange().levelCount(1)
			barrier.subresourceRange().baseArrayLayer(0)
			barrier.subresourceRange().layerCount(1)


			if (newLayout === VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL) {
				barrier.subresourceRange().aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT)
				if (hasStencilComponent(format)) {
					barrier.subresourceRange().aspectMask(
						barrier.subresourceRange().aspectMask() or VK_IMAGE_ASPECT_STENCIL_BIT
					)
				}
			} else {
				barrier.subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
			}

			val sourceStage: Int
			val destinationStage: Int
			if (oldLayout == VK_IMAGE_LAYOUT_UNDEFINED && newLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
				barrier.srcAccessMask(0)
					.dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
				sourceStage = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT
				destinationStage = VK_PIPELINE_STAGE_TRANSFER_BIT
			} else if (oldLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL && newLayout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
				barrier.srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
					.dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
				sourceStage = VK_PIPELINE_STAGE_TRANSFER_BIT
				destinationStage = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT
			} else if (oldLayout == VK_IMAGE_LAYOUT_UNDEFINED && newLayout == VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL) {

				barrier.srcAccessMask(0);
				barrier.dstAccessMask(VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT or VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT)

				sourceStage = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
				destinationStage = VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT;

			} else {
				throw IllegalArgumentException("Unsupported layout transition")
			}
			val commandBuffer: VkCommandBuffer = model.beginSingleTimeCommands(device, this)
			vkCmdPipelineBarrier(
				commandBuffer,
				sourceStage, destinationStage,
				0,
				null,
				null,
				barrier
			)
			model.endSingleTimeCommands(commandBuffer, device, this)
		}
	}

	private fun findSupportedFormat(formatCandidates: IntBuffer, tiling: Int, features: Int): Int {
		stackPush().use { stack ->
			val props = VkFormatProperties.callocStack(stack)
			for (i in 0 until formatCandidates.capacity()) {
				val format = formatCandidates[i]
				vkGetPhysicalDeviceFormatProperties(device.physicalDevice, format, props)
				if (tiling == VK_IMAGE_TILING_LINEAR && props.linearTilingFeatures() and features == features) {
					return format
				} else if (tiling == VK_IMAGE_TILING_OPTIMAL && props.optimalTilingFeatures() and features == features) {
					return format
				}
			}
		}
		throw RuntimeException("Failed to find supported format")
	}


	fun findDepthFormat(): Int {
		return findSupportedFormat(
			stackGet().ints(VK_FORMAT_D32_SFLOAT, VK_FORMAT_D32_SFLOAT_S8_UINT, VK_FORMAT_D24_UNORM_S8_UINT),
			VK_IMAGE_TILING_OPTIMAL,
			VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT
		)
	}

	fun hasStencilComponent(format: Int): Boolean {
		return format == VK_FORMAT_D32_SFLOAT_S8_UINT || format == VK_FORMAT_D24_UNORM_S8_UINT
	}

	private fun createDescriptorSets() {
		stackPush().use { stack ->
			val layouts = stack.mallocLong(swapChain.swapChainImages.size)
			for (i in 0 until layouts.capacity()) {
				layouts.put(i, descriptorSetLayout)
			}
			val allocInfo = VkDescriptorSetAllocateInfo.callocStack(stack)
				.sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
				.descriptorPool(descriptorPool)
				.pSetLayouts(layouts)
			val pDescriptorSets = stack.mallocLong(swapChain.swapChainImages.size)
			if (vkAllocateDescriptorSets(device.device, allocInfo, pDescriptorSets) !== VK_SUCCESS) {
				throw RuntimeException("Failed to allocate descriptor sets")
			}
			descriptorSets = ArrayList(pDescriptorSets.capacity())
			val bufferInfo = VkDescriptorBufferInfo.callocStack(1, stack)
				.offset(0)
				.range(UniformBufferObject.SIZEOF.toLong())
			val imageInfo = VkDescriptorImageInfo.callocStack(1, stack)
				.imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
				.imageView(model.textureImageView)
				.sampler(model.textureSampler)
			val descriptorWrites = VkWriteDescriptorSet.callocStack(2, stack)
			val uboDescriptorWrite = descriptorWrites[0]
				.sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
				.dstBinding(0)
				.dstArrayElement(0)
				.descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
				.descriptorCount(1)
				.pBufferInfo(bufferInfo)
			val samplerDescriptorWrite = descriptorWrites[1]
				.sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
				.dstBinding(1)
				.dstArrayElement(0)
				.descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
				.descriptorCount(1)
				.pImageInfo(imageInfo)
			for (i in 0 until pDescriptorSets.capacity()) {
				val descriptorSet = pDescriptorSets[i]
				bufferInfo.buffer(uniformBuffers[i])
				uboDescriptorWrite.dstSet(descriptorSet)
				samplerDescriptorWrite.dstSet(descriptorSet)
				vkUpdateDescriptorSets(device.device, descriptorWrites, null)
				(descriptorSets as ArrayList<Long>).add(descriptorSet)
			}
		}
	}

	private fun createDescriptorPool() {
		stackPush().use { stack ->
			val poolSizes = VkDescriptorPoolSize.callocStack(2, stack)

			// Uniform Buffer Pool Size
			poolSizes[0]
				.type(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
				.descriptorCount(swapChain.swapChainImages.size)

			// Texture Sampler Pool Size
			poolSizes[1]
				.type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
				.descriptorCount(swapChain.swapChainImages.size)

			val poolInfo = VkDescriptorPoolCreateInfo.callocStack(stack)
				.sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
				.pPoolSizes(poolSizes)
				.maxSets(swapChain.swapChainImages.size)

			val pDescriptorPool = stack.mallocLong(1)
			if (vkCreateDescriptorPool(device.device, poolInfo, null, pDescriptorPool) !== VK_SUCCESS) {
				throw RuntimeException("Failed to create descriptor pool")
			}
			descriptorPool = pDescriptorPool[0]
		}
	}

	private fun createSyncObjects() {
		inFlightFrames = ArrayList(MAX_FRAMES_IN_FLIGHT)
		imagesInFlight = HashMap(swapChain.swapChainImages.size)

		stackPush().use { stack ->
			val semaphoreInfo = VkSemaphoreCreateInfo.callocStack(stack)
			semaphoreInfo.sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO)
			val fenceInfo = VkFenceCreateInfo.callocStack(stack)
			fenceInfo.sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO)
			fenceInfo.flags(VK_FENCE_CREATE_SIGNALED_BIT)
			val pImageAvailableSemaphore = stack.mallocLong(1)
			val pRenderFinishedSemaphore = stack.mallocLong(1)
			val pFence = stack.mallocLong(1)
			for (i in 0 until MAX_FRAMES_IN_FLIGHT) {
				vkCreateSemaphore(
					device.device,
					semaphoreInfo,
					null,
					pImageAvailableSemaphore
				).ok()
				vkCreateSemaphore(
					device.device,
					semaphoreInfo,
					null,
					pRenderFinishedSemaphore
				).ok()
				vkCreateFence(device.device, fenceInfo, null, pFence).ok()
				(inFlightFrames as ArrayList<Frame>).add(
					Frame(
						pImageAvailableSemaphore[0],
						pRenderFinishedSemaphore[0],
						pFence[0]
					)
				)
			}
		}
	}

	fun windowResizeCallback(windowPtr: Long, width: Int, height: Int) {
		this.framebufferResize = true
	}

	private fun createFramebuffers() {
		swapChain.swapChainFramebuffers = ArrayList<Long>(swapChain.swapChainImageViews.size)
		stackPush().use { stack ->
			val attachments = stack.longs(VK_NULL_HANDLE, depthImageView)
			val pFramebuffer = stack.mallocLong(1)
			val framebufferInfo = VkFramebufferCreateInfo.callocStack(stack)
			framebufferInfo.sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO)
			framebufferInfo.renderPass(renderPass.renderPass)
			framebufferInfo.width(swapChain.swapChainExtent!!.width())
			framebufferInfo.height(swapChain.swapChainExtent!!.height())
			framebufferInfo.layers(1)
			for (imageView in swapChain.swapChainImageViews) {
				attachments.put(0, imageView)
				framebufferInfo.pAttachments(attachments)
				vkCreateFramebuffer(device.device, framebufferInfo, null, pFramebuffer).ok()
				(swapChain.swapChainFramebuffers as ArrayList<Long>).add(pFramebuffer[0])
			}
		}
	}

	fun createImageView(image: Long, format: Int, aspectFlags: Int): Long {
		stackPush().use { stack ->
			val viewInfo = VkImageViewCreateInfo.callocStack(stack)
			.sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
			.image(image)
			.viewType(VK_IMAGE_VIEW_TYPE_2D)
			.format(format)
			viewInfo.subresourceRange().aspectMask(aspectFlags)
			viewInfo.subresourceRange().baseMipLevel(0)
			viewInfo.subresourceRange().levelCount(1)
			viewInfo.subresourceRange().baseArrayLayer(0)
			viewInfo.subresourceRange().layerCount(1)
			val pImageView = stack.mallocLong(1)
			if (vkCreateImageView(device.device, viewInfo, null, pImageView) !== VK_SUCCESS) {
				throw RuntimeException("Failed to create texture image view")
			}
			return pImageView[0]
		}
	}

	private fun createImgViews() {
		swapChain.swapChainImageViews = ArrayList(swapChain.swapChainImages.size)

		for (swapChainImage in swapChain.swapChainImages) {
			(swapChain.swapChainImageViews as ArrayList<Long>).add(
				createImageView(
					swapChainImage,
					swapChain.swapChainImageFormat,
					VK_IMAGE_ASPECT_COLOR_BIT
				)
			)
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
			glfwCreateWindowSurface(vulkanInstance, screen.windowPtr, null, pSurface).ok()
			this.surface = pSurface.get(0)
		}
	}

	fun destroy() {
		this.state = State.STOPPING

		//TODO: less temporary model system
		model.destroy(device)

		destroySwapChain()

		inFlightFrames!!.forEach(Consumer { frame: Frame ->
			vkDestroySemaphore(device.device, frame.renderFinishedSemaphore(), null)
			vkDestroySemaphore(device.device, frame.imageAvailableSemaphore(), null)
			vkDestroyFence(device.device, frame.fence(), null)
		})
		imagesInFlight = null

		vkDestroyCommandPool(device.device, commandBuffers.commandPool, null)

		vkDestroySwapchainKHR(device.device, swapChain.swapChain, null)
		vkDestroyDevice(device.device, null)

		if (vkGetInstanceProcAddr(vulkanInstance, "vkDestroyDebugUtilsMessengerEXT") != NULL) {
			EXTDebugUtils.vkDestroyDebugUtilsMessengerEXT(vulkanInstance, debugMessenger, null)
		}

		vkDestroySurfaceKHR(vulkanInstance, surface, null)
		vkDestroyInstance(vulkanInstance, null)
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

	private fun asPtrBuffer(list: List<Pointer>): PointerBuffer {
		val stack = stackGet()
		val buffer = stack.mallocPointer(list.size)
		list.forEach { pointer: Pointer? -> buffer.put(pointer!!) }
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

	fun renderFrame() {
		stackPush().use { stack ->
			val thisFrame = inFlightFrames!![currentFrame]
			vkWaitForFences(device.device, thisFrame.pFence(), true, UINT64_MAX)
			val pImageIndex = stack.mallocInt(1)

			var vkResult: Int = vkAcquireNextImageKHR(
				device.device,
				swapChain.swapChain,
				UINT64_MAX,
				thisFrame.imageAvailableSemaphore(),
				VK_NULL_HANDLE,
				pImageIndex
			)

			if (vkResult == VK_ERROR_OUT_OF_DATE_KHR) {
				recreateSwapChain()
				return
			}

			val imageIndex = pImageIndex[0]

			updateUniformBuffer(imageIndex, swapChain, device)

			if (imagesInFlight!!.containsKey(imageIndex)) {
				vkWaitForFences(device.device, imagesInFlight!![imageIndex]!!.fence(), true, UINT64_MAX)
			}
			imagesInFlight!![imageIndex] = thisFrame
			val submitInfo = VkSubmitInfo.callocStack(stack)
			submitInfo.sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
			submitInfo.waitSemaphoreCount(1)
			submitInfo.pWaitSemaphores(thisFrame.pImageAvailableSemaphore())
			submitInfo.pWaitDstStageMask(stack.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT))
			submitInfo.pSignalSemaphores(thisFrame.pRenderFinishedSemaphore())
			submitInfo.pCommandBuffers(stack.pointers(commandBuffers.commandBuffers[imageIndex]))
			vkResetFences(device.device, thisFrame.pFence())
			vkQueueSubmit(graphicsQueue, submitInfo, thisFrame.fence()).ok()
			val presentInfo = VkPresentInfoKHR.callocStack(stack)
			presentInfo.sType(VK_STRUCTURE_TYPE_PRESENT_INFO_KHR)
			presentInfo.pWaitSemaphores(thisFrame.pRenderFinishedSemaphore())
			presentInfo.swapchainCount(1)
			presentInfo.pSwapchains(stack.longs(swapChain.swapChain))
			presentInfo.pImageIndices(pImageIndex)

			vkResult = vkQueuePresentKHR(presentQueue, presentInfo)

			if (vkResult == VK_ERROR_OUT_OF_DATE_KHR || vkResult == VK_SUBOPTIMAL_KHR || framebufferResize) {
				framebufferResize = false
				recreateSwapChain()
			} else if (vkResult != VK_SUCCESS) {
				throw RuntimeException("Failed to present swap chain image")
			}

			currentFrame = (currentFrame + 1) % MAX_FRAMES_IN_FLIGHT
		}
	}

	private fun recreateSwapChain() {
		stackPush().use { stack ->
			val width = stack.ints(0)
			val height = stack.ints(0)
			while (width[0] == 0 && height[0] == 0) {
				glfwGetFramebufferSize(screen.windowPtr, width, height)
				glfwWaitEvents()
			}
		}

		vkDeviceWaitIdle(device.device)
		destroySwapChain()
		createFullSwapChain()
	}

	private fun destroySwapChain() {
		vkDestroyDescriptorPool(device.device, descriptorPool, null)

		// Free Depth Buffer
		vkDestroyImageView(device.device, depthImageView, null)
		vkDestroyImage(device.device, depthImage, null)
		vkFreeMemory(device.device, depthImageMemory, null)

		destroyUbos(device)

		swapChain.swapChainFramebuffers.forEach { framebuffer ->
			vkDestroyFramebuffer(
				device.device,
				framebuffer,
				null
			)
		}
		vkFreeCommandBuffers(device.device, commandBuffers.commandPool, asPtrBuffer(commandBuffers.commandBuffers))
		vkDestroyPipeline(device.device, pipeline.graphicsPipeline, null)
		vkDestroyPipelineLayout(device.device, pipeline.pipelineLayout, null)
		vkDestroyRenderPass(device.device, renderPass.renderPass, null)
		swapChain.swapChainImageViews.forEach { imageView -> vkDestroyImageView(device.device, imageView, null) }
		vkDestroySwapchainKHR(device.device, swapChain.swapChain, null)
	}


	enum class State {
		STARTING, READY, STOPPING
	}

	companion object {
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

		private const val MAX_FRAMES_IN_FLIGHT = 2
		private const val UINT64_MAX = -0x1L
	}
}