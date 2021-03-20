package me.hydos.rosella.shader.ubo

import me.hydos.rosella.device.Device
import me.hydos.rosella.swapchain.SwapChain
import me.hydos.rosella.util.alignas
import me.hydos.rosella.util.alignof
import me.hydos.rosella.util.memory.BufferInfo
import me.hydos.rosella.util.memory.Memory
import me.hydos.rosella.util.sizeof
import org.joml.Matrix4f
import org.lwjgl.system.MemoryStack
import org.lwjgl.util.vma.Vma
import org.lwjgl.vulkan.VK10

class BasicUbo(val device: Device, val memory: Memory) : Ubo() {

	var ubos: MutableList<BufferInfo> = ArrayList()

	var modelMatrix: Matrix4f = Matrix4f()

	override fun create(swapChain: SwapChain) {
		MemoryStack.stackPush().use { stack ->
			ubos = ArrayList(swapChain.swapChainImages.size)
			for (i in swapChain.swapChainImages.indices) {
				val pBuffer = stack.mallocLong(1)
				ubos.add(
					memory.createBuffer(
						getSize(),
						VK10.VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
						Vma.VMA_MEMORY_USAGE_CPU_ONLY,
						pBuffer
					)
				)
			}
		}
	}

	override fun update(currentImg: Int, swapChain: SwapChain, view: Matrix4f, proj: Matrix4f) {
		if (ubos.size == 0) {
			create(swapChain) //TODO: CONCERN. why did i write this
		}

		MemoryStack.stackPush().use {
			val data = it.mallocPointer(1)
			memory.map(ubos[currentImg].allocation, false, data)
			val buffer = data.getByteBuffer(0, getSize())
			val mat4Size = 16 * java.lang.Float.BYTES
			modelMatrix[0, buffer]
			view.get(alignas(mat4Size, alignof(view)), buffer)
			proj.get(alignas(mat4Size * 2, alignof(view)), buffer)
			memory.unmap(ubos[currentImg].allocation)
		}
	}

	override fun free() {
		for (uboImg in ubos) {
			memory.freeBuffer(uboImg)
		}
	}

	override fun getSize(): Int {
		return 3 * sizeof(Matrix4f::class)
	}

	override fun getUniformBuffers(): List<BufferInfo> {
		return ubos
	}
}