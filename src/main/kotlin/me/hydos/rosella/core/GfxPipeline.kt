package me.hydos.rosella.core

import me.hydos.rosella.util.ShaderType
import me.hydos.rosella.util.SpirV
import me.hydos.rosella.util.compileShaderFile
import me.hydos.rosella.util.ok
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*
import java.nio.ByteBuffer
import java.nio.LongBuffer


class GfxPipeline(private val device: Device, private  val swapchain: Swapchain) {

	internal var pipelineLayout: Long = 0

	init {
		stackPush().use {
			val vertShaderSPIRV: SpirV = compileShaderFile("shaders/base.vert", ShaderType.VERTEX_SHADER)!!
			val fragShaderSPIRV: SpirV = compileShaderFile("shaders/base.frag", ShaderType.FRAGMENT_SHADER)!!
			val vertShaderModule = createShaderModule(vertShaderSPIRV.bytecode()!!)
			val fragShaderModule = createShaderModule(fragShaderSPIRV.bytecode()!!)
			val entryPoint: ByteBuffer = it.UTF8("main")
			val shaderStages = VkPipelineShaderStageCreateInfo.callocStack(2, it)
			val vertShaderStageInfo = shaderStages[0]

			vertShaderStageInfo.sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
				.stage(VK_SHADER_STAGE_VERTEX_BIT)
				.module(vertShaderModule)
				.pName(entryPoint)

			val fragShaderStageInfo = shaderStages[1]

			fragShaderStageInfo.sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
			fragShaderStageInfo.stage(VK_SHADER_STAGE_FRAGMENT_BIT)
			fragShaderStageInfo.module(fragShaderModule)
			fragShaderStageInfo.pName(entryPoint)

			vkDestroyShaderModule(device.device, vertShaderModule, null)
			vkDestroyShaderModule(device.device, fragShaderModule, null)

			// Stages
			/**
			 * Vertex
			 */
			val vertexInputInfo: VkPipelineVertexInputStateCreateInfo =
				VkPipelineVertexInputStateCreateInfo.callocStack(it)
			vertexInputInfo.sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)

			/**
			 * Assembly
			 */
			val inputAssembly: VkPipelineInputAssemblyStateCreateInfo =
				VkPipelineInputAssemblyStateCreateInfo.callocStack(it)
			inputAssembly.sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
			inputAssembly.topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
			inputAssembly.primitiveRestartEnable(false)

			/**
			 * Viewport
			 */
			val viewport = VkViewport.callocStack(1, it)
			viewport.x(0.0f)
			viewport.y(0.0f)
			viewport.width(swapchain.swapChainExtent!!.width().toFloat())
			viewport.height(swapchain.swapChainExtent!!.height().toFloat())
			viewport.minDepth(0.0f)
			viewport.maxDepth(1.0f)

			/**
			 * Scissor
			 */
			val scissor = VkRect2D.callocStack(1, it)
			scissor.offset(VkOffset2D.callocStack(it).set(0, 0))
			scissor.extent(swapchain.swapChainExtent!!)

			val viewportState: VkPipelineViewportStateCreateInfo = VkPipelineViewportStateCreateInfo.callocStack(it)
			viewportState.sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
			viewportState.pViewports(viewport)
			viewportState.pScissors(scissor)

			/**
			 * Rasterisation
			 */
			val rasterizer: VkPipelineRasterizationStateCreateInfo =
				VkPipelineRasterizationStateCreateInfo.callocStack(it)
			rasterizer.sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
			rasterizer.depthClampEnable(false)
			rasterizer.rasterizerDiscardEnable(false)
			rasterizer.polygonMode(VK_POLYGON_MODE_FILL)
			rasterizer.lineWidth(1.0f)
			rasterizer.cullMode(VK_CULL_MODE_BACK_BIT)
			rasterizer.frontFace(VK_FRONT_FACE_CLOCKWISE)
			rasterizer.depthBiasEnable(false)

			/**
			 * Multisampling
			 */
			val multisampling: VkPipelineMultisampleStateCreateInfo =
				VkPipelineMultisampleStateCreateInfo.callocStack(it)
			multisampling.sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
			multisampling.sampleShadingEnable(false)
			multisampling.rasterizationSamples(VK_SAMPLE_COUNT_1_BIT)

			/**
			 * Colour Blending
			 */
			val colorBlendAttachment = VkPipelineColorBlendAttachmentState.callocStack(1, it)
			colorBlendAttachment.colorWriteMask(VK_COLOR_COMPONENT_R_BIT or VK_COLOR_COMPONENT_G_BIT or VK_COLOR_COMPONENT_B_BIT or VK_COLOR_COMPONENT_A_BIT)
			colorBlendAttachment.blendEnable(false)

			val colorBlending: VkPipelineColorBlendStateCreateInfo =
				VkPipelineColorBlendStateCreateInfo.callocStack(it)
			colorBlending.sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
			colorBlending.logicOpEnable(false)
			colorBlending.logicOp(VK_LOGIC_OP_COPY)
			colorBlending.pAttachments(colorBlendAttachment)
			colorBlending.blendConstants(it.floats(0.0f, 0.0f, 0.0f, 0.0f))

			/**
			 * Pipeline layout
			 */
			val pipelineLayoutInfo: VkPipelineLayoutCreateInfo = VkPipelineLayoutCreateInfo.callocStack(it)
			pipelineLayoutInfo.sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)

			val pPipelineLayout: LongBuffer = it.longs(VK_NULL_HANDLE)

			vkCreatePipelineLayout(device.device, pipelineLayoutInfo, null, pPipelineLayout).ok()

			pipelineLayout = pPipelineLayout[0]

			/**
			 * Free Resources
			 */

			vertShaderSPIRV.free()
			fragShaderSPIRV.free()
		}
	}

	private fun createShaderModule(spirvCode: ByteBuffer): Long {
		stackPush().use { stack ->
			val createInfo = VkShaderModuleCreateInfo.callocStack(stack)
			createInfo.sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
				.pCode(spirvCode)
			val pShaderModule = stack.mallocLong(1)
			vkCreateShaderModule(device.device, createInfo, null, pShaderModule).ok()
			return pShaderModule[0]
		}
	}
}