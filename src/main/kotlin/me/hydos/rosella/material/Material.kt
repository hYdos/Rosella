package me.hydos.rosella.material

import me.hydos.rosella.RenderPass
import me.hydos.rosella.Rosella
import me.hydos.rosella.device.Device
import me.hydos.rosella.model.Vertex
import me.hydos.rosella.resource.Resource
import me.hydos.rosella.shader.Shader
import me.hydos.rosella.shader.ShaderPair
import me.hydos.rosella.swapchain.SwapChain
import me.hydos.rosella.util.*
import org.joml.Vector3f
import org.lwjgl.PointerBuffer
import org.lwjgl.stb.STBImage
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.Pointer
import org.lwjgl.vulkan.*
import java.nio.ByteBuffer
import java.nio.LongBuffer

/**
 * A Material is like texture information, normal information, and all of those things which give an object character wrapped into one class.
 * similar to how unity works
 * guaranteed to change once and a while
 */
class Material(private val vertexShader: Resource, private val fragmentShader: Resource, private val texture: Resource) {

	var pipelineLayout: Long = 0
	var graphicsPipeline: Long = 0

	private var textureImage: Long = 0
	private var textureImageMemory: Long = 0

	lateinit var shaders: ShaderPair

	var textureImageView: Long = 0
	var textureSampler: Long = 0

	fun loadShaders(device: Device) {
		this.shaders = ShaderPair(Shader(vertexShader), Shader(fragmentShader), device)
	}

	fun loadTextures(device: Device, engine: Rosella) {
		createTextureImage(device, engine)
		createTextureImageView(engine)
		createTextureSampler(device)
	}

	/**
	 * The main rendering pipeline of this material
	 */
	fun createPipeline(
		device: Device,
		swapChain: SwapChain,
		renderPass: RenderPass,
		descriptorSetLayout: Long
	) {
		//TODO: optimise pipeline creation. could make it work better in some ways. i should write this stuff down
		MemoryStack.stackPush().use {
			val vertShaderSPIRV: SpirV = compileShaderFile(vertexShader, ShaderType.VERTEX_SHADER)
			val fragShaderSPIRV: SpirV = compileShaderFile(fragmentShader, ShaderType.FRAGMENT_SHADER)
			val vertShaderModule = createShader(vertShaderSPIRV.bytecode(), device)
			val fragShaderModule = createShader(fragShaderSPIRV.bytecode(), device)
			val entryPoint: ByteBuffer = it.UTF8("main")
			val shaderStages = VkPipelineShaderStageCreateInfo.callocStack(2, it)

			shaderStages[0]
				.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
				.stage(VK10.VK_SHADER_STAGE_VERTEX_BIT)
				.module(vertShaderModule)
				.pName(entryPoint)

			shaderStages[1]
				.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
				.stage(VK10.VK_SHADER_STAGE_FRAGMENT_BIT)
				.module(fragShaderModule)
				.pName(entryPoint)

			// Stages
			/**
			 * Vertex
			 */
			val vertexInputInfo: VkPipelineVertexInputStateCreateInfo =
				VkPipelineVertexInputStateCreateInfo.callocStack(it)
					.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
					.pVertexBindingDescriptions(Vertex.bindingDescription)
					.pVertexAttributeDescriptions(Vertex.attributeDescriptions)

			/**
			 * Assembly
			 */
			val inputAssembly: VkPipelineInputAssemblyStateCreateInfo =
				VkPipelineInputAssemblyStateCreateInfo.callocStack(it)
					.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
					.topology(VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
					.primitiveRestartEnable(false)

			/**
			 * Viewport
			 */
			val viewport = VkViewport.callocStack(1, it)
				.x(0.0f)
				.y(0.0f)
				.width(swapChain.swapChainExtent!!.width().toFloat())
				.height(swapChain.swapChainExtent!!.height().toFloat())
				.minDepth(0.0f)
				.maxDepth(1.0f)

			/**
			 * Scissor
			 */
			val scissor = VkRect2D.callocStack(1, it)
				.offset(VkOffset2D.callocStack(it).set(0, 0))
				.extent(swapChain.swapChainExtent!!)

			/**
			 * Viewport State
			 */
			val viewportState: VkPipelineViewportStateCreateInfo = VkPipelineViewportStateCreateInfo.callocStack(it)
				.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
				.pViewports(viewport)
				.pScissors(scissor)

			/**
			 * Rasterisation
			 */
			val rasterizer: VkPipelineRasterizationStateCreateInfo =
				VkPipelineRasterizationStateCreateInfo.callocStack(it)
					.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
					.depthClampEnable(false)
					.rasterizerDiscardEnable(false)
					.polygonMode(VK10.VK_POLYGON_MODE_FILL)
					.lineWidth(1.0f)
					.cullMode(VK10.VK_CULL_MODE_BACK_BIT)
					.frontFace(VK10.VK_FRONT_FACE_COUNTER_CLOCKWISE) //TODO: make the user be able to specify this
					.depthBiasEnable(false)

			/**
			 * Multisampling
			 */
			val multisampling: VkPipelineMultisampleStateCreateInfo =
				VkPipelineMultisampleStateCreateInfo.callocStack(it)
					.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
					.sampleShadingEnable(false)
					.rasterizationSamples(VK10.VK_SAMPLE_COUNT_1_BIT)

			val depthStencil: VkPipelineDepthStencilStateCreateInfo =
				VkPipelineDepthStencilStateCreateInfo.callocStack(it)
					.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO)
					.depthTestEnable(true)
					.depthWriteEnable(true)
					.depthCompareOp(VK10.VK_COMPARE_OP_LESS)
					.depthBoundsTestEnable(false)
					.minDepthBounds(0.0f)
					.maxDepthBounds(1.0f)
					.stencilTestEnable(false)

			/**
			 * Colour Blending
			 */
			val colorBlendAttachment = VkPipelineColorBlendAttachmentState.callocStack(1, it)
				.colorWriteMask(VK10.VK_COLOR_COMPONENT_R_BIT or VK10.VK_COLOR_COMPONENT_G_BIT or VK10.VK_COLOR_COMPONENT_B_BIT or VK10.VK_COLOR_COMPONENT_A_BIT)
				.blendEnable(false)

			val colorBlending: VkPipelineColorBlendStateCreateInfo =
				VkPipelineColorBlendStateCreateInfo.callocStack(it)
					.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
					.logicOpEnable(false)
					.logicOp(VK10.VK_LOGIC_OP_COPY)
					.pAttachments(colorBlendAttachment)
					.blendConstants(it.floats(0.0f, 0.0f, 0.0f, 0.0f))

			/**
			 * Create Push Constants
			 */
			val pushConstantRange = VkPushConstantRange.Buffer(it.bytes(1))
				.stageFlags(VK10.VK_SHADER_STAGE_VERTEX_BIT)
				.offset(0)
				.size(sizeof(Vector3f::class))

			/**
			 * Pipeline Layout Creation
			 */
			val pipelineLayoutInfo: VkPipelineLayoutCreateInfo = VkPipelineLayoutCreateInfo.callocStack(it)
				.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
				.pSetLayouts(it.longs(descriptorSetLayout))
				.pPushConstantRanges(pushConstantRange)

			val pPipelineLayout = it.longs(VK10.VK_NULL_HANDLE)
			VK10.vkCreatePipelineLayout(device.device, pipelineLayoutInfo, null, pPipelineLayout).ok()
			pipelineLayout = pPipelineLayout[0]

			/**
			 * Pipeline Creation
			 */
			val pipelineInfo = VkGraphicsPipelineCreateInfo.callocStack(1, it)
				.sType(VK10.VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
				.pStages(shaderStages)
				.pVertexInputState(vertexInputInfo)
				.pInputAssemblyState(inputAssembly)
				.pViewportState(viewportState)
				.pRasterizationState(rasterizer)
				.pMultisampleState(multisampling)
				.pDepthStencilState(depthStencil)
				.pColorBlendState(colorBlending)
				.layout(pipelineLayout)
				.renderPass(renderPass.renderPass)
				.subpass(0)
				.basePipelineHandle(VK10.VK_NULL_HANDLE)
				.basePipelineIndex(-1)

			val pGraphicsPipeline: LongBuffer = it.mallocLong(1)
			VK10.vkCreateGraphicsPipelines(device.device, VK10.VK_NULL_HANDLE, pipelineInfo, null, pGraphicsPipeline)
				.ok()
			graphicsPipeline = pGraphicsPipeline[0]

			/**
			 * Free Resources
			 */
			VK10.vkDestroyShaderModule(device.device, vertShaderModule, null)
			VK10.vkDestroyShaderModule(device.device, fragShaderModule, null)

			vertShaderSPIRV.free()
			fragShaderSPIRV.free()
		}
	}

	/**
	 * Create a Vulkan shader module. used during pipeline creation.
	 */
	private fun createShader(spirvCode: ByteBuffer, device: Device): Long {
		MemoryStack.stackPush().use { stack ->
			val createInfo = VkShaderModuleCreateInfo.callocStack(stack)
				.sType(VK10.VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
				.pCode(spirvCode)
			val pShaderModule = stack.mallocLong(1)
			VK10.vkCreateShaderModule(device.device, createInfo, null, pShaderModule).ok()
			return pShaderModule[0]
		}
	}

	private fun asPtrBuffer(list: List<Pointer>): PointerBuffer {
		val stack = MemoryStack.stackGet()
		val buffer = stack.mallocPointer(list.size)
		list.forEach { pointer: Pointer -> buffer.put(pointer) }
		return buffer.rewind()
	}

	fun free(device: Device, engine: Rosella) {
		VK10.vkFreeCommandBuffers(device.device, engine.commandPool, asPtrBuffer(engine.commandBuffers))
		VK10.vkDestroyPipeline(device.device, graphicsPipeline, null)
		VK10.vkDestroyPipelineLayout(device.device, pipelineLayout, null)
	}

	private fun createTextureSampler(device: Device) {
		MemoryStack.stackPush().use { stack ->
			val samplerInfo = VkSamplerCreateInfo.callocStack(stack)
				.sType(VK10.VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO)
				.magFilter(VK10.VK_FILTER_LINEAR)
				.minFilter(VK10.VK_FILTER_LINEAR)
				.addressModeU(VK10.VK_SAMPLER_ADDRESS_MODE_REPEAT)
				.addressModeV(VK10.VK_SAMPLER_ADDRESS_MODE_REPEAT)
				.addressModeW(VK10.VK_SAMPLER_ADDRESS_MODE_REPEAT)
				.anisotropyEnable(true)
				.maxAnisotropy(16.0f)
				.borderColor(VK10.VK_BORDER_COLOR_INT_OPAQUE_BLACK)
				.unnormalizedCoordinates(false)
				.compareEnable(false)
				.compareOp(VK10.VK_COMPARE_OP_ALWAYS)
				.mipmapMode(VK10.VK_SAMPLER_MIPMAP_MODE_LINEAR)
			val pTextureSampler = stack.mallocLong(1)
			if (VK10.vkCreateSampler(device.device, samplerInfo, null, pTextureSampler) !== VK10.VK_SUCCESS) {
				throw RuntimeException("Failed to create texture sampler")
			}
			textureSampler = pTextureSampler[0]
		}
	}

	private fun createTextureImageView(engine: Rosella) {
		textureImageView = engine.createImageView(textureImage,
			VK10.VK_FORMAT_R8G8B8A8_SRGB,
			VK10.VK_IMAGE_ASPECT_COLOR_BIT
		)
	}

	private fun createTextureImage(device: Device, engine: Rosella) {
		MemoryStack.stackPush().use { stack ->
			val file = texture.readAllBytes(true)
			val pWidth = stack.mallocInt(1)
			val pHeight = stack.mallocInt(1)
			val pChannels = stack.mallocInt(1)
			val pixels: ByteBuffer? = STBImage.stbi_load_from_memory(file, pWidth, pHeight, pChannels, STBImage.STBI_rgb_alpha)
			val imageSize = (pWidth[0] * pHeight[0] * 4).toLong()
			if (pixels == null) {
				throw RuntimeException("Failed to load texture image ${texture.identifier}")
			}
			val pStagingBuffer = stack.mallocLong(1)
			val pStagingBufferMemory = stack.mallocLong(1)
			createBuffer(
				imageSize.toInt(),
				VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
				VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
				pStagingBuffer,
				pStagingBufferMemory,
				device
			)
			val data = stack.mallocPointer(1)
			VK10.vkMapMemory(device.device, pStagingBufferMemory[0], 0, imageSize, 0, data)
			run { memcpy(data.getByteBuffer(0, imageSize.toInt()), pixels, imageSize) }
			VK10.vkUnmapMemory(device.device, pStagingBufferMemory[0])
			STBImage.stbi_image_free(pixels)
			val pTextureImage = stack.mallocLong(1)
			val pTextureImageMemory = stack.mallocLong(1)
			engine.createImage(
				pWidth[0], pHeight[0],
				VK10.VK_FORMAT_R8G8B8A8_SRGB, VK10.VK_IMAGE_TILING_OPTIMAL,
				VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT or VK10.VK_IMAGE_USAGE_SAMPLED_BIT,
				VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
				pTextureImage,
				pTextureImageMemory
			)
			textureImage = pTextureImage[0]
			textureImageMemory = pTextureImageMemory[0]
			engine.transitionImageLayout(
				textureImage,
				VK10.VK_FORMAT_R8G8B8A8_SRGB,
				VK10.VK_IMAGE_LAYOUT_UNDEFINED,
				VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL
			)
			copyBufferToImage(pStagingBuffer[0], textureImage, pWidth[0], pHeight[0], device, engine)
			engine.transitionImageLayout(
				textureImage,
				VK10.VK_FORMAT_R8G8B8A8_SRGB,
				VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
				VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
			)
			VK10.vkDestroyBuffer(device.device, pStagingBuffer[0], null)
			VK10.vkFreeMemory(device.device, pStagingBufferMemory[0], null)
		}
	}

	private fun copyBufferToImage(buffer: Long, image: Long, width: Int, height: Int, device: Device, engine: Rosella) {
		MemoryStack.stackPush().use { stack ->
			val commandBuffer: VkCommandBuffer = beginSingleTimeCommands(engine)
			val region = VkBufferImageCopy.callocStack(1, stack)
				.bufferOffset(0)
				.bufferRowLength(0)
				.bufferImageHeight(0)
			region.imageSubresource().aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
			region.imageSubresource().mipLevel(0)
			region.imageSubresource().baseArrayLayer(0)
			region.imageSubresource().layerCount(1)
			region.imageOffset()[0, 0] = 0
			region.imageExtent(VkExtent3D.callocStack(stack).set(width, height, 1))
			VK10.vkCmdCopyBufferToImage(commandBuffer, buffer, image, VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region)
			endSingleTimeCommands(commandBuffer, device, engine)
		}
	}

	private fun memcpy(dst: ByteBuffer, src: ByteBuffer, size: Long) {
		src.limit(size.toInt())
		dst.put(src)
		src.limit(src.capacity()).rewind()
	}

	fun beginSingleTimeCommands(engine: Rosella): VkCommandBuffer {
		MemoryStack.stackPush().use { stack ->
			val pCommandBuffer = stack.mallocPointer(1)
			return engine.beginCmdBuffer(stack, pCommandBuffer)
		}
	}

	fun endSingleTimeCommands(commandBuffer: VkCommandBuffer, device: Device, engine: Rosella) {
		MemoryStack.stackPush().use { stack ->
			VK10.vkEndCommandBuffer(commandBuffer)
			val submitInfo = VkSubmitInfo.callocStack(1, stack)
				.sType(VK10.VK_STRUCTURE_TYPE_SUBMIT_INFO)
				.pCommandBuffers(stack.pointers(commandBuffer))
			VK10.vkQueueSubmit(engine.queues.graphicsQueue, submitInfo, VK10.VK_NULL_HANDLE)
			VK10.vkQueueWaitIdle(engine.queues.graphicsQueue)
			VK10.vkFreeCommandBuffers(device.device, engine.commandPool, commandBuffer)
		}
	}
}
