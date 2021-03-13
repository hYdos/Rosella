package me.hydos.rosella.shader.ubo

import me.hydos.rosella.swapchain.SwapChain
import me.hydos.rosella.util.alignas
import me.hydos.rosella.util.alignof
import java.nio.ByteBuffer

abstract class Ubo {

	/**
	 * Called when the uniform buffers should be created
	 */
	abstract fun create(swapChain: SwapChain)

	/**
	 * Called before each frame to update the ubo
	 */
	abstract fun update(currentImg: Int, swapChain: SwapChain)

	/**
	 * Called when the program is closing and free's memory
	 */
	abstract fun free()

	/**
	 * Gets the size of the ubo
	 */
	abstract fun getSize(): Int

	/**
	 * Gets an list of pointers to ubo's
	 */
	abstract fun getUniformBuffers(): List<Long>

	@Deprecated("Bad")
	fun copyLegacyUboIntoMemory(buffer: ByteBuffer, ubo: LegacyUbo) {
		val mat4Size = 16 * java.lang.Float.BYTES
		ubo.model[0, buffer]
		ubo.view.get(alignas(mat4Size, alignof(ubo.view)), buffer)
		ubo.proj.get(alignas(mat4Size * 2, alignof(ubo.view)), buffer)
	}
}