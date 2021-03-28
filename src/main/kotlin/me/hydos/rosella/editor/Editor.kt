package me.hydos.rosella.editor

import me.hydos.rosella.Rosella
import me.hydos.rosella.render.io.Window

object Editor {

	private val window: Window = Window("Rosella Scene Editor", 1920, 1080, true)
	private val rosella: Rosella = Rosella("sceneEditor", false, window)

	@JvmStatic
	fun main(args: Array<String>) {
		rosella.renderer.rebuildCommandBuffers(rosella.renderer.renderPass, rosella)
		window.onMainLoop {
			rosella.renderer.render(rosella)
		}
		window.start(rosella)
	}
}