package me.hydos.rosella.core.swapchain

import me.hydos.rosella.core.Device
import me.hydos.rosella.core.Rosella
import me.hydos.rosella.core.findQueueFamilies
import me.hydos.rosella.util.ok
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO
import org.lwjgl.vulkan.VK10.vkCreateCommandPool
import java.util.*


class CommandBuffers(
	val device: Device,
	val engine: Rosella
) {
	internal var commandPool: Long = 0
	internal var commandBuffers: List<VkCommandBuffer> = ArrayList<VkCommandBuffer>()

	init {
		/**
		 * Create the Command Pool
		 */
		stackPush().use { stack ->
			val queueFamilyIndices = findQueueFamilies(device.physicalDevice, engine)
			val poolInfo = VkCommandPoolCreateInfo.callocStack(stack)
			poolInfo.sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
			poolInfo.queueFamilyIndex(queueFamilyIndices.graphicsFamily!!)
			val pCommandPool = stack.mallocLong(1)
			vkCreateCommandPool(device.device, poolInfo, null, pCommandPool).ok()
			commandPool = pCommandPool[0]
		}
	}

	fun createCommandBuffers(
		swapchain: Swapchain,
		renderPass: RenderPass,
		pipeline: GfxPipeline
	) {
		/**
		 * Create the Command Buffers
		 */
		val commandBuffersCount: Int = swapchain.swapChainFramebuffers.size

		commandBuffers = ArrayList(commandBuffersCount)

		stackPush().use { stack ->
			// Allocate
			val allocInfo = VkCommandBufferAllocateInfo.callocStack(stack)
			allocInfo.sType(VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
			allocInfo.commandPool(commandPool)
			allocInfo.level(VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY)
			allocInfo.commandBufferCount(commandBuffersCount)
			val pCommandBuffers = stack.mallocPointer(commandBuffersCount)
			if (VK10.vkAllocateCommandBuffers(device.device, allocInfo, pCommandBuffers) != VK10.VK_SUCCESS) {
				throw RuntimeException("Failed to allocate command buffers")
			}

			for (i in 0 until commandBuffersCount) {
				(commandBuffers as ArrayList<VkCommandBuffer>).add(VkCommandBuffer(pCommandBuffers[i], device.device))
			}
			val beginInfo = VkCommandBufferBeginInfo.callocStack(stack)
				.sType(VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)

			val renderPassInfo = VkRenderPassBeginInfo.callocStack(stack)
				.sType(VK10.VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
				.renderPass(renderPass.renderPass)

			val renderArea = VkRect2D.callocStack(stack)
				.offset(VkOffset2D.callocStack(stack).set(0, 0))
				.extent(swapchain.swapChainExtent!!)

			renderPassInfo.renderArea(renderArea)

			val clearValues = VkClearValue.callocStack(1, stack)
			clearValues.color().float32(stack.floats(0xef / 255f, 0x32 / 255f, 0x3d / 255f, 1.0f))

			renderPassInfo.pClearValues(clearValues)

			for (i in 0 until commandBuffersCount) {
				val commandBuffer = commandBuffers[i]
				VK10.vkBeginCommandBuffer(commandBuffer, beginInfo).ok()
				renderPassInfo.framebuffer(swapchain.swapChainFramebuffers[i])

				// Draw stuff
				VK10.vkCmdBeginRenderPass(commandBuffer, renderPassInfo, VK10.VK_SUBPASS_CONTENTS_INLINE)
				run {
					VK10.vkCmdBindPipeline(
						commandBuffer,
						VK10.VK_PIPELINE_BIND_POINT_GRAPHICS,
						pipeline.graphicsPipeline
					)
					val offsets = stack.longs(0)

					val vertexBuffers = stack.longs(engine.model.vertexBuffer)
					VK10.vkCmdBindVertexBuffers(commandBuffer, 0, vertexBuffers, offsets)
					VK10.vkCmdDraw(commandBuffer, engine.model.vertices.size, 1, 0, 0)
				}
				VK10.vkCmdEndRenderPass(commandBuffer)
				VK10.vkEndCommandBuffer(commandBuffer).ok()
			}
		}
	}
}