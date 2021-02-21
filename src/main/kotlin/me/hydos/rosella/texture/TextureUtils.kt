package me.hydos.rosella.texture

import me.hydos.rosella.core.Device
import me.hydos.rosella.core.Rosella
import me.hydos.rosella.core.swapchain.CommandBuffers
import me.hydos.rosella.util.findMemoryType
import me.hydos.rosella.util.ok
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*
import java.nio.LongBuffer


fun createImage(
	width: Int, height: Int, format: Int, tiling: Int, usage: Int, memProperties: Int,
	pTextureImage: LongBuffer, pTextureImageMemory: LongBuffer, device: Device
) {
	stackPush().use { stack ->
		val imageInfo = VkImageCreateInfo.callocStack(stack)
			.sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
			.imageType(VK_IMAGE_TYPE_2D)
			.mipLevels(1)
			.arrayLayers(1)
			.format(format)
			.tiling(tiling)
			.initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
			.usage(usage)
			.samples(VK_SAMPLE_COUNT_1_BIT)
			.sharingMode(VK_SHARING_MODE_EXCLUSIVE)

		imageInfo.extent().width(width)
			.height(height)
			.depth(1)
		vkCreateImage(device.device, imageInfo, null, pTextureImage).ok()
		val memRequirements = VkMemoryRequirements.mallocStack(stack)
		vkGetImageMemoryRequirements(device.device, pTextureImage[0], memRequirements)
		val allocInfo = VkMemoryAllocateInfo.callocStack(stack)
			.sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
			.allocationSize(memRequirements.size())
			.memoryTypeIndex(findMemoryType(memRequirements.memoryTypeBits(), memProperties, device))
		vkAllocateMemory(device.device, allocInfo, null, pTextureImageMemory).ok()
		vkBindImageMemory(device.device, pTextureImage[0], pTextureImageMemory[0], 0)
	}
}

fun transitionImageLayout(image: Long, format: Int, oldLayout: Int, newLayout: Int, engine: Rosella) {
	stackPush().use { stack ->
		val sourceStage: Int
		val destinationStage: Int

		val barrier = VkImageMemoryBarrier.callocStack(1, stack)
			.sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
			.oldLayout(oldLayout)
			.newLayout(newLayout)
			.srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
			.dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
			.image(image)

		barrier.subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
			.baseMipLevel(0)
			.levelCount(1)
			.baseArrayLayer(0)
			.layerCount(1)

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
		} else {
			throw IllegalArgumentException("Unsupported layout transition")
		}
		val commandBuffer: VkCommandBuffer = beginSingleTimeCommands(engine.commandBuffers, engine.device)
		vkCmdPipelineBarrier(
			commandBuffer,
			sourceStage, destinationStage,
			0,
			null,
			null,
			barrier
		)
		endSingleTimeCommands(commandBuffer, engine)
	}
}

fun copyBufferToImage(buffer: Long, image: Long, width: Int, height: Int, engine: Rosella) {
	stackPush().use { stack ->
		val commandBuffer: VkCommandBuffer = beginSingleTimeCommands(engine.commandBuffers, engine.device)
		val region = VkBufferImageCopy.callocStack(1, stack)
			.bufferOffset(0)
			.bufferRowLength(0)
			.bufferImageHeight(0)
		region.imageSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
			.mipLevel(0)
			.baseArrayLayer(0)
			.layerCount(1)
		region.imageOffset()[0, 0] = 0
		region.imageExtent(VkExtent3D.callocStack(stack).set(width, height, 1))
		vkCmdCopyBufferToImage(commandBuffer, buffer, image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region)
		endSingleTimeCommands(commandBuffer, engine)
	}
}


private fun beginSingleTimeCommands(commandBuffers: CommandBuffers, device: Device): VkCommandBuffer {
	stackPush().use { stack ->
		val allocInfo = VkCommandBufferAllocateInfo.callocStack(stack)
		allocInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
		allocInfo.level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
		allocInfo.commandPool(commandBuffers.commandPool)
		allocInfo.commandBufferCount(1)
		val pCommandBuffer = stack.mallocPointer(1)
		vkAllocateCommandBuffers(device.device, allocInfo, pCommandBuffer)
		val commandBuffer = VkCommandBuffer(pCommandBuffer[0], device.device)
		val beginInfo = VkCommandBufferBeginInfo.callocStack(stack)
		beginInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
		beginInfo.flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT)
		vkBeginCommandBuffer(commandBuffer, beginInfo)
		return commandBuffer
	}
}

private fun endSingleTimeCommands(commandBuffer: VkCommandBuffer, engine: Rosella) {
	stackPush().use { stack ->
		vkEndCommandBuffer(commandBuffer)
		val submitInfo = VkSubmitInfo.callocStack(1, stack)
		submitInfo.sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
		submitInfo.pCommandBuffers(stack.pointers(commandBuffer))
		vkQueueSubmit(engine.graphicsQueue, submitInfo, VK_NULL_HANDLE)
		vkQueueWaitIdle(engine.graphicsQueue)
		vkFreeCommandBuffers(engine.device.device, engine.commandBuffers.commandPool, commandBuffer)
	}
}