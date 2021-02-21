package me.hydos.rosella.core.swapchain

import me.hydos.rosella.core.Device
import me.hydos.rosella.model.Vertex
import me.hydos.rosella.util.ShaderType
import me.hydos.rosella.util.SpirV
import me.hydos.rosella.util.compileShaderFile
import me.hydos.rosella.util.ok
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*
import java.nio.ByteBuffer
import java.nio.LongBuffer


class GfxPipeline(private val device: Device, private val swapchain: Swapchain, private val renderPass: RenderPass, private val descriptorSetLayout: Long) {

	internal var pipelineLayout: Long = 0
	internal var graphicsPipeline: Long = 0

	init {
		stackPush().use {
			val vertShaderSPIRV: SpirV = compileShaderFile("shaders/base.v.glsl", ShaderType.VERTEX_SHADER)
			val fragShaderSPIRV: SpirV = compileShaderFile("shaders/base.f.glsl", ShaderType.FRAGMENT_SHADER)
			val vertShaderModule = createShaderModule(vertShaderSPIRV.bytecode())
			val fragShaderModule = createShaderModule(fragShaderSPIRV.bytecode())
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
			 * Pipeline Layout Creation
			 */
			val pipelineLayoutInfo: VkPipelineLayoutCreateInfo = VkPipelineLayoutCreateInfo.callocStack(it)
				.sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
				.pSetLayouts(it.longs(descriptorSetLayout))
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

	private fun createShaderModule(spirvCode: ByteBuffer): Long {
		stackPush().use { stack ->
			val createInfo = VkShaderModuleCreateInfo.callocStack(stack)
				.sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
				.pCode(spirvCode)
			val pShaderModule = stack.mallocLong(1)
			vkCreateShaderModule(device.device, createInfo, null, pShaderModule).ok()
			return pShaderModule[0]
		}
	}
}