package me.hydos.rosella.core.swapchain

import me.hydos.rosella.core.Device
import me.hydos.rosella.core.Rosella
import me.hydos.rosella.core.findQueueFamilies
import me.hydos.rosella.util.ok
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*
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
			allocInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
			allocInfo.commandPool(commandPool)
			allocInfo.level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
			allocInfo.commandBufferCount(commandBuffersCount)
			val pCommandBuffers = stack.mallocPointer(commandBuffersCount)
			if (vkAllocateCommandBuffers(device.device, allocInfo, pCommandBuffers) != VK_SUCCESS) {
				throw RuntimeException("Failed to allocate command buffers")
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

			val clearValues = VkClearValue.callocStack(2, stack)
			clearValues[0].color().float32(stack.floats(0xef / 255f, 0x32 / 255f, 0x3d / 255f, 1.0f))
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
						pipeline.graphicsPipeline
					)
					val offsets = stack.longs(0)

					val vertexBuffers = stack.longs(engine.model.vertexBuffer)
					vkCmdBindVertexBuffers(commandBuffer, 0, vertexBuffers, offsets)
					vkCmdBindIndexBuffer(commandBuffer, engine.model.indexBuffer, 0, VK_INDEX_TYPE_UINT16)
					vkCmdBindDescriptorSets(
						commandBuffer,
						VK_PIPELINE_BIND_POINT_GRAPHICS,
						pipeline.pipelineLayout,
						0,
						stack.longs(engine.descriptorSets[i]),
						null
					)

					vkCmdDrawIndexed(commandBuffer, engine.model.indices.size, 1, 0, 0, 0)
				}
				vkCmdEndRenderPass(commandBuffer)
				vkEndCommandBuffer(commandBuffer).ok()
			}
		}
	}
}