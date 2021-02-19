package me.hydos.rosella.io

import it.unimi.dsi.fastutil.objects.ObjectArrayList
import me.hydos.rosella.Rosella
import org.lwjgl.glfw.GLFW

class Screen(title: String, width: Int, height: Int, windowResizable: Boolean) {
	private val windowPtr: Long
	private val callbacks: MutableList<() -> Unit> = ObjectArrayList()
	private var engine: Rosella? = null

	fun start(engine: Rosella) {
		this.engine = engine
		while (!GLFW.glfwWindowShouldClose(windowPtr)) {
			GLFW.glfwPollEvents()
			for (callback in callbacks) {
				callback()
			}
		}
	}

	fun onMainLoop(callback: () -> Unit) {
		callbacks.add(callback)
	}

	init {
		GLFW.glfwInit()
		windowPtr = GLFW.glfwCreateWindow(width, height, title, 0, 0)
		GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_NO_API)
		GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, if (windowResizable) GLFW.GLFW_TRUE else GLFW.GLFW_FALSE)
		Runtime.getRuntime().addShutdownHook(Thread {
			GLFW.glfwDestroyWindow(windowPtr)
			GLFW.glfwTerminate()
		})
	}
}