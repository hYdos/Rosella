package me.hydos.rosella.core

import me.hydos.rosella.util.ok
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*
import java.util.*


class CommandBuffers(
	val device: Device,
	val swapchain: Swapchain,
	renderPass: RenderPass,
	pipeline: GfxPipeline,
	engine: Rosella
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

		/**
		 * Create the Command Buffers
		 */
		val commandBuffersCount: Int = swapchain.swapChainFramebuffers.size

		commandBuffers = ArrayList(commandBuffersCount)

		stackPush().use { stack ->
			// Allocate
			val allocInfo = VkCommandBufferAllocateInfo.callocStack(stack)
			allocInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
			allocInfo.commandPool(commandPool)
			allocInfo.level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
			allocInfo.commandBufferCount(commandBuffersCount)
			val pCommandBuffers = stack.mallocPointer(commandBuffersCount)
			if (vkAllocateCommandBuffers(device.device, allocInfo, pCommandBuffers) != VK_SUCCESS) {
				throw RuntimeException("Fialed to allocate command buffers")
			}

			for (i in 0 until commandBuffersCount) {
				(commandBuffers as ArrayList<VkCommandBuffer>).add(VkCommandBuffer(pCommandBuffers[i], device.device))
			}
			val beginInfo = VkCommandBufferBeginInfo.callocStack(stack)
				.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)

			val renderPassInfo = VkRenderPassBeginInfo.callocStack(stack)
				.sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
				.renderPass(renderPass.renderPass)

			val renderArea = VkRect2D.callocStack(stack)
				.offset(VkOffset2D.callocStack(stack).set(0, 0))
				.extent(swapchain.swapChainExtent!!)

			renderPassInfo.renderArea(renderArea)

			val clearValues = VkClearValue.callocStack(1, stack)
			clearValues.color().float32(stack.floats(0.0f, 0.0f, 0.0f, 1.0f))

			renderPassInfo.pClearValues(clearValues)

			for (i in 0 until commandBuffersCount) {
				val commandBuffer = commandBuffers[i]
				if (vkBeginCommandBuffer(commandBuffer, beginInfo) != VK_SUCCESS) {
					throw RuntimeException("Failed to begin recording command buffer")
				}
				renderPassInfo.framebuffer(swapchain.swapChainFramebuffers[i])

				// Draw stuff
				vkCmdBeginRenderPass(commandBuffer, renderPassInfo, VK_SUBPASS_CONTENTS_INLINE)
				run {
					vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.graphicsPipeline)

					vkCmdDraw(commandBuffer, 3, 1, 0, 0)
				}
				vkCmdEndRenderPass(commandBuffer)
				vkEndCommandBuffer(commandBuffer).ok()
			}
		}
	}
}