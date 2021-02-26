package me.hydos.rosella.model

import me.hydos.rosella.core.Device
import me.hydos.rosella.core.Rosella
import me.hydos.rosella.util.createBuffer
import me.hydos.rosella.util.findMemoryType
import me.hydos.rosella.util.memcpy
import me.hydos.rosella.util.ok
import org.joml.Vector2f
import org.joml.Vector3f
import org.lwjgl.PointerBuffer
import org.lwjgl.stb.STBImage.*
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*
import java.lang.ClassLoader.getSystemClassLoader
import java.nio.ByteBuffer
import java.nio.LongBuffer


class Model {
	private val vertices = arrayOf(
		Vertex(Vector2f(-0.5f, -0.5f), Vector3f(1.0f, 0.0f, 0.0f)),
		Vertex(Vector2f(0.5f, -0.5f), Vector3f(0.0f, 1.0f, 0.0f)),
		Vertex(Vector2f(0.5f, 0.5f), Vector3f(0.0f, 0.0f, 1.0f)),
		Vertex(Vector2f(-0.5f, 0.5f), Vector3f(1.0f, 1.0f, 1.0f))
	)

	internal val indices = shortArrayOf(
		0, 1, 2, 2, 3, 0
	)

	var vertexBuffer: Long = 0
	var vertexBufferMemory: Long = 0

	private var textureImage: Long = 0
	private var textureImageMemory: Long = 0

	var indexBuffer: Long = 0
	var indexBufferMemory: Long = 0

	fun createVertexBuffer(device: Device, rosella: Rosella) {
		stackPush().use { stack ->
			val bufferSize: Int = Vertex.SIZEOF * vertices.size
			val pBuffer = stack.mallocLong(1)
			val pBufferMemory = stack.mallocLong(1)
			createBuffer(
				bufferSize,
				VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
				VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
				pBuffer,
				pBufferMemory,
				device
			)
			val stagingBuffer = pBuffer[0]
			val stagingBufferMemory = pBufferMemory[0]
			val data = stack.mallocPointer(1)
			vkMapMemory(device.device, stagingBufferMemory, 0, bufferSize.toLong(), 0, data)
			run { memcpy(data.getByteBuffer(0, bufferSize), vertices) }
			vkUnmapMemory(device.device, stagingBufferMemory)
			createBuffer(
				bufferSize,
				VK_BUFFER_USAGE_TRANSFER_DST_BIT or VK_BUFFER_USAGE_VERTEX_BUFFER_BIT,
				VK_MEMORY_HEAP_DEVICE_LOCAL_BIT,
				pBuffer,
				pBufferMemory,
				device
			)
			vertexBuffer = pBuffer[0]
			vertexBufferMemory = pBufferMemory[0]
			copyBuffer(stagingBuffer, vertexBuffer, bufferSize, rosella, device)
			vkDestroyBuffer(device.device, stagingBuffer, null)
			vkFreeMemory(device.device, stagingBufferMemory, null)
		}
	}


	internal fun createIndexBuffer(device: Device, engine: Rosella) {
		stackPush().use { stack ->
			val bufferSize: Long = (java.lang.Short.BYTES * indices.size).toLong()
			val pBuffer = stack.mallocLong(1)
			val pBufferMemory = stack.mallocLong(1)
			createBuffer(
				bufferSize.toInt(),
				VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
				VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
				pBuffer,
				pBufferMemory,
				device
			)
			val stagingBuffer = pBuffer[0]
			val stagingBufferMemory = pBufferMemory[0]
			val data = stack.mallocPointer(1)
			vkMapMemory(device.device, stagingBufferMemory, 0, bufferSize, 0, data)
			run { memcpy(data.getByteBuffer(0, bufferSize.toInt()), indices) }
			vkUnmapMemory(device.device, stagingBufferMemory)
			createBuffer(
				bufferSize.toInt(),
				VK_BUFFER_USAGE_TRANSFER_DST_BIT or VK_BUFFER_USAGE_INDEX_BUFFER_BIT,
				VK_MEMORY_HEAP_DEVICE_LOCAL_BIT,
				pBuffer,
				pBufferMemory,
				device
			)
			indexBuffer = pBuffer[0]
			indexBufferMemory = pBufferMemory[0]
			copyBuffer(stagingBuffer, indexBuffer, bufferSize.toInt(), engine, device)
			vkDestroyBuffer(device.device, stagingBuffer, null)
			vkFreeMemory(device.device, stagingBufferMemory, null)
		}
	}

	private fun copyBuffer(srcBuffer: Long, dstBuffer: Long, size: Int, engine: Rosella, device: Device) {
		stackPush().use { stack ->
			val pCommandBuffer = stack.mallocPointer(1)
			val commandBuffer = beginCmdBuffer(stack, engine, device, pCommandBuffer)
			run {
				val copyRegion = VkBufferCopy.callocStack(1, stack)
				copyRegion.size(size.toLong())
				vkCmdCopyBuffer(commandBuffer, srcBuffer, dstBuffer, copyRegion)
			}
			vkEndCommandBuffer(commandBuffer)
			val submitInfo = VkSubmitInfo.callocStack(stack)
				.sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
				.pCommandBuffers(pCommandBuffer)
			vkQueueSubmit(engine.graphicsQueue, submitInfo, VK_NULL_HANDLE).ok()
			vkQueueWaitIdle(engine.graphicsQueue)
			vkFreeCommandBuffers(device.device, engine.commandBuffers.commandPool, pCommandBuffer)
		}
	}

	private fun beginCmdBuffer(
		stack: MemoryStack,
		engine: Rosella,
		device: Device,
		pCommandBuffer: PointerBuffer
	): VkCommandBuffer {
		val allocInfo = VkCommandBufferAllocateInfo.callocStack(stack)
			.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
			.level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
			.commandPool(engine.commandBuffers.commandPool)
			.commandBufferCount(1)
		vkAllocateCommandBuffers(device.device, allocInfo, pCommandBuffer)
		val commandBuffer = VkCommandBuffer(pCommandBuffer[0], device.device)
		val beginInfo = VkCommandBufferBeginInfo.callocStack(stack)
			.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
			.flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT)
		vkBeginCommandBuffer(commandBuffer, beginInfo)
		return commandBuffer
	}

	fun destroy(device: Device) {
		vkDestroyBuffer(device.device, vertexBuffer, null)
		vkFreeMemory(device.device, vertexBufferMemory, null)

		vkDestroyBuffer(device.device, indexBuffer, null)
		vkFreeMemory(device.device, indexBufferMemory, null)
	}

	fun create(device: Device, engine: Rosella) {
		createVertexBuffer(device, engine)
		createIndexBuffer(device, engine)
		createTextureImage(device, engine)
	}

	private fun createTextureImage(device: Device, engine: Rosella) {
		stackPush().use { stack ->
			val filename =
				getSystemClassLoader().getResource("textures/texture.jpg").toExternalForm().replace("file:", "")
			val pWidth = stack.mallocInt(1)
			val pHeight = stack.mallocInt(1)
			val pChannels = stack.mallocInt(1)
			val pixels: ByteBuffer? = stbi_load(filename, pWidth, pHeight, pChannels, STBI_rgb_alpha)
			val imageSize =
				(pWidth[0] * pHeight[0] *  /*always 4 due to STBI_rgb_alpha*/pChannels[0]).toLong()
			if (pixels == null) {
				throw RuntimeException("Failed to load texture image $filename")
			}
			val pStagingBuffer = stack.mallocLong(1)
			val pStagingBufferMemory = stack.mallocLong(1)
			createBuffer(
				imageSize.toInt(),
				VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
				VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
				pStagingBuffer,
				pStagingBufferMemory,
				device
			)
			val data = stack.mallocPointer(1)
			vkMapMemory(device.device, pStagingBufferMemory[0], 0, imageSize, 0, data)
			run { memcpy(data.getByteBuffer(0, imageSize.toInt()), pixels, imageSize) }
			vkUnmapMemory(device.device, pStagingBufferMemory[0])
			stbi_image_free(pixels)
			val pTextureImage = stack.mallocLong(1)
			val pTextureImageMemory = stack.mallocLong(1)
			createImage(
				pWidth[0], pHeight[0],
				VK_FORMAT_R8G8B8A8_SRGB, VK_IMAGE_TILING_OPTIMAL,
				VK_IMAGE_USAGE_TRANSFER_DST_BIT or VK_IMAGE_USAGE_SAMPLED_BIT,
				VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
				pTextureImage,
				pTextureImageMemory,
				device
			)
			textureImage = pTextureImage[0]
			textureImageMemory = pTextureImageMemory[0]
			transitionImageLayout(
				textureImage,
				VK_FORMAT_R8G8B8A8_SRGB,
				VK_IMAGE_LAYOUT_UNDEFINED,
				VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
				device,
				engine
			)
			copyBufferToImage(pStagingBuffer[0], textureImage, pWidth[0], pHeight[0], device, engine)
			transitionImageLayout(
				textureImage,
				VK_FORMAT_R8G8B8A8_SRGB,
				VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
				VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
				device,
				engine
			)
			vkDestroyBuffer(device.device, pStagingBuffer[0], null)
			vkFreeMemory(device.device, pStagingBufferMemory[0], null)
		}
	}

	private fun createImage(
		width: Int, height: Int, format: Int, tiling: Int, usage: Int, memProperties: Int,
		pTextureImage: LongBuffer, pTextureImageMemory: LongBuffer, device: Device
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
			if (vkCreateImage(device.device, imageInfo, null, pTextureImage) !== VK10.VK_SUCCESS) {
				throw RuntimeException("Failed to create image")
			}
			val memRequirements = VkMemoryRequirements.mallocStack(stack)
			vkGetImageMemoryRequirements(device.device, pTextureImage[0], memRequirements)
			val allocInfo = VkMemoryAllocateInfo.callocStack(stack)
				.sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
				.allocationSize(memRequirements.size())
				.memoryTypeIndex(findMemoryType(memRequirements.memoryTypeBits(), memProperties, device))
			if (vkAllocateMemory(device.device, allocInfo, null, pTextureImageMemory) !== VK10.VK_SUCCESS) {
				throw RuntimeException("Failed to allocate image memory")
			}
			vkBindImageMemory(device.device, pTextureImage[0], pTextureImageMemory[0], 0)
		}
	}

	private fun transitionImageLayout(
		image: Long,
		format: Int,
		oldLayout: Int,
		newLayout: Int,
		device: Device,
		engine: Rosella
	) {
		stackPush().use { stack ->
			val barrier = VkImageMemoryBarrier.callocStack(1, stack)
				.sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
				.oldLayout(oldLayout)
				.newLayout(newLayout)
				.srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
				.dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
				.image(image)
			barrier.subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
			barrier.subresourceRange().baseMipLevel(0)
			barrier.subresourceRange().levelCount(1)
			barrier.subresourceRange().baseArrayLayer(0)
			barrier.subresourceRange().layerCount(1)
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
			} else {
				throw IllegalArgumentException("Unsupported layout transition")
			}
			val commandBuffer: VkCommandBuffer = beginSingleTimeCommands(device, engine)
			vkCmdPipelineBarrier(
				commandBuffer,
				sourceStage, destinationStage,
				0,
				null,
				null,
				barrier
			)
			endSingleTimeCommands(commandBuffer, device, engine)
		}
	}

	private fun copyBufferToImage(buffer: Long, image: Long, width: Int, height: Int, device: Device, engine: Rosella) {
		stackPush().use { stack ->
			val commandBuffer: VkCommandBuffer = beginSingleTimeCommands(device, engine)
			val region = VkBufferImageCopy.callocStack(1, stack)
				.bufferOffset(0)
				.bufferRowLength(0)
				.bufferImageHeight(0)
			region.imageSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
			region.imageSubresource().mipLevel(0)
			region.imageSubresource().baseArrayLayer(0)
			region.imageSubresource().layerCount(1)
			region.imageOffset()[0, 0] = 0
			region.imageExtent(VkExtent3D.callocStack(stack).set(width, height, 1))
			vkCmdCopyBufferToImage(commandBuffer, buffer, image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region)
			endSingleTimeCommands(commandBuffer, device, engine)
		}
	}

	private fun memcpy(dst: ByteBuffer, src: ByteBuffer, size: Long) {
		src.limit(size.toInt())
		dst.put(src)
		src.limit(src.capacity()).rewind()
	}

	private fun createBuffer(
		size: Long,
		usage: Int,
		properties: Int,
		pBuffer: LongBuffer,
		pBufferMemory: LongBuffer,
		device: Device
	) {
		stackPush().use { stack ->
			val bufferInfo = VkBufferCreateInfo.callocStack(stack)
				.sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
				.size(size)
				.usage(usage)
				.sharingMode(VK_SHARING_MODE_EXCLUSIVE)
			vkCreateBuffer(device.device, bufferInfo, null, pBuffer).ok()
			val memRequirements = VkMemoryRequirements.mallocStack(stack)
			vkGetBufferMemoryRequirements(device.device, pBuffer[0], memRequirements)
			val allocInfo = VkMemoryAllocateInfo.callocStack(stack)
				.sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
				.allocationSize(memRequirements.size())
				.memoryTypeIndex(findMemoryType(memRequirements.memoryTypeBits(), properties, device))
			vkAllocateMemory(device.device, allocInfo, null, pBufferMemory).ok()
			vkBindBufferMemory(device.device, pBuffer[0], pBufferMemory[0], 0)
		}
	}

	private fun beginSingleTimeCommands(device: Device, engine: Rosella): VkCommandBuffer {
		stackPush().use { stack ->
			val pCommandBuffer = stack.mallocPointer(1)
			return beginCmdBuffer(stack, engine, device, pCommandBuffer)
		}
	}

	private fun endSingleTimeCommands(commandBuffer: VkCommandBuffer, device: Device, engine: Rosella) {
		stackPush().use { stack ->
			vkEndCommandBuffer(commandBuffer)
			val submitInfo = VkSubmitInfo.callocStack(1, stack)
				.sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
				.pCommandBuffers(stack.pointers(commandBuffer))
			vkQueueSubmit(engine.graphicsQueue, submitInfo, VK_NULL_HANDLE)
			vkQueueWaitIdle(engine.graphicsQueue)
			vkFreeCommandBuffers(device.device, engine.commandBuffers.commandPool, commandBuffer)
		}
	}
}