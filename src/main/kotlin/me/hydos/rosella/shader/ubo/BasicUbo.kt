package me.hydos.rosella.shader.ubo

import me.hydos.rosella.device.Device
import me.hydos.rosella.memory.MemMan
import me.hydos.rosella.swapchain.SwapChain
import me.hydos.rosella.util.alignas
import me.hydos.rosella.util.alignof
import me.hydos.rosella.util.createBuffer
import me.hydos.rosella.util.sizeof
import org.joml.Matrix4f
import org.lwjgl.glfw.GLFW
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10
import java.util.function.Consumer

class BasicUbo(val device: Device, val memory: MemMan) : Ubo() {

	var ubos: MutableList<Long> = ArrayList()
	var ubosMem: MutableList<Long> = ArrayList()

	var model: Matrix4f = Matrix4f()

	override fun create(swapChain: SwapChain) {
		MemoryStack.stackPush().use { stack ->
			ubos = ArrayList(swapChain.swapChainImages.size)
			ubosMem = ArrayList(swapChain.swapChainImages.size)
			val pBuffer = stack.mallocLong(1)
			val pBufferMemory = stack.mallocLong(1)
			for (i in swapChain.swapChainImages.indices) {
				createBuffer(
					getSize(),
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

	override fun update(currentImg: Int, swapChain: SwapChain, view: Matrix4f, proj: Matrix4f) {
		if(ubosMem.size == 0) { //no models exist tm
			create(swapChain)
		}

		MemoryStack.stackPush().use {
			model.rotate((GLFW.glfwGetTime() * Math.toRadians(90.0)).toFloat(), 0.0f, 0.0f, 1.0f)

			val data = it.mallocPointer(1)
			VK10.vkMapMemory(
				device.device,
				ubosMem[currentImg],
				0,
				getSize().toLong(),
				0,
				data
			)
			run {
				val buffer = data.getByteBuffer(0, getSize())
				val mat4Size = 16 * java.lang.Float.BYTES
				model[0, buffer]
				view.get(alignas(mat4Size, alignof(view)), buffer)
				proj.get(alignas(mat4Size * 2, alignof(view)), buffer)
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

	override fun getSize(): Int {
		return 3 * sizeof(Matrix4f::class)
	}

	override fun getUniformBuffers(): List<Long> {
		return ubos
	}
}