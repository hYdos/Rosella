package me.hydos.example;

import me.hydos.rosella.Rosella;
import me.hydos.rosella.io.Screen;

/**
 * Funny joke i came up with in class yes
 */
public class MowingSim {

	public static void main(String[] args) {
		Screen screen = new Screen("Concerning Vulkan engine", 800, 600, false);
		Rosella engine = new Rosella("Thing Name Here", true);

		// Register events so we can interact and run game logic
		screen.onMainLoop(MowingSim::loop);

		// Start the screens main loop.
		screen.start(engine);
	}

	private static void loop() {
		System.out.println("Main Loop");
	}
}
