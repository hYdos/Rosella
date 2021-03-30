package me.hydos.rosella.render.io

import it.unimi.dsi.fastutil.objects.ObjectArrayList
import org.lwjgl.glfw.GLFW.*


/**
 * Represents a window in which Rosella can be attached to
 */
class Window(title: String, width: Int, height: Int, windowResizable: Boolean = true) {
	var monitorWidth: Int = 0
	var monitorHeight: Int = 0

	internal val windowPtr: Long
	private val loopCallbacks: MutableList<() -> Unit> = ObjectArrayList()
	private val closeCallbacks: MutableList<() -> Unit> = ObjectArrayList()
	private val resizeCallbacks: MutableList<(width: Int, height: Int) -> Unit> = ObjectArrayList()


	fun start() {
		glfwSetFramebufferSizeCallback(windowPtr, this::onResize)

		while (!glfwWindowShouldClose(windowPtr)) {
			glfwPollEvents()

			for (callback in loopCallbacks) {
				callback()
			}
		}
	}

	private fun onResize(window: Long, width: Int, height: Int) {
		for (resizeCallback in resizeCallbacks) {
			resizeCallback(width, height)
		}
	}

	fun onMainLoop(callback: () -> Unit) {
		loopCallbacks.add(callback)
	}

	fun onWindowClose(function: () -> Unit) {
		closeCallbacks.add(function)
	}

	fun onWindowResize(function: (width: Int, height: Int) -> Unit) {
		resizeCallbacks.add(function)
	}

	init {
		if (!glfwInit()) {
			throw RuntimeException("Cannot Initialize GLFW")
		}
		glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)
		glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
		glfwWindowHint(GLFW_RESIZABLE, if (windowResizable) GLFW_TRUE else GLFW_FALSE)
		windowPtr = glfwCreateWindow(width, height, title, 0, 0)

		val videoMode = glfwGetVideoMode(glfwGetPrimaryMonitor()) ?: error("Could not start window")
		monitorWidth = videoMode.width()
		monitorHeight = videoMode.height()

		Runtime.getRuntime().addShutdownHook(Thread {
			for (callback in closeCallbacks) {
				callback()
			}
			glfwDestroyWindow(windowPtr)
			glfwTerminate()
		})
	}
}
