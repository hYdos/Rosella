package me.hydos.rosella.shader.ubo

import me.hydos.rosella.device.Device
import me.hydos.rosella.memory.MemMan
import me.hydos.rosella.swapchain.SwapChain
import org.lwjgl.glfw.GLFW
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10

class BasicUbo(val device: Device, val memory: MemMan) : Ubo(device, memory) {

	override fun create(swapChain: SwapChain) {
		TODO("Not yet implemented")
	}

	override fun update(currentImg: Int, swapChain: SwapChain, uniformBuffersMemory: MutableList<Long>) {
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
				uniformBuffersMemory[currentImg],
				0,
				LegacyUbo.SIZEOF.toLong(),
				0,
				data
			)
			run {
				copyLegacyUboIntoMemory(data.getByteBuffer(0, LegacyUbo.SIZEOF), ubo)
			}
			VK10.vkUnmapMemory(device.device, uniformBuffersMemory[currentImg])
		}
	}

	override fun free() {
		TODO("Not yet implemented")
	}
}