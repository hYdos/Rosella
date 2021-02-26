package me.hydos.rosella.model.ubo

import me.hydos.rosella.core.Device
import me.hydos.rosella.core.swapchain.Swapchain
import me.hydos.rosella.model.ubo.UniformBufferObject.Companion.SIZEOF
import me.hydos.rosella.util.createBuffer
import me.hydos.rosella.util.memcpy
import org.lwjgl.glfw.GLFW.glfwGetTime
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.vulkan.VK10.*
import java.util.*
import java.util.function.Consumer


var uniformBuffers: List<Long> = ArrayList()
var uniformBuffersMemory: List<Long> = ArrayList()

fun freeUbos(device: Device) {
	uniformBuffers.forEach(Consumer { ubo: Long? -> vkDestroyBuffer(device.device, ubo!!, null) })
	uniformBuffersMemory.forEach(Consumer { uboMemory: Long? ->
		vkFreeMemory(
			device.device,
			uboMemory!!, null
		)
	})
}

fun updateUniformBuffer(currentImage: Int, swapchain: Swapchain, device: Device) {
	stackPush().use {
		val ubo = UniformBufferObject()
		ubo.model.rotate((glfwGetTime() * Math.toRadians(90.0)).toFloat(), 0.0f, 0.0f, 1.0f)
		ubo.view.lookAt(2.0f, 2.0f, 2.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f)
		ubo.proj.perspective(
			Math.toRadians(45.0).toFloat(),
			swapchain.swapChainExtent!!.width().toFloat() / swapchain.swapChainExtent!!.height().toFloat(), 0.1f, 10.0f
		)
		ubo.proj.m11(ubo.proj.m11() * -1)
		val data = it.mallocPointer(1)
		vkMapMemory(
			device.device,
			uniformBuffersMemory[currentImage],
			0,
			SIZEOF.toLong(),
			0,
			data
		)
		run {
			memcpy(data.getByteBuffer(0, SIZEOF), ubo)
		}
		vkUnmapMemory(device.device, uniformBuffersMemory[currentImage])
	}
}

fun createUniformBuffers(swapchain: Swapchain, device: Device) {
	stackPush().use { stack ->
		uniformBuffers = ArrayList(swapchain.swapChainImages.size)
		uniformBuffersMemory = ArrayList(swapchain.swapChainImages.size)
		val pBuffer = stack.mallocLong(1)
		val pBufferMemory = stack.mallocLong(1)
		for (i in swapchain.swapChainImages.indices) {
			createBuffer(
				SIZEOF,
				VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
				VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
				pBuffer,
				pBufferMemory,
				device
			)
			(uniformBuffers as ArrayList<Long>).add(pBuffer[0])
			(uniformBuffersMemory as ArrayList<Long>).add(pBufferMemory[0])
		}
	}
}
