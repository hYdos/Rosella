/**
 * This file is for accessing vulkan indirectly. it manages structs so engine code can look better.
 */
package me.hydos.cell

import me.hydos.rosella.device.Device
import me.hydos.rosella.swapchain.RenderPass
import me.hydos.rosella.util.ok
import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo
import org.lwjgl.vulkan.VkCommandBufferBeginInfo
import org.lwjgl.vulkan.VkRenderPassBeginInfo

fun allocateCmdBuffers(
	stack: MemoryStack,
	device: Device,
	commandPool: Long,
	commandBuffersCount: Int,
	level: Int = VK_COMMAND_BUFFER_LEVEL_PRIMARY
): PointerBuffer {
	val allocInfo = VkCommandBufferAllocateInfo.callocStack(stack)
		.sType(VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
		.commandPool(commandPool)
		.level(level)
		.commandBufferCount(commandBuffersCount)
	val pCommandBuffers = stack.callocPointer(commandBuffersCount)
	VK10.vkAllocateCommandBuffers(device.device, allocInfo, pCommandBuffers).ok()
	return pCommandBuffers
}

fun createBeginInfo(stack: MemoryStack): VkCommandBufferBeginInfo {
	return VkCommandBufferBeginInfo.callocStack(stack)
		.sType(VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
}

fun createRenderPassInfo(stack: MemoryStack, renderPass: RenderPass): VkRenderPassBeginInfo {
	return VkRenderPassBeginInfo.callocStack(stack)
		.sType(VK10.VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
		.renderPass(renderPass.renderPass)
}