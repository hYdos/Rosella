package me.hydos.example

import me.hydos.rosella.Rosella
import me.hydos.rosella.io.Screen

/**
 * Funny joke i came up with in class yes
 */
object MowingSim {
	@JvmStatic
	fun main(args: Array<String>) {
		val screen = Screen("Concerning Vulkan engine", 800, 600, false)
		val engine = Rosella("Thing Name Here", true)

		// Register events so we can interact and run game logic
		screen.onMainLoop { loop() }

		// Start the screens main loop.
		screen.start(engine)
	}

	private fun loop() {
//		println("Main Loop")
	}
}