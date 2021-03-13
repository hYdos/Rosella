package me.hydos.rosella.shader.ubo

import me.hydos.rosella.device.Device
import me.hydos.rosella.memory.MemMan
import me.hydos.rosella.swapchain.SwapChain
import me.hydos.rosella.util.alignas
import me.hydos.rosella.util.alignof
import java.nio.ByteBuffer

abstract class Ubo(device: Device, memory: MemMan) {

	abstract fun create(swapChain: SwapChain)
	abstract fun update(currentImg: Int, swapChain: SwapChain, uniformBuffersMemory: MutableList<Long>)
	abstract fun free()

	@Deprecated("Bad")
	fun copyLegacyUboIntoMemory(buffer: ByteBuffer, ubo: LegacyUbo) {
		val mat4Size = 16 * java.lang.Float.BYTES
		ubo.model[0, buffer]
		ubo.view.get(alignas(mat4Size, alignof(ubo.view)), buffer)
		ubo.proj.get(alignas(mat4Size * 2, alignof(ubo.view)), buffer)
	}
}