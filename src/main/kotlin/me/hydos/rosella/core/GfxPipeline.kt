package me.hydos.rosella.core

import me.hydos.rosella.util.ShaderType
import me.hydos.rosella.util.SpirV
import me.hydos.rosella.util.compileShaderFile
import me.hydos.rosella.util.ok
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo
import org.lwjgl.vulkan.VkShaderModuleCreateInfo
import java.nio.ByteBuffer


class GfxPipeline(private val device: Device) {

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