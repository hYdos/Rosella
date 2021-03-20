package me.hydos.rosella.shader.ubo

import me.hydos.rosella.swapchain.SwapChain
import me.hydos.rosella.util.memory.BufferInfo
import org.joml.Matrix4f

abstract class Ubo {

	/**
	 * Called when the uniform buffers should be created
	 */
	abstract fun create(swapChain: SwapChain)

	/**
	 * Called before each frame to update the ubo
	 */
	abstract fun update(currentImg: Int, swapChain: SwapChain, view: Matrix4f, proj: Matrix4f)

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
	abstract fun getUniformBuffers(): List<BufferInfo>
}