package me.hydos.example

import me.hydos.rosella.core.Rosella
import me.hydos.rosella.io.Screen
import org.lwjgl.vulkan.VK10.vkDeviceWaitIdle

object Example {
	@JvmStatic
	fun main(args: Array<String>) {
		val screen = Screen("Concerning Vulkan engine", 1280, 720, false)
		val engine = Rosella("Thing Name Here", true, screen)

		// Register events so we can interact and run game logic
		screen.onMainLoop {
			engine.renderFrame()
		}
		screen.onWindowClose {
			vkDeviceWaitIdle(engine.device.device) // The 1 vulkan method that needs to be called from the program using our engine
			engine.destroy()
		}

		// Start the screens main loop.
		screen.start(engine)
	}
}
