package me.hydos.rosella.core

import me.hydos.rosella.core.device.Device
import me.hydos.rosella.core.swapchain.SwapChain
import me.hydos.rosella.model.Vertex
import me.hydos.rosella.model.ubo.ModelPushConstant
import me.hydos.rosella.model.ubo.ModelUbo
import me.hydos.rosella.util.*
import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.system.Pointer
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*
import java.nio.ByteBuffer
import java.nio.LongBuffer
import java.util.*


class Pipeline() {

	private var pipelineLayout: Long = 0
	private var graphicsPipeline: Long = 0

	fun createPipeline(
		device: Device,
		swapchain: SwapChain,
		renderPass: RenderPass,
		descriptorSetLayout: Long
	) {
		stackPush().use {
			val vertShaderSPIRV: SpirV = compileShaderFile("shaders/base.v.glsl", ShaderType.VERTEX_SHADER)
			val fragShaderSPIRV: SpirV = compileShaderFile("shaders/base.f.glsl", ShaderType.FRAGMENT_SHADER)
			val vertShaderModule = createShaderModule(vertShaderSPIRV.bytecode(), device)
			val fragShaderModule = createShaderModule(fragShaderSPIRV.bytecode(), device)
			val entryPoint: ByteBuffer = it.UTF8("main")
			val shaderStages = VkPipelineShaderStageCreateInfo.callocStack(2, it)

			val vertShaderStageInfo = shaderStages[0]
				.sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
				.stage(VK_SHADER_STAGE_VERTEX_BIT)
				.module(vertShaderModule)
				.pName(entryPoint)

			val fragShaderStageInfo = shaderStages[1]
				.sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
				.stage(VK_SHADER_STAGE_FRAGMENT_BIT)
				.module(fragShaderModule)
				.pName(entryPoint)

			// Stages
			/**
			 * Vertex
			 */
			val vertexInputInfo: VkPipelineVertexInputStateCreateInfo =
				VkPipelineVertexInputStateCreateInfo.callocStack(it)
					.sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
					.pVertexBindingDescriptions(Vertex.bindingDescription)
					.pVertexAttributeDescriptions(Vertex.attributeDescriptions)

			/**
			 * Assembly
			 */
			val inputAssembly: VkPipelineInputAssemblyStateCreateInfo =
				VkPipelineInputAssemblyStateCreateInfo.callocStack(it)
					.sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
					.topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
					.primitiveRestartEnable(false)

			/**
			 * Viewport
			 */
			val viewport = VkViewport.callocStack(1, it)
				.x(0.0f)
				.y(0.0f)
				.width(swapchain.swapChainExtent!!.width().toFloat())
				.height(swapchain.swapChainExtent!!.height().toFloat())
				.minDepth(0.0f)
				.maxDepth(1.0f)

			/**
			 * Scissor
			 */
			val scissor = VkRect2D.callocStack(1, it)
				.offset(VkOffset2D.callocStack(it).set(0, 0))
				.extent(swapchain.swapChainExtent!!)

			val viewportState: VkPipelineViewportStateCreateInfo = VkPipelineViewportStateCreateInfo.callocStack(it)
				.sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
				.pViewports(viewport)
				.pScissors(scissor)

			/**
			 * Rasterisation
			 */
			val rasterizer: VkPipelineRasterizationStateCreateInfo =
				VkPipelineRasterizationStateCreateInfo.callocStack(it)
					.sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
					.depthClampEnable(false)
					.rasterizerDiscardEnable(false)
					.polygonMode(VK_POLYGON_MODE_FILL)
					.lineWidth(1.0f)
					.cullMode(VK_CULL_MODE_BACK_BIT)
					.frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE) //TODO: make the user be able to specify this
					.depthBiasEnable(false)

			/**
			 * Multisampling
			 */
			val multisampling: VkPipelineMultisampleStateCreateInfo =
				VkPipelineMultisampleStateCreateInfo.callocStack(it)
					.sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
					.sampleShadingEnable(false)
					.rasterizationSamples(VK_SAMPLE_COUNT_1_BIT)

			val depthStencil: VkPipelineDepthStencilStateCreateInfo =
				VkPipelineDepthStencilStateCreateInfo.callocStack(it)
					.sType(VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO)
					.depthTestEnable(true)
					.depthWriteEnable(true)
					.depthCompareOp(VK_COMPARE_OP_LESS)
					.depthBoundsTestEnable(false)
					.minDepthBounds(0.0f)
					.maxDepthBounds(1.0f)
					.stencilTestEnable(false)

			/**
			 * Colour Blending
			 */
			val colorBlendAttachment = VkPipelineColorBlendAttachmentState.callocStack(1, it)
				.colorWriteMask(VK_COLOR_COMPONENT_R_BIT or VK_COLOR_COMPONENT_G_BIT or VK_COLOR_COMPONENT_B_BIT or VK_COLOR_COMPONENT_A_BIT)
				.blendEnable(false)

			val colorBlending: VkPipelineColorBlendStateCreateInfo =
				VkPipelineColorBlendStateCreateInfo.callocStack(it)
					.sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
					.logicOpEnable(false)
					.logicOp(VK_LOGIC_OP_COPY)
					.pAttachments(colorBlendAttachment)
					.blendConstants(it.floats(0.0f, 0.0f, 0.0f, 0.0f))

			/**
			 * Create Push Constants
			 */
			val pushConstantRange = VkPushConstantRange.Buffer(it.bytes(1))
				.offset(0)
				.size(ModelUbo.MAT4f_SIZE)
				.stageFlags(VK_SHADER_STAGE_VERTEX_BIT)

			/**
			 * Pipeline Layout Creation
			 */
			val pipelineLayoutInfo: VkPipelineLayoutCreateInfo = VkPipelineLayoutCreateInfo.callocStack(it)
				.sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
				.pSetLayouts(it.longs(descriptorSetLayout))
				.pPushConstantRanges(pushConstantRange)
			val pPipelineLayout = it.longs(VK_NULL_HANDLE)
			vkCreatePipelineLayout(device.device, pipelineLayoutInfo, null, pPipelineLayout).ok()
			pipelineLayout = pPipelineLayout[0]

			/**
			 * Pipeline Creation
			 */
			val pipelineInfo = VkGraphicsPipelineCreateInfo.callocStack(1, it)
				.sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
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
				.basePipelineHandle(VK_NULL_HANDLE)
				.basePipelineIndex(-1)

			val pGraphicsPipeline: LongBuffer = it.mallocLong(1)
			vkCreateGraphicsPipelines(device.device, VK_NULL_HANDLE, pipelineInfo, null, pGraphicsPipeline).ok()
			graphicsPipeline = pGraphicsPipeline[0]

			/**
			 * Free Resources
			 */
			vkDestroyShaderModule(device.device, vertShaderModule, null)
			vkDestroyShaderModule(device.device, fragShaderModule, null)

			vertShaderSPIRV.free()
			fragShaderSPIRV.free()
		}
	}

	var commandPool: Long = 0
	var commandBuffers: List<VkCommandBuffer> = ArrayList<VkCommandBuffer>()

	fun createCmdPool(engine: Rosella) {
		stackPush().use { stack ->
			val queueFamilyIndices = findQueueFamilies(engine.device.physicalDevice, engine)
			val poolInfo = VkCommandPoolCreateInfo.callocStack(stack)
			poolInfo.sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
			poolInfo.queueFamilyIndex(queueFamilyIndices.graphicsFamily!!)
			val pCommandPool = stack.mallocLong(1)
			vkCreateCommandPool(engine.device.device, poolInfo, null, pCommandPool).ok()
			commandPool = pCommandPool[0]
		}
	}

	fun createCommandBuffers(
		swapchain: SwapChain,
		renderPass: RenderPass,
		pipeline: Pipeline,
		engine: Rosella
	) {
		/**
		 * Create the Command Buffers
		 */
		val commandBuffersCount: Int = swapchain.swapChainFramebuffers.size

		commandBuffers = ArrayList(commandBuffersCount)

		stackPush().use {
			// Allocate
			val allocInfo = VkCommandBufferAllocateInfo.callocStack(it)
			allocInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
			allocInfo.commandPool(commandPool)
			allocInfo.level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
			allocInfo.commandBufferCount(commandBuffersCount)
			val pCommandBuffers = it.mallocPointer(commandBuffersCount)
			if (vkAllocateCommandBuffers(engine.device.device, allocInfo, pCommandBuffers) != VK_SUCCESS) {
				throw RuntimeException("Failed to allocate command buffers")
			}

			for (i in 0 until commandBuffersCount) {
				(commandBuffers as ArrayList<VkCommandBuffer>).add(
					VkCommandBuffer(
						pCommandBuffers[i],
						engine.device.device
					)
				)
			}
			val beginInfo = VkCommandBufferBeginInfo.callocStack(it)
				.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)

			val renderPassInfo = VkRenderPassBeginInfo.callocStack(it)
				.sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
				.renderPass(renderPass.renderPass)

			val renderArea = VkRect2D.callocStack(it)
				.offset(VkOffset2D.callocStack(it).set(0, 0))
				.extent(swapchain.swapChainExtent!!)

			renderPassInfo.renderArea(renderArea)

			val clearValues = VkClearValue.callocStack(2, it)
			clearValues[0].color().float32(it.floats(0xef / 255f, 0x32 / 255f, 0x3d / 255f, 1.0f))
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
					val offsets = it.longs(0)

					val vertexBuffers = it.longs(engine.model.vertexBuffer)
					vkCmdBindVertexBuffers(commandBuffer, 0, vertexBuffers, offsets)
					vkCmdBindIndexBuffer(commandBuffer, engine.model.indexBuffer, 0, VK_INDEX_TYPE_UINT32)
					vkCmdBindDescriptorSets(
						commandBuffer,
						VK_PIPELINE_BIND_POINT_GRAPHICS,
						pipeline.pipelineLayout,
						0,
						it.longs(engine.model.descriptorSets[i]),
						null
					)

					//TODO: push constants are the way to make multiple objects work. Valoghese remind me after i get them working to make multiple models work. :)
					// Load the push constant into memory
					val data = it.mallocPointer(1)
					val modelPushConstant = ModelPushConstant()
					modelPushConstant.position.add(0f, 1f, 0f)
					val size = sizeof(modelPushConstant.position)
					vkMapMemory(
						engine.device.device,
						engine.shaderDataManager.pushConstantBuffersMemory[0], // Hardcoded to 0 for the 1 model
						0,
						size.toLong(),
						0,
						data
					)
					run {
						memcpy(data.getByteBuffer(0, size), modelPushConstant)
					}
					vkUnmapMemory(
						engine.device.device,
						engine.shaderDataManager.pushConstantBuffersMemory[0]
					)// Hardcoded to 0 for the 1 model

					val buffer = it.longs(1)
					buffer.put(engine.shaderDataManager.pushConstantBuffers[0])

					vkCmdPushConstants(
						commandBuffer,
						engine.pipeline.pipelineLayout,
						VK_SHADER_STAGE_VERTEX_BIT,
						0,
						data.getByteBuffer(0, size)
					)

					vkCmdDrawIndexed(commandBuffer, engine.model.indices.size, 1, 0, 0, 0)
				}
				vkCmdEndRenderPass(commandBuffer)
				vkEndCommandBuffer(commandBuffer).ok()
			}
		}
	}

	private fun createShaderModule(spirvCode: ByteBuffer, device: Device): Long {
		stackPush().use { stack ->
			val createInfo = VkShaderModuleCreateInfo.callocStack(stack)
				.sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
				.pCode(spirvCode)
			val pShaderModule = stack.mallocLong(1)
			vkCreateShaderModule(device.device, createInfo, null, pShaderModule).ok()
			return pShaderModule[0]
		}
	}

	private fun asPtrBuffer(list: List<Pointer>): PointerBuffer {
		val stack = MemoryStack.stackGet()
		val buffer = stack.mallocPointer(list.size)
		list.forEach { pointer: Pointer? -> buffer.put(pointer!!) }
		return buffer.rewind()
	}

	fun free(device: Device) {
		vkFreeCommandBuffers(device.device, commandPool, asPtrBuffer(commandBuffers))
		vkDestroyPipeline(device.device, graphicsPipeline, null)
		vkDestroyPipelineLayout(device.device, pipelineLayout, null)
	}
}