package me.hydos.rosella.core

import me.hydos.rosella.util.ok
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkAttachmentDescription
import org.lwjgl.vulkan.VkAttachmentReference
import org.lwjgl.vulkan.VkRenderPassCreateInfo
import org.lwjgl.vulkan.VkSubpassDescription
import java.nio.LongBuffer


class RenderPass(val device: Device, val swapchain: Swapchain) {
	var renderPass: Long = 0

	init {
		stackPush().use {
			val colorAttachment = VkAttachmentDescription.callocStack(1, it)
				.format(swapchain.swapChainImageFormat)
				.samples(VK_SAMPLE_COUNT_1_BIT)
				.loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
				.storeOp(VK_ATTACHMENT_STORE_OP_STORE)
				.stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
				.stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
				.initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
				.finalLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR)

			val colorAttachmentRef = VkAttachmentReference.callocStack(1, it)
				.attachment(0)
				.layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)

			val subpass = VkSubpassDescription.callocStack(1, it)
				.pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
				.colorAttachmentCount(1)
				.pColorAttachments(colorAttachmentRef)

			val renderPassInfo: VkRenderPassCreateInfo = VkRenderPassCreateInfo.callocStack(it)
				.sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO)
				.pAttachments(colorAttachment)
				.pSubpasses(subpass)

			val pRenderPass: LongBuffer = it.mallocLong(1)
			vkCreateRenderPass(device.device, renderPassInfo, null, pRenderPass).ok()
			renderPass = pRenderPass[0]
		}
	}
}

