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


class GfxPipeline(private val device: Device, private val swapchain: Swapchain) {

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

			shaderStages[1]
				.sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
				.stage(VK_SHADER_STAGE_FRAGMENT_BIT)
				.module(fragShaderModule)
				.pName(entryPoint)

			vkDestroyShaderModule(device.device, vertShaderModule, null)
			vkDestroyShaderModule(device.device, fragShaderModule, null)

			// Stages
			/**
			 * Vertex
			 */
			VkPipelineVertexInputStateCreateInfo.callocStack(it)
				.sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)

			/**
			 * Assembly
			 */
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

			VkPipelineViewportStateCreateInfo.callocStack(it)
				.sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
				.pViewports(viewport)
				.pScissors(scissor)

			/**
			 * Rasterisation
			 */
			VkPipelineRasterizationStateCreateInfo.callocStack(it)
				.sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
				.depthClampEnable(false)
				.rasterizerDiscardEnable(false)
				.polygonMode(VK_POLYGON_MODE_FILL)
				.lineWidth(1.0f)
				.cullMode(VK_CULL_MODE_BACK_BIT)
				.frontFace(VK_FRONT_FACE_CLOCKWISE)
				.depthBiasEnable(false)

			/**
			 * Multisampling
			 */
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

			VkPipelineColorBlendStateCreateInfo.callocStack(it)
				.sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
				.logicOpEnable(false)
				.logicOp(VK_LOGIC_OP_COPY)
				.pAttachments(colorBlendAttachment)
				.blendConstants(it.floats(0.0f, 0.0f, 0.0f, 0.0f))

			/**
			 * Pipeline layout
			 */
			val pipelineLayoutInfo: VkPipelineLayoutCreateInfo = VkPipelineLayoutCreateInfo.callocStack(it)
				.sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)

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