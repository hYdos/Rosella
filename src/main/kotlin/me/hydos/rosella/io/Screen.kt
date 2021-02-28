package me.hydos.rosella.io

import it.unimi.dsi.fastutil.objects.ObjectArrayList
import me.hydos.rosella.Rosella
import org.lwjgl.glfw.GLFW.*

class Screen(title: String, width: Int, height: Int, windowResizable: Boolean = true) {
	internal val windowPtr: Long
	private val loopCallbacks: MutableList<() -> Unit> = ObjectArrayList()
	private val closeCallbacks: MutableList<() -> Unit> = ObjectArrayList()

	fun start(engine: Rosella) {
		glfwSetFramebufferSizeCallback(windowPtr, engine::windowResizeCallback)

		while (!glfwWindowShouldClose(windowPtr)) {
			glfwPollEvents()

			for (callback in loopCallbacks) {
				callback()
			}
		}
	}

	fun onMainLoop(callback: () -> Unit) {
		loopCallbacks.add(callback)
	}

	fun onWindowClose(function: () -> Unit) {
		closeCallbacks.add(function)
	}

	init {
		if (!glfwInit()) {
			throw RuntimeException("Cannot Initialize GLFW")
		}
		glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)
		glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
		glfwWindowHint(GLFW_RESIZABLE, windowResizable.asGlfw())
		windowPtr = glfwCreateWindow(width, height, title, 0, 0)

		Runtime.getRuntime().addShutdownHook(Thread {
			for (callback in closeCallbacks) {
				callback()
			}
			glfwDestroyWindow(windowPtr)
			glfwTerminate()
		})
	}
}

private fun Boolean.asGlfw(): Int = if (this) GLFW_TRUE else GLFW_FALSE
