package me.hydos.rosella.shader.ubo

import me.hydos.rosella.device.Device
import me.hydos.rosella.memory.MemMan
import me.hydos.rosella.swapchain.SwapChain
import me.hydos.rosella.util.createBuffer
import org.lwjgl.glfw.GLFW
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10
import java.util.function.Consumer

class BasicUbo(val device: Device, val memory: MemMan) : Ubo(device, memory) {

	var ubos: MutableList<Long> = ArrayList()
	var ubosMem: MutableList<Long> = ArrayList()

	override fun create(swapChain: SwapChain) {
		MemoryStack.stackPush().use { stack ->
			ubos = ArrayList(swapChain.swapChainImages.size)
			ubosMem = ArrayList(swapChain.swapChainImages.size)
			val pBuffer = stack.mallocLong(1)
			val pBufferMemory = stack.mallocLong(1)
			for (i in swapChain.swapChainImages.indices) {
				createBuffer(
					LegacyUbo.SIZEOF,
					VK10.VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
					VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
					pBuffer,
					pBufferMemory,
					device
				)
				ubos.add(pBuffer[0])
				ubosMem.add(pBufferMemory[0])
			}
		}
	}

	override fun update(currentImg: Int, swapChain: SwapChain) {
		MemoryStack.stackPush().use {
			val ubo = LegacyUbo()
			ubo.model.rotate((GLFW.glfwGetTime() * Math.toRadians(90.0)).toFloat(), 0.0f, 0.0f, 1.0f)
			ubo.view.lookAt(2.0f, -40.0f, 2.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f)
			ubo.proj.perspective(
				Math.toRadians(45.0).toFloat(),
				swapChain.swapChainExtent!!.width().toFloat() / swapChain.swapChainExtent!!.height().toFloat(),
				0.1f,
				1000.0f
			)
			ubo.proj.m11(ubo.proj.m11() * -1)

			val data = it.mallocPointer(1)
			VK10.vkMapMemory(
				device.device,
				ubosMem[currentImg],
				0,
				LegacyUbo.SIZEOF.toLong(),
				0,
				data
			)
			run {
				copyLegacyUboIntoMemory(data.getByteBuffer(0, LegacyUbo.SIZEOF), ubo)
			}
			VK10.vkUnmapMemory(device.device, ubosMem[currentImg])
		}
	}

	override fun free() {
		ubos.forEach(Consumer { ubo: Long? -> VK10.vkDestroyBuffer(device.device, ubo!!, null) })
		ubosMem.forEach(Consumer { uboMemory: Long? ->
			VK10.vkFreeMemory(
				device.device,
				uboMemory!!, null
			)
		})
	}

	override fun getUniformBuffers(): List<Long> {
		return ubos
	}
}