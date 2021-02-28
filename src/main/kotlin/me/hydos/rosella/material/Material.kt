package me.hydos.rosella.material

import me.hydos.rosella.RenderPass
import me.hydos.rosella.Rosella
import me.hydos.rosella.device.Device
import me.hydos.rosella.model.Vertex
import me.hydos.rosella.model.ubo.ModelPushConstant
import me.hydos.rosella.swapchain.SwapChain
import me.hydos.rosella.util.*
import org.lwjgl.PointerBuffer
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
class Material(val vertexShaderFile: String, val fragmentShaderFile: String) {

	var pipelineLayout: Long = 0
	var graphicsPipeline: Long = 0

	/**
	 * The main rendering pipeline of this material
	 */
	fun createPipeline(
		device: Device,
		swapchain: SwapChain,
		renderPass: RenderPass,
		descriptorSetLayout: Long
	) {
		MemoryStack.stackPush().use {
			val vertShaderSPIRV: SpirV = compileShaderFile(vertexShaderFile, ShaderType.VERTEX_SHADER)
			val fragShaderSPIRV: SpirV = compileShaderFile(fragmentShaderFile, ShaderType.FRAGMENT_SHADER)
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
				.offset(0)
				.size(sizeof(ModelPushConstant().position))
				.stageFlags(VK10.VK_SHADER_STAGE_VERTEX_BIT)

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
}