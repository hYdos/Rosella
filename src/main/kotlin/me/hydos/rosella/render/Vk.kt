/**
 * This file is for accessing vulkan indirectly. it manages structs so engine code can look better.
 */
@file:JvmName("VkKt")

package me.hydos.rosella.render

import me.hydos.rosella.device.QueueFamilyIndices
import me.hydos.rosella.device.VulkanDevice
import me.hydos.rosella.fbo.RenderPass
import me.hydos.rosella.memory.Memory
import me.hydos.rosella.render.renderer.Renderer
import me.hydos.rosella.render.swapchain.DepthBuffer
import me.hydos.rosella.render.swapchain.Swapchain
import me.hydos.rosella.render.texture.ImageRegion
import me.hydos.rosella.render.texture.Texture
import me.hydos.rosella.render.texture.TextureImage
import me.hydos.rosella.render.texture.UploadableImage
import me.hydos.rosella.render.util.ok
import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*
import java.nio.LongBuffer

fun allocateCmdBuffers(
    stack: MemoryStack,
    device: VulkanDevice,
    commandPool: Long,
    commandBuffersCount: Int,
    level: Int = VK_COMMAND_BUFFER_LEVEL_PRIMARY
): PointerBuffer {
    val allocInfo = VkCommandBufferAllocateInfo.callocStack(stack)
        .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
        .commandPool(commandPool)
        .level(level)
        .commandBufferCount(commandBuffersCount)
    val pCommandBuffers = stack.callocPointer(commandBuffersCount)
    vkAllocateCommandBuffers(device.rawDevice, allocInfo, pCommandBuffers).ok()
    return pCommandBuffers
}

fun createBeginInfo(stack: MemoryStack): VkCommandBufferBeginInfo {
    return VkCommandBufferBeginInfo.callocStack(stack)
        .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
}

fun createRenderPassInfo(stack: MemoryStack, renderPass: RenderPass): VkRenderPassBeginInfo {
    return VkRenderPassBeginInfo.callocStack(stack)
        .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
        .renderPass(renderPass.renderPass)
}

fun createRenderArea(stack: MemoryStack, x: Int = 0, y: Int = 0, swapchain: Swapchain): VkRect2D {
    return VkRect2D.callocStack(stack)
        .offset(VkOffset2D.callocStack(stack).set(x, y))
        .extent(swapchain.swapChainExtent)
}

fun createImageView(image: Long, format: Int, aspectFlags: Int, device: VulkanDevice): Long {
    MemoryStack.stackPush().use { stack ->
        val viewInfo = VkImageViewCreateInfo.callocStack(stack)
            .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
            .image(image)
            .viewType(VK_IMAGE_VIEW_TYPE_2D)
            .format(format)
        viewInfo.subresourceRange().aspectMask(aspectFlags)
            .baseMipLevel(0)
            .levelCount(1)
            .baseArrayLayer(0)
            .layerCount(1)

        val pImageView = stack.mallocLong(1)
        vkCreateImageView(device.rawDevice, viewInfo, null, pImageView).ok("Failed to create texture image view")
        return pImageView[0]
    }
}

fun createImgViews(swapchain: Swapchain, device: VulkanDevice) {
    swapchain.swapChainImageViews = ArrayList(swapchain.swapChainImages.size)
    for (swapChainImage in swapchain.swapChainImages) {
        swapchain.swapChainImageViews.add(
            createImageView(
                swapChainImage,
                swapchain.swapChainImageFormat,
                VK_IMAGE_ASPECT_COLOR_BIT,
                device
            )
        )
    }
}

fun createCmdPool(device: VulkanDevice, renderer: Renderer, surface: Long) {
    MemoryStack.stackPush().use { stack ->
        val queueFamilyIndices = findQueueFamilies(device, surface)
        val poolInfo = VkCommandPoolCreateInfo.callocStack(stack)
            .sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
            .queueFamilyIndex(queueFamilyIndices.graphicsFamily)
        val pCommandPool = stack.mallocLong(1)
        vkCreateCommandPool(device.rawDevice, poolInfo, null, pCommandPool).ok()
        renderer.commandPool = pCommandPool[0]
    }
}

fun createClearValues(
    stack: MemoryStack,
    r: Float = 0f,
    g: Float = 0f,
    b: Float = 0f,
    depth: Float = 1.0f,
    stencil: Int = 0
): VkClearValue.Buffer {
    val clearValues = VkClearValue.callocStack(2, stack)
    clearValues[0].color().float32(stack.floats(r, g, b, 1.0f))
    clearValues[1].depthStencil().set(depth, stencil)
    return clearValues
}

fun beginSingleTimeCommands(renderer: Renderer, device: VulkanDevice): VkCommandBuffer {
    MemoryStack.stackPush().use { stack ->
        val pCommandBuffer = stack.mallocPointer(1)
        return renderer.beginCmdBuffer(stack, pCommandBuffer, device)
    }
}

fun endSingleTimeCommands(commandBuffer: VkCommandBuffer, device: VulkanDevice, renderer: Renderer) {
    MemoryStack.stackPush().use { stack ->
        vkEndCommandBuffer(commandBuffer)
        val submitInfo = VkSubmitInfo.callocStack(1, stack)
            .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
            .pCommandBuffers(stack.pointers(commandBuffer))
        renderer.queues.graphicsQueue.vkQueueSubmit(submitInfo, VK_NULL_HANDLE)
        renderer.queues.graphicsQueue.vkQueueWaitIdle()
        vkFreeCommandBuffers(device.rawDevice, renderer.commandPool, commandBuffer)
    }
}

fun findQueueFamilies(device: VkDevice, surface: Long): QueueFamilyIndices {
    return findQueueFamilies(device.physicalDevice, surface)
}

fun findQueueFamilies(device: VulkanDevice, surface: Long): QueueFamilyIndices {
    return findQueueFamilies(device.physicalDevice, surface)
}

fun findQueueFamilies(device: VkPhysicalDevice, surface: Long): QueueFamilyIndices {
    MemoryStack.stackPush().use { stack ->
        val indices = QueueFamilyIndices()

        val queueFamilyCount = stack.ints(0)
        vkGetPhysicalDeviceQueueFamilyProperties(device, queueFamilyCount, null)

        val queueFamilies = VkQueueFamilyProperties.mallocStack(queueFamilyCount[0], stack)
        vkGetPhysicalDeviceQueueFamilyProperties(device, queueFamilyCount, queueFamilies)

        val presentSupport = stack.ints(VK_FALSE)

        var i = 0
        while (i < queueFamilies.capacity() || !indices.isComplete) {
            if (queueFamilies[i].queueFlags() and VK_QUEUE_GRAPHICS_BIT != 0) {
                indices.graphicsFamily = i
            }
            KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR(device, i, surface, presentSupport)
            if (presentSupport.get(0) == VK_TRUE) {
                indices.presentFamily = i
            }
            i++
        }
        return indices
    }
}

fun findMemoryType(typeFilter: Int, properties: Int, device: VulkanDevice): Int {
    val memProperties = VkPhysicalDeviceMemoryProperties.mallocStack()
    vkGetPhysicalDeviceMemoryProperties(device.physicalDevice, memProperties)
    for (i in 0 until memProperties.memoryTypeCount()) {
        if (typeFilter and (1 shl i) != 0 && memProperties.memoryTypes(i)
                .propertyFlags() and properties == properties
        ) {
            return i
        }
    }
    error("Failed to find suitable memory type")
}

fun createTextureImageView(device: VulkanDevice, imgFormat: Int, textureImage: Long): Long {
    return createImageView(
        textureImage,
        imgFormat,
        VK_IMAGE_ASPECT_COLOR_BIT,
        device
    )
}

fun createImage(
    width: Int, height: Int, format: Int, tiling: Int, usage: Int, memProperties: Int,
    pTextureImage: LongBuffer, pTextureImageMemory: LongBuffer, device: VulkanDevice
) {
    MemoryStack.stackPush().use { stack ->
        val imageInfo = VkImageCreateInfo.callocStack(stack)
            .sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
            .imageType(VK_IMAGE_TYPE_2D)
        imageInfo.extent()
            .width(width)
            .height(height)
            .depth(1)
        imageInfo
            .mipLevels(1)
            .arrayLayers(1)
            .format(format)
            .tiling(tiling)
            .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
            .usage(usage)
            .samples(VK_SAMPLE_COUNT_1_BIT)
            .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
        vkCreateImage(device.rawDevice, imageInfo, null, pTextureImage).ok("Failed to allocate image memory")
        val memRequirements = VkMemoryRequirements.mallocStack(stack)
        vkGetImageMemoryRequirements(device.rawDevice, pTextureImage[0], memRequirements)
        val allocInfo = VkMemoryAllocateInfo.callocStack(stack)
            .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
            .allocationSize(memRequirements.size())
            .memoryTypeIndex(findMemoryType(memRequirements.memoryTypeBits(), memProperties, device))
        vkAllocateMemory(device.rawDevice, allocInfo, null, pTextureImageMemory).ok("Failed to allocate image memory")
        vkBindImageMemory(device.rawDevice, pTextureImage[0], pTextureImageMemory[0], 0)
    }
}

fun transitionImageLayout(
    renderer: Renderer,
    device: VulkanDevice,
    depthBuffer: DepthBuffer,
    image: Long,
    format: Int,
    oldLayout: Int,
    newLayout: Int
) {
    MemoryStack.stackPush().use { stack ->
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


        if (newLayout == VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL) {
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
                .dstAccessMask(VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT or VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT)

            sourceStage = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT
            destinationStage = VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT

        } else if (oldLayout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL && newLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {

            barrier.srcAccessMask(VK_ACCESS_SHADER_READ_BIT)
                .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)

            sourceStage = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT
            destinationStage = VK_PIPELINE_STAGE_TRANSFER_BIT

        } else {
            throw IllegalArgumentException("Unsupported layout transition")
        }
        val commandBuffer: VkCommandBuffer = beginSingleTimeCommands(renderer, device)
        vkCmdPipelineBarrier(
            commandBuffer,
            sourceStage, destinationStage,
            0,
            null,
            null,
            barrier
        )
        endSingleTimeCommands(commandBuffer, device, renderer)
    }
}

fun createTextureImage(
    renderer: Renderer,
    device: VulkanDevice,
    width: Int,
    height: Int,
    imgFormat: Int,
    textureImage: TextureImage
) {
    MemoryStack.stackPush().use { stack ->

        val pTextureImage = stack.mallocLong(1)
        val pTextureImageMemory = stack.mallocLong(1)
        createImage(
            width, height,
            imgFormat, VK_IMAGE_TILING_OPTIMAL,
            VK_IMAGE_USAGE_TRANSFER_DST_BIT or VK_IMAGE_USAGE_SAMPLED_BIT,
            VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
            pTextureImage,
            pTextureImageMemory,
            device
        )
        textureImage.textureImage = pTextureImage[0]
        textureImage.textureImageMemory = pTextureImageMemory[0]


        transitionImageLayout(
            renderer,
            device,
            renderer.depthBuffer,
            textureImage.textureImage,
            imgFormat,
            VK_IMAGE_LAYOUT_UNDEFINED,
            VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL
        )
    }
}

fun copyToTexture(
    renderer: Renderer,
    device: VulkanDevice,
    memory: Memory,
    image: UploadableImage,
    srcRegion: ImageRegion,
    dstRegion: ImageRegion,
    texture: Texture
) {
    MemoryStack.stackPush().use { stack ->
        val pBuffer = stack.mallocLong(1)
        val stagingBuf = memory.createStagingBuf(
            image.getSize(),
            pBuffer,
            stack
        ) { data ->
            val pixels = image.getPixels()!!
            val newData = data.getByteBuffer(0, pixels.limit())
            newData.put(0, pixels, 0, pixels.limit())
        }

        copyBufferToImage(
            renderer,
            device,
            stagingBuf.buffer(),
            texture.textureImage.textureImage,
            image.getWidth(),
            image.getHeight(),
            srcRegion.xOffset,
            srcRegion.yOffset,
            image.getFormat().pixelSize,
            dstRegion.width,
            dstRegion.height,
            dstRegion.xOffset,
            dstRegion.yOffset
        )

        stagingBuf.free(device, memory)
    }
}

fun copyBufferToImage(
    renderer: Renderer,
    device: VulkanDevice,
    buffer: Long,
    image: Long,
    srcImageWidth: Int,
    srcImageHeight: Int,
    srcXOffset: Int,
    srcYOffset: Int,
    srcPixelSize: Int,
    dstRegionWidth: Int,
    dstRegionHeight: Int,
    dstXOffset: Int,
    dstYOffset: Int
) {
    MemoryStack.stackPush().use { stack ->
        val region = VkBufferImageCopy.callocStack(1, stack)
            .bufferOffset((((srcYOffset * srcImageWidth) + srcXOffset) * srcPixelSize).toLong())
            .bufferRowLength(srcImageWidth)
            .bufferImageHeight(srcImageHeight)
        region.imageOffset().set(dstXOffset, dstYOffset, 0)
        region.imageSubresource()
            .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
            .mipLevel(0)
            .baseArrayLayer(0)
            .layerCount(1)
        region.imageExtent().set(dstRegionWidth, dstRegionHeight, 1)

        val commandBuffer: VkCommandBuffer = beginSingleTimeCommands(renderer, device)
        vkCmdCopyBufferToImage(commandBuffer, buffer, image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region)
        endSingleTimeCommands(commandBuffer, device, renderer)
    }


}
