package me.hydos.example

import me.hydos.rosella.core.Rosella
import me.hydos.rosella.io.Screen

object Example {
	@JvmStatic
	fun main(args: Array<String>) {
		val screen = Screen("Concerning Vulkan engine", 1280, 720, false)
		val engine = Rosella("Thing Name Here", true, screen)

		// Register events so we can interact and run game logic
		screen.onMainLoop { loop() }
		screen.onWindowClose { engine.destroy() }

		// Start the screens main loop.
		screen.start(engine)
	}

	private fun loop() {
//		println("Main Loop")
	}
}