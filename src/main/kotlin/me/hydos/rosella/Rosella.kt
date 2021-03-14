package me.hydos.rosella

import me.hydos.rosella.device.Device
import me.hydos.rosella.device.Queues
import me.hydos.rosella.io.Screen
import me.hydos.rosella.memory.MemMan
import me.hydos.rosella.memory.memcpy
import me.hydos.rosella.model.Model
import me.hydos.rosella.resource.ResourceLoader
import me.hydos.rosella.shader.pushconstant.ModelPushConstant
import me.hydos.rosella.swapchain.SwapChain
import me.hydos.rosella.util.findMemoryType
import me.hydos.rosella.util.findQueueFamilies
import me.hydos.rosella.util.ok
import me.hydos.rosella.util.sizeof
import org.joml.Matrix4f
import org.lwjgl.PointerBuffer
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWVulkan
import org.lwjgl.glfw.GLFWVulkan.glfwCreateWindowSurface
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryStack.stackGet
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.KHRSurface.vkDestroySurfaceKHR
import org.lwjgl.vulkan.KHRSwapchain.*
import org.lwjgl.vulkan.VK10.*
import java.nio.LongBuffer
import java.util.function.Consumer
import java.util.stream.Collectors

class Rosella(
	name: String,
	val enableValidationLayers: Boolean,
	internal val screen: Screen,
	val resources: ResourceLoader
) {

	var memMan: MemMan

	var depthBuffer = DepthBuffer()

	var model: Model = Model("models/fact_core.gltf")

	var view: Matrix4f = Matrix4f()
	var proj: Matrix4f = Matrix4f()

	private var inFlightFrames: MutableList<Frame>? = null
	private var imagesInFlight: MutableMap<Int, Frame>? = null
	private var currentFrame = 0

	private var framebufferResize: Boolean = false

	lateinit var swapChain: SwapChain
	private lateinit var renderPass: RenderPass
	internal lateinit var vulkanInstance: VkInstance

	internal val device: Device
	private var state: State
	private var debugMessenger: Long = 0
	var surface: Long = 0

	var queues: Queues = Queues()

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

		this.memMan = MemMan(device, vulkanInstance)

		this.createCmdPool(this)
		createModels()
		createSwapChain()

		glfwShowWindow(screen.windowPtr)
		state = State.READY
	}

	private fun createProjAndView() {
		view = Matrix4f()
		proj = Matrix4f()

		view.lookAt(2.0f, -40.0f, 2.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f)
		proj.perspective(
			Math.toRadians(45.0).toFloat(),
			swapChain.swapChainExtent!!.width().toFloat() / swapChain.swapChainExtent!!.height().toFloat(),
			0.1f,
			1000.0f
		)
		proj.m11(proj.m11() * -1)
	}

	private fun createModels() {
		model.create(this)
		model.material.loadShaders(device, memMan)
		model.material.loadTextures(device, this)
		model.material.shader.createDescriptorSetLayout()
	}

	fun beginCmdBuffer(stack: MemoryStack, pCommandBuffer: PointerBuffer): VkCommandBuffer {
		val allocInfo = VkCommandBufferAllocateInfo.callocStack(stack)
			.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
			.level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
			.commandPool(commandPool)
			.commandBufferCount(1)
		vkAllocateCommandBuffers(device.device, allocInfo, pCommandBuffer)
		val commandBuffer = VkCommandBuffer(pCommandBuffer[0], device.device)
		val beginInfo = VkCommandBufferBeginInfo.callocStack(stack)
			.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
			.flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT)
		vkBeginCommandBuffer(commandBuffer, beginInfo)
		return commandBuffer
	}

	private fun createSwapChain() {
		this.swapChain = SwapChain(this, device.device, device.physicalDevice, surface)
		this.renderPass = RenderPass(device, swapChain, this)
		createImgViews()
		model.material.createPipeline(device, swapChain, renderPass, model.material.shader.descriptorSetLayout)
		depthBuffer.createDepthResources(this)
		createFramebuffers()
		createProjAndView()
		model.material.initializeShader(this)
		createCommandBuffers(swapChain, renderPass)
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
			vkCreateImage(device.device, imageInfo, null, pTextureImage).ok("Failed to allocate image memory")
			val memRequirements = VkMemoryRequirements.mallocStack(stack)
			vkGetImageMemoryRequirements(device.device, pTextureImage[0], memRequirements)
			val allocInfo = VkMemoryAllocateInfo.callocStack(stack)
				.sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
				.allocationSize(memRequirements.size())
				.memoryTypeIndex(findMemoryType(memRequirements.memoryTypeBits(), memProperties, device))
			vkAllocateMemory(device.device, allocInfo, null, pTextureImageMemory).ok("Failed to allocate image memory")
			vkBindImageMemory(device.device, pTextureImage[0], pTextureImageMemory[0], 0)
		}
	}

	var commandPool: Long = 0
	lateinit var commandBuffers: ArrayList<VkCommandBuffer>

	fun createCmdPool(engine: Rosella) {
		stackPush().use { stack ->
			val queueFamilyIndices = findQueueFamilies(engine.device.physicalDevice, engine)
			val poolInfo = VkCommandPoolCreateInfo.callocStack(stack)
			poolInfo.sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
			poolInfo.queueFamilyIndex(queueFamilyIndices.graphicsFamily!!)
			val pCommandPool = stack.mallocLong(1)
			vkCreateCommandPool(engine.device.device, poolInfo, null, pCommandPool).ok()
			commandPool = pCommandPool[0]
		}
	}

	fun createCommandBuffers(
		swapchain: SwapChain,
		renderPass: RenderPass
	) {
		/**
		 * Create the Command Buffers
		 */
		val commandBuffersCount: Int = swapchain.swapChainFramebuffers.size

		commandBuffers = java.util.ArrayList(commandBuffersCount)

		stackPush().use {
			// Allocate
			val allocInfo = VkCommandBufferAllocateInfo.callocStack(it)
			allocInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
			allocInfo.commandPool(commandPool)
			allocInfo.level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
			allocInfo.commandBufferCount(commandBuffersCount)
			val pCommandBuffers = it.mallocPointer(commandBuffersCount)
			if (vkAllocateCommandBuffers(device.device, allocInfo, pCommandBuffers) != VK_SUCCESS) {
				throw RuntimeException("Failed to allocate command buffers")
			}

			for (i in 0 until commandBuffersCount) {
				commandBuffers.add(
					VkCommandBuffer(
						pCommandBuffers[i],
						device.device
					)
				)
			}
			val beginInfo = VkCommandBufferBeginInfo.callocStack(it)
				.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)

			val renderPassInfo = VkRenderPassBeginInfo.callocStack(it)
				.sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
				.renderPass(renderPass.renderPass)

			val renderArea = VkRect2D.callocStack(it)
				.offset(VkOffset2D.callocStack(it).set(0, 0))
				.extent(swapchain.swapChainExtent!!)

			renderPassInfo.renderArea(renderArea)

			val clearValues = VkClearValue.callocStack(2, it)
			clearValues[0].color().float32(it.floats(0xef / 255f, 0x32 / 255f, 0x3d / 255f, 1.0f))
			clearValues[1].depthStencil().set(1.0f, 0)

			renderPassInfo.pClearValues(clearValues)

			for (i in 0 until commandBuffersCount) {
				val commandBuffer = commandBuffers[i]
				vkBeginCommandBuffer(commandBuffer, beginInfo).ok()
				renderPassInfo.framebuffer(swapchain.swapChainFramebuffers[i])

				// Draw stuff
				vkCmdBeginRenderPass(commandBuffer, renderPassInfo, VK_SUBPASS_CONTENTS_INLINE)
				run {
					vkCmdBindPipeline(
						commandBuffer,
						VK_PIPELINE_BIND_POINT_GRAPHICS,
						model.material.graphicsPipeline
					)
					val offsets = it.longs(0)

					val vertexBuffers = it.longs(model.vertexBuffer)
					vkCmdBindVertexBuffers(commandBuffer, 0, vertexBuffers, offsets)
					vkCmdBindIndexBuffer(commandBuffer, model.indexBuffer, 0, VK_INDEX_TYPE_UINT32)
					vkCmdBindDescriptorSets(
						commandBuffer,
						VK_PIPELINE_BIND_POINT_GRAPHICS,
						model.material.pipelineLayout,
						0,
						it.longs(model.material.shader.descriptorSets[i]),
						null
					)

					//TODO: push constants are the way to make multiple objects work. Valoghese remind me after i get them working to make multiple models work. :)
					// Load the push constant into memory
					val data = it.mallocPointer(1)
					val modelPushConstant = ModelPushConstant()
					modelPushConstant.position.add(0f, 1f, 0f)
					val size = sizeof(modelPushConstant.position)
					vkMapMemory(
						device.device,
						model.material.shader.pushConstantBuffersMemory[0], // Hardcoded to 0 for the 1 model
						0,
						size.toLong(),
						0,
						data
					)
					run {
						memcpy(data.getByteBuffer(0, size), modelPushConstant)
					}
					vkUnmapMemory(
						device.device,
						model.material.shader.pushConstantBuffersMemory[0]
					)// Hardcoded to 0 for the 1 model

					val buffer = it.longs(1)
					buffer.put(model.material.shader.pushConstantBuffers[0])
					vkCmdPushConstants(
						commandBuffer,
						model.material.pipelineLayout,
						VK_SHADER_STAGE_VERTEX_BIT,
						0,
						data.getByteBuffer(0, size)
					)

					vkCmdDrawIndexed(commandBuffer, model.indices.size, 1, 0, 0, 0)
				}
				vkCmdEndRenderPass(commandBuffer)
				vkEndCommandBuffer(commandBuffer).ok()
			}
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
				if (depthBuffer.hasStencilComponent(format)) {
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

				barrier.srcAccessMask(0)
				barrier.dstAccessMask(VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT or VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT)

				sourceStage = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT
				destinationStage = VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT

			} else {
				throw IllegalArgumentException("Unsupported layout transition")
			}
			val commandBuffer: VkCommandBuffer = model.material.beginSingleTimeCommands(this)
			vkCmdPipelineBarrier(
				commandBuffer,
				sourceStage, destinationStage,
				0,
				null,
				null,
				barrier
			)
			model.material.endSingleTimeCommands(commandBuffer, device, this)
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
				inFlightFrames!!.add(
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
		swapChain.swapChainFramebuffers = ArrayList(swapChain.swapChainImageViews.size)
		stackPush().use { stack ->
			val attachments = stack.longs(VK_NULL_HANDLE, depthBuffer.depthImageView)
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
				swapChain.swapChainFramebuffers.add(pFramebuffer[0])
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
			vkCreateImageView(device.device, viewInfo, null, pImageView).ok("Failed to create texture image view")
			return pImageView[0]
		}
	}

	private fun createImgViews() {
		swapChain.swapChainImageViews = ArrayList(swapChain.swapChainImages.size)

		for (swapChainImage in swapChain.swapChainImages) {
			swapChain.swapChainImageViews.add(
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

	fun free() {
		this.state = State.STOPPING

		model.destroy(memMan)

		freeSwapChain()

		inFlightFrames!!.forEach(Consumer { frame: Frame ->
			vkDestroySemaphore(device.device, frame.renderFinishedSemaphore(), null)
			vkDestroySemaphore(device.device, frame.imageAvailableSemaphore(), null)
			vkDestroyFence(device.device, frame.fence(), null)
		})
		imagesInFlight = null

		vkDestroyCommandPool(device.device, commandPool, null)

		swapChain.free(device.device)

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

			model.material.shader.updateUbo(imageIndex, swapChain, this)

			if (imagesInFlight!!.containsKey(imageIndex)) {
				vkWaitForFences(device.device, imagesInFlight!![imageIndex]!!.fence(), true, UINT64_MAX)
			}
			imagesInFlight!![imageIndex] = thisFrame
			val submitInfo = VkSubmitInfo.callocStack(stack)
				.sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
				.waitSemaphoreCount(1)
				.pWaitSemaphores(thisFrame.pImageAvailableSemaphore())
				.pWaitDstStageMask(stack.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT))
				.pSignalSemaphores(thisFrame.pRenderFinishedSemaphore())
				.pCommandBuffers(stack.pointers(commandBuffers[imageIndex]))
			vkResetFences(device.device, thisFrame.pFence())
			vkQueueSubmit(queues.graphicsQueue, submitInfo, thisFrame.fence()).ok()
			val presentInfo = VkPresentInfoKHR.callocStack(stack)
				.sType(VK_STRUCTURE_TYPE_PRESENT_INFO_KHR)
				.pWaitSemaphores(thisFrame.pRenderFinishedSemaphore())
				.swapchainCount(1)
				.pSwapchains(stack.longs(swapChain.swapChain))
				.pImageIndices(pImageIndex)

			vkResult = vkQueuePresentKHR(queues.presentQueue, presentInfo)

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
		freeSwapChain()
		createSwapChain()
		createProjAndView()
	}

	private fun freeSwapChain() {
		vkDestroyDescriptorPool(device.device, model.material.shader.descriptorPool, null)

		// Free Depth Buffer
		depthBuffer.free(device)

		model.material.shader.free()

		swapChain.swapChainFramebuffers.forEach { framebuffer ->
			vkDestroyFramebuffer(
				device.device,
				framebuffer,
				null
			)
		}
		model.material.free(device, this)
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
