package me.hydos.rosella.renderer

import me.hydos.rosella.Rosella
import me.hydos.rosella.camera.Camera
import me.hydos.rosella.device.Device
import me.hydos.rosella.device.Queues
import me.hydos.rosella.io.Window
import me.hydos.rosella.shader.pushconstant.ModelPushConstant
import me.hydos.rosella.swapchain.DepthBuffer
import me.hydos.rosella.swapchain.Frame
import me.hydos.rosella.swapchain.RenderPass
import me.hydos.rosella.swapchain.SwapChain
import me.hydos.rosella.util.findQueueFamilies
import me.hydos.rosella.util.memory.asPointerBuffer
import me.hydos.rosella.util.memory.memcpy
import me.hydos.rosella.util.ok
import me.hydos.rosella.util.sizeof
import org.lwjgl.PointerBuffer
import org.lwjgl.glfw.GLFW
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*

class Renderer() {
	var depthBuffer = DepthBuffer()

	var inFlightFrames: MutableList<Frame>? = null
	var imagesInFlight: MutableMap<Int, Frame>? = null
	private var currentFrame = 0

	private var resizeFramebuffer: Boolean = false

	lateinit var swapChain: SwapChain
	lateinit var renderPass: RenderPass

	lateinit var device: Device

	var queues: Queues = Queues()

	var commandPool: Long = 0
	lateinit var commandBuffers: ArrayList<VkCommandBuffer>

	fun createSwapChain(engine: Rosella) {
		this.swapChain = SwapChain(engine, device.device, device.physicalDevice, engine.surface)
		this.renderPass = RenderPass(device, swapChain, engine)
		createImgViews()
		for (material in engine.materials.values) {
			material.createPipeline(device, swapChain, renderPass, material.shader.descriptorSetLayout)
		}
		depthBuffer.createDepthResources(engine)
		createFrameBuffers()
		engine.camera.createViewAndProj(swapChain)
		for (material in engine.materials.values) {
			material.initializeShader(swapChain)
		}
		createCommandBuffers(renderPass, engine)
		createSyncObjects()
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

	fun createImageView(image: Long, format: Int, aspectFlags: Int): Long {
		MemoryStack.stackPush().use { stack ->
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

	fun createImgViews() {
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

	fun createCmdPool(engine: Rosella) {
		MemoryStack.stackPush().use { stack ->
			val queueFamilyIndices = findQueueFamilies(device.physicalDevice, engine)
			val poolInfo = VkCommandPoolCreateInfo.callocStack(stack)
			poolInfo.sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
			poolInfo.queueFamilyIndex(queueFamilyIndices.graphicsFamily!!)
			val pCommandPool = stack.mallocLong(1)
			vkCreateCommandPool(device.device, poolInfo, null, pCommandPool).ok()
			commandPool = pCommandPool[0]
		}
	}

	fun render(engine: Rosella) {
		MemoryStack.stackPush().use { stack ->
			val thisFrame = inFlightFrames!![currentFrame]
			vkWaitForFences(device.device, thisFrame.pFence(), true, Rosella.UINT64_MAX)
			val pImageIndex = stack.mallocInt(1)

			var vkResult: Int = KHRSwapchain.vkAcquireNextImageKHR(
				device.device,
				swapChain.swapChain,
				Rosella.UINT64_MAX,
				thisFrame.imageAvailableSemaphore(),
				VK_NULL_HANDLE,
				pImageIndex
			)

			if (vkResult == KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR) {
				recreateSwapChain(engine.window, engine.camera, engine)
				return
			}

			val imageIndex = pImageIndex[0]

			for (shader in engine.shaders.values) {
				shader.updateUbo(imageIndex, swapChain, engine)
			}

			if (imagesInFlight!!.containsKey(imageIndex)) {
				vkWaitForFences(device.device, imagesInFlight!![imageIndex]!!.fence(), true, Rosella.UINT64_MAX)
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
				.sType(KHRSwapchain.VK_STRUCTURE_TYPE_PRESENT_INFO_KHR)
				.pWaitSemaphores(thisFrame.pRenderFinishedSemaphore())
				.swapchainCount(1)
				.pSwapchains(stack.longs(swapChain.swapChain))
				.pImageIndices(pImageIndex)

			vkResult = KHRSwapchain.vkQueuePresentKHR(queues.presentQueue, presentInfo)

			if (vkResult == KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR || vkResult == KHRSwapchain.VK_SUBOPTIMAL_KHR || resizeFramebuffer) {
				resizeFramebuffer = false
				recreateSwapChain(engine.window, engine.camera, engine)
			} else if (vkResult != VK_SUCCESS) {
				throw RuntimeException("Failed to present swap chain image")
			}

			currentFrame = (currentFrame + 1) % Rosella.MAX_FRAMES_IN_FLIGHT
		}
	}

	fun recreateSwapChain(window: Window, camera: Camera, engine: Rosella) {
		MemoryStack.stackPush().use { stack ->
			val width = stack.ints(0)
			val height = stack.ints(0)
			while (width[0] == 0 && height[0] == 0) {
				GLFW.glfwGetFramebufferSize(window.windowPtr, width, height)
				GLFW.glfwWaitEvents()
			}
		}

		vkDeviceWaitIdle(device.device)
		freeSwapChain(engine)
		createSwapChain(engine)
		camera.createViewAndProj(swapChain)
	}

	fun freeSwapChain(engine: Rosella) {
		for (shaderPair in engine.shaders.values) {
			vkDestroyDescriptorPool(device.device, shaderPair.descriptorPool, null)
			shaderPair.free()
		}

		vkFreeCommandBuffers(device.device, commandPool, commandBuffers.asPointerBuffer())

		for (material in engine.materials.values) {
			material.free(device, engine)
		}

		// Free Depth Buffer
		depthBuffer.free(device)

		swapChain.swapChainFrameBuffers.forEach { framebuffer ->
			vkDestroyFramebuffer(
				device.device,
				framebuffer,
				null
			)
		}
		vkDestroyRenderPass(device.device, renderPass.renderPass, null)
		swapChain.swapChainImageViews.forEach { imageView -> vkDestroyImageView(device.device, imageView, null) }
	}

	private fun createSyncObjects() {
		inFlightFrames = ArrayList(Rosella.MAX_FRAMES_IN_FLIGHT)
		imagesInFlight = HashMap(swapChain.swapChainImages.size)

		MemoryStack.stackPush().use { stack ->
			val semaphoreInfo = VkSemaphoreCreateInfo.callocStack(stack)
			semaphoreInfo.sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO)
			val fenceInfo = VkFenceCreateInfo.callocStack(stack)
			fenceInfo.sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO)
			fenceInfo.flags(VK_FENCE_CREATE_SIGNALED_BIT)
			val pImageAvailableSemaphore = stack.mallocLong(1)
			val pRenderFinishedSemaphore = stack.mallocLong(1)
			val pFence = stack.mallocLong(1)
			for (i in 0 until Rosella.MAX_FRAMES_IN_FLIGHT) {
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
		this.resizeFramebuffer = true
	}

	private fun createFrameBuffers() {
		swapChain.swapChainFrameBuffers = ArrayList(swapChain.swapChainImageViews.size)
		MemoryStack.stackPush().use { stack ->
			val attachments = stack.longs(VK_NULL_HANDLE, depthBuffer.depthImageView)
			val pFramebuffer = stack.mallocLong(1)
			val framebufferInfo = VkFramebufferCreateInfo.callocStack(stack)
				.sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO)
				.renderPass(renderPass.renderPass)
				.width(swapChain.swapChainExtent!!.width())
				.height(swapChain.swapChainExtent!!.height())
				.layers(1)
			for (imageView in swapChain.swapChainImageViews) {
				attachments.put(0, imageView)
				framebufferInfo.pAttachments(attachments)
				vkCreateFramebuffer(device.device, framebufferInfo, null, pFramebuffer).ok()
				swapChain.swapChainFrameBuffers.add(pFramebuffer[0])
			}
		}
	}

	/**
	 * Create the Command Buffers
	 */
	fun createCommandBuffers(renderPass: RenderPass, engine: Rosella) {
		val commandBuffersCount: Int = swapChain.swapChainFrameBuffers.size

		commandBuffers = java.util.ArrayList(commandBuffersCount)

		MemoryStack.stackPush().use {
			// Allocate
			val allocInfo = VkCommandBufferAllocateInfo.callocStack(it)
				.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
				.commandPool(commandPool)
				.level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
				.commandBufferCount(commandBuffersCount)
			val pCommandBuffers = it.callocPointer(commandBuffersCount)
			vkAllocateCommandBuffers(device.device, allocInfo, pCommandBuffers).ok()

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
				.extent(swapChain.swapChainExtent!!)

			renderPassInfo.renderArea(renderArea)

			val clearValues = VkClearValue.callocStack(2, it)
			clearValues[0].color().float32(it.floats(0xef / 255f, 0x32 / 255f, 0x3d / 255f, 1.0f))
			clearValues[1].depthStencil().set(1.0f, 0)

			renderPassInfo.pClearValues(clearValues)

			for (i in 0 until commandBuffersCount) {
				val commandBuffer = commandBuffers[i]
				vkBeginCommandBuffer(commandBuffer, beginInfo).ok()
				renderPassInfo.framebuffer(swapChain.swapChainFrameBuffers[i])

				vkCmdBeginRenderPass(commandBuffer, renderPassInfo, VK_SUBPASS_CONTENTS_INLINE)
				run {
					for (model in engine.models) {
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
				}
				vkCmdEndRenderPass(commandBuffer)
				vkEndCommandBuffer(commandBuffer).ok()
			}
		}
	}

	/**
	 * Called after the vulkan device and instance have been initialized.
	 */
	fun initialize(engine: Rosella) {
		device = engine.device
		createCmdPool(engine)
		createSwapChain(engine)
	}
}