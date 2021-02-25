package me.hydos.rosella.model

import me.hydos.rosella.core.Device
import me.hydos.rosella.core.Rosella
import me.hydos.rosella.texture.*
import me.hydos.rosella.util.createBuffer
import me.hydos.rosella.util.memcpy
import org.joml.Vector2f
import org.joml.Vector3f
import org.lwjgl.stb.STBImage.*
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkBufferCopy
import org.lwjgl.vulkan.VkCommandBuffer
import java.nio.ByteBuffer


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

	private var textureImage: Long = 0
	private var textureImageMemory: Long = 0

	var vertexBuffer: Long = 0
	var vertexBufferMemory: Long = 0

	var indexBuffer: Long = 0
	var indexBufferMemory: Long = 0

	internal fun createTexture(device: Device, engine: Rosella, texture: String) {
		stackPush().use { stack ->
			val pWidth = stack.mallocInt(1)
			val pHeight = stack.mallocInt(1)
			val pChannels = stack.mallocInt(1)
			val pixels: ByteBuffer? = stbi_load_from_memory(
				ioResourceToByteBuffer(texture, 8 * 1024),
				pWidth,
				pHeight,
				pChannels,
				STBI_rgb_alpha
			)
			val imageSize =
				(pWidth[0] * pHeight[0] * pChannels[0]).toLong()
			if (pixels == null) {
				throw RuntimeException("Failed to load texture $texture" + " Reason: " + stbi_failure_reason())
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
				engine
			)
			copyBufferToImage(pStagingBuffer[0], textureImage, pWidth[0], pHeight[0], engine)
			transitionImageLayout(
				textureImage,
				VK_FORMAT_R8G8B8A8_SRGB,
				VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
				VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
				engine
			)
			vkDestroyBuffer(device.device, pStagingBuffer[0], null)
			vkFreeMemory(device.device, pStagingBufferMemory[0], null)
		}
	}

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
			copyBuffer(stagingBuffer, vertexBuffer, bufferSize.toLong(), rosella, device)
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
			copyBuffer(stagingBuffer, indexBuffer, bufferSize.toInt().toLong(), engine, device)
			vkDestroyBuffer(device.device, stagingBuffer, null)
			vkFreeMemory(device.device, stagingBufferMemory, null)
		}
	}

	private fun copyBuffer(srcBuffer: Long, dstBuffer: Long, size: Long, engine: Rosella, device: Device) {
		stackPush().use { stack ->
			val commandBuffer: VkCommandBuffer = beginSingleTimeCommands(engine.commandBuffers, device)
			val copyRegion = VkBufferCopy.callocStack(1, stack)
			copyRegion.size(size)
			vkCmdCopyBuffer(commandBuffer, srcBuffer, dstBuffer, copyRegion)
			endSingleTimeCommands(commandBuffer, engine)
		}
	}

	fun destroy(device: Device) {
		vkDestroyImage(device.device, textureImage, null);
		vkFreeMemory(device.device, textureImageMemory, null);

		vkDestroyBuffer(device.device, vertexBuffer, null)
		vkFreeMemory(device.device, vertexBufferMemory, null)

		vkDestroyBuffer(device.device, indexBuffer, null)
		vkFreeMemory(device.device, indexBufferMemory, null)
	}
}